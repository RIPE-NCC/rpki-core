package net.ripe.rpki.services.impl.handlers;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.CustomerCertificateAuthority;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.PublicationStatus;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.KeyManagementActivatePendingKeysCommand;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.opentest4j.AssertionFailedError;

import javax.inject.Inject;
import javax.security.auth.x500.X500Principal;
import javax.transaction.Transactional;
import java.net.URI;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class ChildParentCertificateUpdateSagaHostedTest extends CertificationDomainTestCase {

    private static final long HOSTED_CA_ID = 7L;

    private static final X500Principal CHILD_CA_NAME = new X500Principal("CN=child");

    @Inject
    private ResourceCache resourceCache;
    @Inject
    private CertificateRequestCreationService certificateRequestCreationService;

    @Inject
    private CommandService subject;

    private ProductionCertificateAuthority parent;
    private CustomerCertificateAuthority child;

    @Before
    public void setUp() {
        clearDatabase();

        parent = createInitialisedProdCaWithRipeResources();
        child = new CustomerCertificateAuthority(HOSTED_CA_ID, CHILD_CA_NAME, parent, 1);

        certificateAuthorityRepository.add(child);
    }

    @Test
    public void should_issue_certificate_for_hosted_child_certified_resources() {
        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), resources("10.10.0.0/16"));

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION)));

        assertThat(child.getCertifiedResources()).isEqualTo(resources("10.10.0.0/16"));

        Collection<KeyPairEntity> keyPairs = child.getKeyPairs();
        assertThat(keyPairs).hasSize(1);

        Optional<IncomingResourceCertificate> certificate = child.findCurrentIncomingResourceCertificate();
        assertThat(certificate).isPresent();
        assertThat(certificate.get().getResources()).isEqualTo(resources("10.10.0.0/16"));

        assertChildParentInvariants(child, parent);
    }

    @Test
    public void should_keep_same_certificate_when_resources_are_unchanged() {
        should_issue_certificate_for_hosted_child_certified_resources();
        IncomingResourceCertificate certificate = child.getCurrentIncomingCertificate();

        CommandStatus status = execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION)));

        assertThat(status.isHasEffect()).isFalse();
        assertThat(child.getCurrentIncomingCertificate()).isSameAs(certificate);
        assertChildParentInvariants(child, parent);
    }

    @Test
    public void should_revoke_issued_certificate_for_hosted_child_without_certifiable_resources() {
        should_issue_certificate_for_hosted_child_certified_resources();
        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), resources(""));

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION)));

        Collection<KeyPairEntity> keyPairs = child.getKeyPairs();
        assertThat(keyPairs).isEqualTo(Collections.emptySet());

        Optional<IncomingResourceCertificate> certificate = child.findCurrentIncomingResourceCertificate();
        assertThat(certificate).isEmpty();

        assertChildParentInvariants(child, parent);
    }

    @Test
    public void should_issue_certificate_for_every_hosted_child_keys() {
        should_issue_certificate_for_hosted_child_certified_resources();
        KeyPairEntity newKeyPair = child.createNewKeyPair(keyPairService);

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION)));

        assertThat(newKeyPair.getStatus()).isEqualTo(KeyPairStatus.PENDING);

        Collection<KeyPairEntity> keyPairs = child.getKeyPairs();
        assertThat(keyPairs).hasSize(2).contains(newKeyPair);

        keyPairs.stream().forEach(kp -> {
            Optional<IncomingResourceCertificate> certificate = kp.findCurrentIncomingCertificate();
            assertThat(certificate).isPresent();
            assertThat(certificate.get().getResources()).isEqualTo(resources("10.10.0.0/16"));
        });

        assertChildParentInvariants(child, parent);
    }

    @Test
    public void should_issue_certificate_for_hosted_child_old_keys() {
        should_issue_certificate_for_every_hosted_child_keys();
        child.activatePendingKeys(Duration.ZERO);
        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), resources("10.10.0.0/16, 10.20.0.0/16"));

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION)));

        Collection<KeyPairEntity> keyPairs = child.getKeyPairs();
        assertThat(keyPairs).hasSize(2);
        KeyPairEntity old = keyPairs.stream().filter(KeyPairEntity::isOld).findFirst().orElseThrow(() -> new AssertionFailedError("no OLD key pair"));

        IncomingResourceCertificate oldCurrent = old.findCurrentIncomingCertificate().orElseThrow(() -> new AssertionFailedError("no CURRENT certificate for OLD key pair"));
        assertThat(oldCurrent.getResources()).isEqualTo(resources("10.10.0.0/16, 10.20.0.0/16"));

        assertChildParentInvariants(child, parent);
    }

    @Test
    public void should_remove_old_certificate_and_issue_new_certificate_when_hosted_child_resources_expand() {
        should_issue_certificate_for_hosted_child_certified_resources();
        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), resources("10.10.0.0/16, 10.20.0.0/16"));

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION)));

        Optional<IncomingResourceCertificate> maybeCertificate = resourceCertificateRepository.findIncomingResourceCertificateBySubjectKeyPair(child.getCurrentKeyPair());
        assertThat(maybeCertificate).isPresent();

        assertThat(maybeCertificate.get().getResources()).isEqualTo(resources("10.10.0.0/16, 10.20.0.0/16"));

        assertChildParentInvariants(child, parent);
    }

    @Test
    public void should_remove_old_certificate_and_issue_new_certificate_when_hosted_child_resources_contract() {
        should_issue_certificate_for_hosted_child_certified_resources();
        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), resources("10.10.0.0/20"));

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION)));

        Optional<IncomingResourceCertificate> maybeCertificate = resourceCertificateRepository.findIncomingResourceCertificateBySubjectKeyPair(child.getCurrentKeyPair());
        assertThat(maybeCertificate).isPresent();

        assertThat(maybeCertificate.get().getResources()).isEqualTo(resources("10.10.0.0/20"));

        assertChildParentInvariants(child, parent);
    }

    @Test
    public void should_require_new_certificate_when_notification_uri_is_removed() {
        should_issue_certificate_for_hosted_child_certified_resources();

        IncomingResourceCertificate currentIncomingCertificate = child.getCurrentIncomingCertificate();
        X509CertificateInformationAccessDescriptor[] siaWithoutNotificationUri = Arrays.stream(currentIncomingCertificate.getSia())
            .filter(x -> !x.getMethod().equals(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_NOTIFY))
            .collect(Collectors.toList())
            .toArray(new X509CertificateInformationAccessDescriptor[0]);

        boolean updatedNeeded = parent.isCertificateIssuanceNeeded(
            certificateToIssuanceRequest(currentIncomingCertificate).withSubjectInformationAccess(siaWithoutNotificationUri),
            currentIncomingCertificate.getValidityPeriod(),
            resourceCertificateRepository
        );

        assertThat(updatedNeeded).isTrue();
    }

    @Test
    public void should_require_new_certificate_when_signing_certificate_publication_uri_changes() {
        should_issue_certificate_for_hosted_child_certified_resources();

        IncomingResourceCertificate currentIncomingCertificate = child.getCurrentIncomingCertificate();
        parent.getCurrentIncomingCertificate().setPublicationUri(URI.create("rsync://example.com/foo.cer"));

        boolean updatedNeeded = parent.isCertificateIssuanceNeeded(
            certificateToIssuanceRequest(currentIncomingCertificate),
            currentIncomingCertificate.getValidityPeriod(),
            resourceCertificateRepository
        );

        assertThat(updatedNeeded).isTrue();
    }

    @Test
    public void should_require_new_certificate_when_not_valid_before_is_earlier() {
        should_issue_certificate_for_hosted_child_certified_resources();

        IncomingResourceCertificate currentIncomingCertificate = child.getCurrentIncomingCertificate();
        ValidityPeriod currentValidityPeriod = currentIncomingCertificate.getValidityPeriod();

        boolean updatedNeeded = parent.isCertificateIssuanceNeeded(
            certificateToIssuanceRequest(currentIncomingCertificate),
            new ValidityPeriod(
                currentValidityPeriod.getNotValidBefore().minusHours(1),
                currentValidityPeriod.getNotValidAfter()
            ),
            resourceCertificateRepository
        );

        assertThat(updatedNeeded).isTrue();
    }

    @Test
    public void should_require_new_certificate_when_not_valid_after_is_later() {
        should_issue_certificate_for_hosted_child_certified_resources();

        IncomingResourceCertificate currentIncomingCertificate = child.getCurrentIncomingCertificate();
        ValidityPeriod currentValidityPeriod = currentIncomingCertificate.getValidityPeriod();

        boolean updatedNeeded = parent.isCertificateIssuanceNeeded(
            certificateToIssuanceRequest(currentIncomingCertificate),
            new ValidityPeriod(
                currentValidityPeriod.getNotValidBefore(),
                currentValidityPeriod.getNotValidAfter().plusHours(1)
            ),
            resourceCertificateRepository
        );

        assertThat(updatedNeeded).isTrue();
    }

    @Test
    public void should_keep_current_certificate_when_validity_is_contained_within_current_validity() {
        should_issue_certificate_for_hosted_child_certified_resources();

        IncomingResourceCertificate currentIncomingCertificate = child.getCurrentIncomingCertificate();
        ValidityPeriod currentValidityPeriod = currentIncomingCertificate.getValidityPeriod();

        boolean updatedNeeded = parent.isCertificateIssuanceNeeded(
            certificateToIssuanceRequest(currentIncomingCertificate),
            new ValidityPeriod(
                currentValidityPeriod.getNotValidBefore().plusHours(1),
                currentValidityPeriod.getNotValidAfter().minusHours(1)
            ),
            resourceCertificateRepository
        );

        assertThat(updatedNeeded).isFalse();
    }

    private CertificateIssuanceRequest certificateToIssuanceRequest(IncomingResourceCertificate certificate) {
        return new CertificateIssuanceRequest(
            certificate.getResources(),
            certificate.getSubject(),
            certificate.getSubjectPublicKey(),
            certificate.getSia()
        );
    }

    @Test
    public void should_revoke_previous_certificate_and_issue_new_certificate_after_parent_key_rollover() {
        // When the parent replaces the CURRENT key with a PENDING key (making the current key OLD and the PENDING key CURRENT)
        // the outgoing child certificates issued by the OLD key should be replaced when the child certificate is re-issued by
        // the PENDING (now CURRENT) key.
        should_issue_certificate_for_hosted_child_certified_resources();
        X509ResourceCertificate certificate1 = child.getCurrentIncomingCertificate().getCertificate();
        URI publicationUri1 = child.getCurrentIncomingCertificate().getPublicationUri();

        KeyPairEntity oldKeyPair = parent.getCurrentKeyPair();
        KeyPairEntity newKeyPair = createInitialisedProductionCaKeyPair(certificateRequestCreationService, parent, "NEW-KEY");

        assertThat(newKeyPair.getStatus()).isEqualTo(KeyPairStatus.PENDING);
        assertChildParentInvariants(child, parent);

        execute(KeyManagementActivatePendingKeysCommand.plannedActivationCommand(parent.getVersionedId(), Duration.ZERO));

        assertThat(oldKeyPair.getStatus()).isEqualTo(KeyPairStatus.OLD);
        assertThat(newKeyPair.getStatus()).isEqualTo(KeyPairStatus.CURRENT);
        assertChildParentInvariants(child, parent);

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION)));

        assertChildParentInvariants(child, parent);
        IncomingResourceCertificate certificate2 = child.getCurrentIncomingCertificate();
        assertThat(certificate1).isNotEqualTo(certificate2.getCertificate());
        assertThat(publicationUri1).isEqualTo(certificate2.getPublicationUri());
    }

    private void assertChildParentInvariants(CustomerCertificateAuthority child, HostedCertificateAuthority parent) {
        // For every published, outgoing certificate in parent there should be a matching incoming certificate in child.
        // A child should never be left without a published outgoing certificate for each of its publishable keys.
        Set<PublicKey> childPublicKeys = child.getKeyPairs().stream()
            .filter(KeyPairEntity::isPublishable)
            .map(KeyPairEntity::getPublicKey)
            .collect(Collectors.toSet());

        Collection<OutgoingResourceCertificate> outgoingResourceCertificates = parent.getKeyPairs().stream()
            .filter(KeyPairEntity::isPublishable)
            .flatMap(kp -> resourceCertificateRepository.findAllBySigningKeyPair(kp).stream())
            .filter(c -> c.isCurrent() && PublicationStatus.ACTIVE_STATUSES.contains(c.getPublishedObject().getStatus()))
            .filter(c -> childPublicKeys.contains(c.getSubjectPublicKey()))
            .collect(Collectors.toList());
        Collection<IncomingResourceCertificate> incomingResourceCertificates = child.getKeyPairs().stream()
            .filter(KeyPairEntity::isPublishable)
            .flatMap(kp -> kp.findCurrentIncomingCertificate().map(Stream::of).orElse(Stream.empty()))
            .collect(Collectors.toList());

        assertThat(childPublicKeys).hasSize(outgoingResourceCertificates.size());
        assertThat(outgoingResourceCertificates).hasSize(incomingResourceCertificates.size());

        outgoingResourceCertificates.stream().forEach(outgoing -> {
            IncomingResourceCertificate incoming = incomingResourceCertificates.stream()
                .filter(certificate -> outgoing.getSerial().equals(certificate.getSerial()))
                .findFirst()
                .orElseThrow(() -> new AssertionFailedError("missing incoming certificate with serial " + outgoing.getSerial()));
            assertThat(outgoing.getCertificate()).isEqualTo(incoming.getCertificate());
        });
    }

    private static IpResourceSet resources(String resources) {
        return IpResourceSet.parse(resources);
    }

    private CommandStatus execute(CertificateAuthorityCommand command) {
        try {
            return subject.execute(command);
        } finally {
            entityManager.flush();
        }
    }
}
