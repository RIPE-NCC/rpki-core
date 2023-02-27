package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.ripencc.cache.JpaResourceCacheImpl;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.KeyManagementActivatePendingKeysCommand;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import net.ripe.rpki.server.api.services.command.CertificationResourceLimitExceededException;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static net.ripe.ipresource.ImmutableResourceSet.parse;
import static net.ripe.rpki.domain.NonHostedCertificateAuthority.INCOMING_RESOURCE_CERTIFICATES_PER_PUBLIC_KEY_LIMIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
public class ChildParentCertificateUpdateSagaNonHostedTest extends CertificationDomainTestCase {

    private static final long CHILD_CA_ID = 7L;

    private static final X500Principal CHILD_CA_NAME = new X500Principal("CN=child");
    public static final List<X509CertificateInformationAccessDescriptor> SIA = Arrays.asList(
        new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY, URI.create("rsync://example.com/rpki/repository")),
        new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST, URI.create("rsync://example.com/rpki/repository/manifest.mft"))
    );

    @Inject
    private JpaResourceCacheImpl resourceCache;
    @Inject
    private CertificateRequestCreationService certificateRequestCreationService;

    private ProductionCertificateAuthority parent;
    private NonHostedCertificateAuthority child;

    private static final PublicKey PUBLIC_KEY = TestObjects.createTestKeyPair().getPublicKey();
    private PublicKeyEntity publicKeyEntity;

    @Before
    public void setUp() {
        clearDatabase();

        parent = createInitialisedProdCaWithRipeResources();
        child = new NonHostedCertificateAuthority(CHILD_CA_ID, CHILD_CA_NAME, ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT, parent);
        publicKeyEntity = child.findOrCreatePublicKeyEntityByPublicKey(PUBLIC_KEY);
        // Request all resources
        publicKeyEntity.setLatestIssuanceRequest(new RequestedResourceSets(), SIA);

        certificateAuthorityRepository.add(child);
    }

    @Test
    public void should_issue_certificate_for_non_hosted_child_certified_resources() {
        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), parse("10.10.0.0/16"));

        CommandStatus status = execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CHILD_CA_ID, VersionedId.INITIAL_VERSION), INCOMING_RESOURCE_CERTIFICATES_PER_PUBLIC_KEY_LIMIT));

        assertThat(status.isHasEffect()).as("command has effect").isTrue();

        Collection<PublicKeyEntity> keyPairs = child.getPublicKeyEntities();
        assertThat(keyPairs).hasSize(1);

        Optional<OutgoingResourceCertificate> certificate = findCurrentResourceCertificate(child);
        assertThat(certificate).isPresent();
        assertThat(certificate.get().getResources()).isEqualTo(parse("10.10.0.0/16"));

        assertChildParentInvariants(child, parent);
    }

    @Test
    public void should_keep_same_certificate_when_resources_are_unchanged() {
        should_issue_certificate_for_non_hosted_child_certified_resources();
        OutgoingResourceCertificate certificate = findCurrentResourceCertificate(child).orElseThrow(() -> new IllegalStateException("missing certificate"));

        CommandStatus status = execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CHILD_CA_ID, VersionedId.INITIAL_VERSION), INCOMING_RESOURCE_CERTIFICATES_PER_PUBLIC_KEY_LIMIT));

        assertThat(status.isHasEffect()).isFalse();
        assertThat(findCurrentResourceCertificate(child)).isPresent().hasValueSatisfying(v -> assertThat(v).isSameAs(certificate));
        assertChildParentInvariants(child, parent);
    }

    @Test
    public void should_revoke_issued_certificate_for_hosted_child_without_certifiable_parse() {
        should_issue_certificate_for_non_hosted_child_certified_resources();
        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), parse(""));

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CHILD_CA_ID, VersionedId.INITIAL_VERSION), INCOMING_RESOURCE_CERTIFICATES_PER_PUBLIC_KEY_LIMIT));

        Collection<PublicKeyEntity> publicKeys = child.getPublicKeyEntities();
        assertThat(publicKeys).hasSize(1);

        Optional<OutgoingResourceCertificate> certificate = findCurrentResourceCertificate(child);
        assertThat(certificate).isEmpty();

        assertChildParentInvariants(child, parent);
    }

    @Test
    public void should_revoke_issued_certificate_for_hosted_child_when_certifiable_resources_do_not_match_requested_resources() {
        should_issue_certificate_for_non_hosted_child_certified_resources();
        publicKeyEntity.setLatestIssuanceRequest(new RequestedResourceSets(
                Optional.empty(),
                Optional.of(parse("192.168.0.0/16")),
                Optional.empty()
            ),
            SIA
        );

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CHILD_CA_ID, VersionedId.INITIAL_VERSION), INCOMING_RESOURCE_CERTIFICATES_PER_PUBLIC_KEY_LIMIT));

        Collection<PublicKeyEntity> publicKeys = child.getPublicKeyEntities();
        assertThat(publicKeys).hasSize(1);

        Optional<OutgoingResourceCertificate> certificate = findCurrentResourceCertificate(child);
        assertThat(certificate).isEmpty();

        assertChildParentInvariants(child, parent);
    }

    @Test
    public void should_revoke_certificate_when_requested_by_child() {
        should_issue_certificate_for_non_hosted_child_certified_resources();
        publicKeyEntity.setLatestRevocationRequest();

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CHILD_CA_ID, VersionedId.INITIAL_VERSION), INCOMING_RESOURCE_CERTIFICATES_PER_PUBLIC_KEY_LIMIT));

        Collection<PublicKeyEntity> publicKeys = child.getPublicKeyEntities();
        assertThat(publicKeys).hasSize(1);

        Optional<OutgoingResourceCertificate> certificate = findCurrentResourceCertificate(child);
        assertThat(certificate).isEmpty();

        assertChildParentInvariants(child, parent);
    }

    // Public keys should always have a certificate when the last request was a certificate issuance request. However,
    // if a child no longer has any resources, the certificates get revoked. When they have resources again we re-issue
    // the certificates using the information from the latest issuance request.
    @Test
    public void should_reissue_certificate_when_child_has_resources_again() {
        should_revoke_issued_certificate_for_hosted_child_without_certifiable_parse();
        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), parse("10.10.0.0/16"));

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CHILD_CA_ID, VersionedId.INITIAL_VERSION), INCOMING_RESOURCE_CERTIFICATES_PER_PUBLIC_KEY_LIMIT));

        Optional<OutgoingResourceCertificate> certificate = findCurrentResourceCertificate(child);
        assertThat(certificate).hasValueSatisfying(cert -> {
            assertThat(cert.getResources()).isEqualTo(parse("10.10.0.0/16"));
        });

        assertChildParentInvariants(child, parent);
    }

    @Test
    public void should_keep_requested_resources_when_non_hosted_child_resources_expand() {
        should_issue_certificate_for_non_hosted_child_certified_resources();
        publicKeyEntity.setLatestIssuanceRequest(new RequestedResourceSets(
                Optional.empty(),
                Optional.of(parse("10.10.0.0/16")),
                Optional.empty()
            ),
            SIA
        );
        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), parse("10.10.0.0/16, 10.20.0.0/16"));

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CHILD_CA_ID, VersionedId.INITIAL_VERSION), INCOMING_RESOURCE_CERTIFICATES_PER_PUBLIC_KEY_LIMIT));

        Optional<OutgoingResourceCertificate> certificate = findCurrentResourceCertificate(child);
        assertThat(certificate).isPresent();
        assertThat(certificate.get().getResources()).isEqualTo(parse("10.10.0.0/16"));

        assertChildParentInvariants(child, parent);
    }

    @Test
    public void should_update_certificates_resources_when_non_hosted_child_resources_expand_and_are_included_in_requested_parse() {
        should_issue_certificate_for_non_hosted_child_certified_resources();
        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), parse("10.10.0.0/16, 10.20.0.0/16"));

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CHILD_CA_ID, VersionedId.INITIAL_VERSION), INCOMING_RESOURCE_CERTIFICATES_PER_PUBLIC_KEY_LIMIT));

        Optional<OutgoingResourceCertificate> certificate = findCurrentResourceCertificate(child);
        assertThat(certificate).isPresent();
        assertThat(certificate.get().getResources()).isEqualTo(parse("10.10.0.0/16, 10.20.0.0/16"));

        assertChildParentInvariants(child, parent);
    }

    @Test
    public void should_issue_new_certificate_when_non_hosted_child_resources_contract() {
        should_issue_certificate_for_non_hosted_child_certified_resources();
        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), parse("10.10.0.0/20"));

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CHILD_CA_ID, VersionedId.INITIAL_VERSION), INCOMING_RESOURCE_CERTIFICATES_PER_PUBLIC_KEY_LIMIT));

        Optional<OutgoingResourceCertificate> certificate = findCurrentResourceCertificate(child);
        assertThat(certificate).isPresent();
        assertThat(certificate.get().getResources()).isEqualTo(parse("10.10.0.0/20"));

        assertChildParentInvariants(child, parent);
    }

    @Test
    public void should_revoke_previous_certificate_and_issue_new_certificate_after_parent_key_rollover() {
        // When the parent replaces the CURRENT key with a PENDING key (making the current key OLD and the PENDING key CURRENT)
        // the outgoing child certificates issued by the OLD key should be replaced when the child certificate is re-issued by
        // the PENDING (now CURRENT) key.
        should_issue_certificate_for_non_hosted_child_certified_resources();
        X509ResourceCertificate certificate1 = findCurrentResourceCertificate(child).get().getCertificate();
        URI publicationUri1 = findCurrentResourceCertificate(child).get().getPublicationUri();

        KeyPairEntity oldKeyPair = parent.getCurrentKeyPair();
        KeyPairEntity newKeyPair = createInitialisedProductionCaKeyPair(certificateRequestCreationService, parent, "NEW-KEY");

        assertThat(newKeyPair.getStatus()).isEqualTo(KeyPairStatus.PENDING);
        assertChildParentInvariants(child, parent);

        execute(KeyManagementActivatePendingKeysCommand.plannedActivationCommand(parent.getVersionedId(), Duration.ZERO));

        assertThat(oldKeyPair.getStatus()).isEqualTo(KeyPairStatus.OLD);
        assertThat(newKeyPair.getStatus()).isEqualTo(KeyPairStatus.CURRENT);
        assertChildParentInvariants(child, parent);

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CHILD_CA_ID, VersionedId.INITIAL_VERSION), INCOMING_RESOURCE_CERTIFICATES_PER_PUBLIC_KEY_LIMIT));

        assertChildParentInvariants(child, parent);
        OutgoingResourceCertificate certificate2 = findCurrentResourceCertificate(child).get();
        assertThat(certificate1).isNotEqualTo(certificate2.getCertificate());
        assertThat(publicationUri1).isEqualTo(certificate2.getPublicationUri());
    }

    @Test
    public void should_limit_number_of_certificates_when_requested_by_non_hosted_ca() {
        should_issue_certificate_for_non_hosted_child_certified_resources();
        publicKeyEntity.setLatestIssuanceRequest(new RequestedResourceSets(
                Optional.empty(),
                Optional.of(parse("10.10.8.0/24")),
                Optional.empty()
            ),
            SIA
        );
        OutgoingResourceCertificate certificate = findCurrentResourceCertificate(child).orElseThrow(() -> new IllegalStateException("missing certificate"));

        assertThatThrownBy(() -> execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CHILD_CA_ID, VersionedId.INITIAL_VERSION), 1)))
            .isInstanceOf(CertificationResourceLimitExceededException.class);

        assertThat(findCurrentResourceCertificate(child)).isPresent().hasValueSatisfying(v -> assertThat(v).isSameAs(certificate));
        assertChildParentInvariants(child, parent);
    }

    private void assertChildParentInvariants(NonHostedCertificateAuthority child, ManagedCertificateAuthority parent) {
        // For every published, outgoing certificate in parent there should be a matching incoming certificate in child.
        // A child should never be left without a published outgoing certificate for each of its publishable keys.
        Set<PublicKey> childPublicKeys = child.getPublicKeyEntities().stream()
            .filter(x -> !x.isRevoked())
            .map(PublicKeyEntity::getPublicKey)
            .collect(Collectors.toSet());

        Collection<OutgoingResourceCertificate> outgoingResourceCertificates = parent.getKeyPairs().stream()
            .filter(KeyPairEntity::isPublishable)
            .flatMap(kp -> resourceCertificateRepository.findAllBySigningKeyPair(kp).stream())
            .filter(c -> c.isCurrent() && PublicationStatus.ACTIVE_STATUSES.contains(c.getPublishedObject().getStatus()))
            .filter(c -> childPublicKeys.contains(c.getSubjectPublicKey()))
            .collect(Collectors.toList());
        Collection<OutgoingResourceCertificate> incomingResourceCertificates = child.getPublicKeyEntities().stream()
            .filter(x -> !x.isRevoked())
            .flatMap(x -> x.findCurrentOutgoingResourceCertificate().stream())
            .collect(Collectors.toList());

        // Not all non-hosted public keys will have a certificate after a certificate revocation request,
        // so number of keys could be greater.
        assertThat(childPublicKeys).hasSizeGreaterThanOrEqualTo(outgoingResourceCertificates.size());
        assertThat(outgoingResourceCertificates).hasSize(incomingResourceCertificates.size());

        outgoingResourceCertificates.forEach(outgoing -> {
            assertThat(outgoing.getRequestingCertificateAuthority()).isEqualTo(child);
            OutgoingResourceCertificate incoming = incomingResourceCertificates.stream()
                .filter(certificate -> outgoing.getSerial().equals(certificate.getSerial()))
                .findFirst()
                .orElseThrow(() -> new AssertionFailedError("missing incoming certificate with serial " + outgoing.getSerial()));
            assertThat(outgoing.getCertificate()).isEqualTo(incoming.getCertificate());
        });
    }

    private Optional<OutgoingResourceCertificate> findCurrentResourceCertificate(NonHostedCertificateAuthority ca) {
        return ca.getPublicKeyEntities().iterator().next().findCurrentOutgoingResourceCertificate();
    }

    private CommandStatus execute(CertificateAuthorityCommand command) {
        try {
            return commandService.execute(command);
        } finally {
            entityManager.flush();
        }
    }
}
