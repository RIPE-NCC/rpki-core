package net.ripe.rpki.services.impl.jpa;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.roa.RoaEntityRepository;
import net.ripe.rpki.domain.roa.RoaEntityService;
import net.ripe.rpki.server.api.commands.*;
import net.ripe.rpki.server.api.dto.OutgoingResourceCertificateStatus;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import jakarta.inject.Inject;
import javax.security.auth.x500.X500Principal;
import jakarta.transaction.Transactional;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest.TEST_KEY_PAIR;
import static net.ripe.rpki.domain.TestObjects.PRODUCTION_CA_NAME;
import static net.ripe.rpki.domain.TestObjects.PRODUCTION_CA_RESOURCES;
import static org.assertj.core.api.Assertions.assertThat;

public class JpaResourceCertificateRepositoryTest extends CertificationDomainTestCase {

    @Inject
    private JpaResourceCertificateRepository subject;

    @Inject
    private RoaEntityRepository roaEntityRepository;

    @Inject
    private RoaEntityService roaEntityService;

    @Inject
    private ResourceCache resourceCache;

    @Before
    public void setUp() {
        transactionTemplate.executeWithoutResult((status) -> clearDatabase());
    }

    @Test
    @Transactional
    public void expireOutgoingResourceCertificates() {
        assertThat(subject.expireOutgoingResourceCertificates(DateTime.now())).isEqualTo(
            new ResourceCertificateRepository.ExpireOutgoingResourceCertificatesResult(0, 0, 0, 0)
        );
    }

    @Test
    public void outgoing_resource_certificate_should_change_to_expired_after_not_valid_after_timestamp() {
        ManagedCertificateAuthority ca = withTx(() -> createInitialisedProdCaWithRipeResources());
        commandService.execute(new UpdateRoaConfigurationCommand(
            ca.getVersionedId(),
            Optional.empty(),
            Collections.singleton(new RoaConfigurationPrefixData(Asn.parse("AS3333"), IpRange.parse("10.0.0.0/8"), null)),
            Collections.emptyList()));
        commandService.execute(new IssueUpdatedManifestAndCrlCommand(ca.getVersionedId()));

        // CA certificate, EE certificate for ROA, EE certificate for manifest
        assertThat(subject.findAllBySigningKeyPair(ca.getCurrentKeyPair()))
            .hasSize(3)
            .allSatisfy(cert -> assertThat(cert.getStatus()).isEqualTo(OutgoingResourceCertificateStatus.CURRENT));

        // ROA
        assertThat(roaEntityRepository.findAll()).hasSize(1);

        // CA certificate, ROA, CRL, Manifest
        assertThat(publishedObjectRepository.findAll())
            .hasSize(4)
            .allSatisfy(po -> assertThat(po.getStatus()).isEqualTo(PublicationStatus.PUBLISHED));

        transactionTemplate.executeWithoutResult((status) -> {
            // Nothing to expire, so nothing changes.
            DateTime now = new DateTime(DateTimeZone.UTC);
            ResourceCertificateRepository.ExpireOutgoingResourceCertificatesResult expired = subject.expireOutgoingResourceCertificates(now);

            assertThat(expired.getExpiredCertificateCount()).isZero();
            assertThat(expired.getDeletedRoaCount()).isZero();
            assertThat(expired.getWithdrawnObjectCount()).isZero();

            assertThat(subject.findAllBySigningKeyPair(ca.getCurrentKeyPair()))
                .hasSize(3)
                .allSatisfy(cert -> {
                    assertThat(cert.getNotValidAfter()).isGreaterThanOrEqualTo(now);
                    assertThat(cert.getStatus()).isEqualTo(OutgoingResourceCertificateStatus.CURRENT);
                });
            assertThat(roaEntityRepository.findAll()).hasSize(1);
            assertThat(publishedObjectRepository.findAll())
                .hasSize(4)
                .allSatisfy(po -> assertThat(po.getStatus()).isEqualTo(PublicationStatus.PUBLISHED));
        });

        transactionTemplate.executeWithoutResult((status) -> {
            // Outgoing resource certificates expire, ROA gets deleted, published objects withdrawn
            DateTime afterValidity = new DateTime(DateTimeZone.UTC).plusYears(2);
            ResourceCertificateRepository.ExpireOutgoingResourceCertificatesResult expired = subject.expireOutgoingResourceCertificates(afterValidity);

            assertThat(expired.getExpiredCertificateCount()).isEqualTo(3);
            assertThat(expired.getDeletedRoaCount()).isEqualTo(1);
            assertThat(expired.getWithdrawnObjectCount()).isEqualTo(4);

            assertThat(subject.findAllBySigningKeyPair(ca.getCurrentKeyPair()))
                .hasSize(3)
                .allSatisfy(cert -> {
                    assertThat(cert.getNotValidAfter()).isLessThan(afterValidity);
                    assertThat(cert.getStatus()).isEqualTo(OutgoingResourceCertificateStatus.EXPIRED);
                });
            assertThat(roaEntityRepository.findAll()).isEmpty();
            assertThat(publishedObjectRepository.findAll())
                .hasSize(4)
                .allSatisfy(po -> assertThat(po.getStatus()).isEqualTo(PublicationStatus.TO_BE_WITHDRAWN));
        });

        transactionTemplate.executeWithoutResult((status) -> {
            // Running the expiration again should not have any affect
            DateTime afterValidity = new DateTime(DateTimeZone.UTC).plusYears(2);
            ResourceCertificateRepository.ExpireOutgoingResourceCertificatesResult expired = subject.expireOutgoingResourceCertificates(afterValidity);

            assertThat(expired.getExpiredCertificateCount()).isZero();
            assertThat(expired.getDeletedRoaCount()).isZero();
            assertThat(expired.getWithdrawnObjectCount()).isZero();
        });
    }

    @Test
    @Transactional
    public void deleteExpiredOutgoingResourceCertificates() {
        assertThat(subject.deleteExpiredOutgoingResourceCertificates(new DateTime(DateTimeZone.UTC).minusDays(1))).isZero();
    }

    @Test
    @Transactional
    public void countNonExpiredOutgoingCertificates() {
        ProductionCertificateAuthority ca = createInitialisedProdCaWithRipeResources();

        assertThat(subject.countNonExpiredOutgoingCertificates(TEST_KEY_PAIR.getPublic(), ca.getCurrentKeyPair())).isZero();
    }

    @Test
    @Transactional
    public void findCurrentOutgoingChildCertificateResources() {
        VersionedId intermediateCaId = commandService.getNextId();
        X500Principal intermediateCaName = new X500Principal("CN=intermediate");
        X500Principal hosted1CaName = new X500Principal("CN=hosted");
        X500Principal hosted2CaName = new X500Principal("CN=hosted-2");
        ImmutableResourceSet hosted1CaResources = ImmutableResourceSet.parse("10.0.0.0/24");
        ImmutableResourceSet hosted2CaResources = ImmutableResourceSet.parse("10.0.1.0/24");
        resourceCache.populateCache(Map.ofEntries(
            Map.entry(CaName.of(PRODUCTION_CA_NAME), PRODUCTION_CA_RESOURCES),
            Map.entry(CaName.of(hosted1CaName), hosted1CaResources),
            Map.entry(CaName.of(hosted2CaName), hosted2CaResources)
        ));

        // Without any child CAs the set of issued child certificate resources is empty.
        ProductionCertificateAuthority productionCa = createInitialisedProdCaWithRipeResources();
        assertThat(subject.findCurrentOutgoingChildCertificateResources(PRODUCTION_CA_NAME)).isEqualTo(ImmutableResourceSet.empty());

        // Intermediate CA uses inherited resources, so the set of issued child certificate resources is still empty.
        commandService.execute(new CreateIntermediateCertificateAuthorityCommand(intermediateCaId, intermediateCaName, productionCa.getId()));
        assertThat(subject.findCurrentOutgoingChildCertificateResources(PRODUCTION_CA_NAME)).isEqualTo(ImmutableResourceSet.empty());

        // Hosted CAs get their resources on the certificate, so now these resources are part of the outgoing child resources
        // for both the production CA (recursive through the intermediate CA) and intermediate CA (directly)
        VersionedId hosted1CaId = commandService.getNextId();
        commandService.execute(new ActivateHostedCertificateAuthorityCommand(hosted1CaId, hosted1CaName, hosted1CaResources, intermediateCaId.getId()));
        assertThat(subject.findCurrentOutgoingChildCertificateResources(PRODUCTION_CA_NAME)).isEqualTo(hosted1CaResources);
        assertThat(subject.findCurrentOutgoingChildCertificateResources(intermediateCaName)).isEqualTo(hosted1CaResources);

        // The resources of a hosted CA that is a direct child of the root CA must also be included.
        commandService.execute(new ActivateHostedCertificateAuthorityCommand(commandService.getNextId(), hosted2CaName, hosted2CaResources, productionCa.getId()));
        assertThat(subject.findCurrentOutgoingChildCertificateResources(PRODUCTION_CA_NAME)).isEqualTo(hosted1CaResources.union(hosted2CaResources));
        assertThat(subject.findCurrentOutgoingChildCertificateResources(intermediateCaName)).isEqualTo(hosted1CaResources);

        // Resources of non-withdrawn certificates also count, so until a CA is published the non-withdrawn certificates
        // must still be covered by the parent CAs certificate.
        commandService.execute(new IssueUpdatedManifestAndCrlCommand(intermediateCaId));

        ImmutableResourceSet updatedHosted1CaResources = ImmutableResourceSet.parse("10.0.2.0/24");
        resourceCache.populateCache(Map.ofEntries(
            Map.entry(CaName.of(PRODUCTION_CA_NAME), PRODUCTION_CA_RESOURCES),
            Map.entry(CaName.of(hosted1CaName), updatedHosted1CaResources),
            Map.entry(CaName.of(hosted2CaName), hosted2CaResources)
        ));
        commandService.execute(new UpdateAllIncomingResourceCertificatesCommand(hosted1CaId, Integer.MAX_VALUE));
        assertThat(subject.findCurrentOutgoingChildCertificateResources(PRODUCTION_CA_NAME)).isEqualTo(hosted1CaResources.union(hosted2CaResources).union(updatedHosted1CaResources));

        // After updating the publication status only the updated resources count, as the old certificate is now
        // withdrawn from the RPKI repository.
        commandService.execute(new IssueUpdatedManifestAndCrlCommand(intermediateCaId));
        assertThat(subject.findCurrentOutgoingChildCertificateResources(PRODUCTION_CA_NAME)).isEqualTo(hosted2CaResources.union(updatedHosted1CaResources));
    }

    @Test
    @Transactional
    public void findCurrentOutgoingResourceCertificateResources() {
        ProductionCertificateAuthority ca = createInitialisedProdCaWithRipeResources();

        assertThat(subject.findCurrentOutgoingResourceCertificateResources(ca.getName())).isEqualTo(PRODUCTION_CA_RESOURCES);
    }

    @Test
    @Transactional
    public void existsCurrentOutgoingChildCertificates() {
        ProductionCertificateAuthority ca = createInitialisedProdCaWithRipeResources();

        assertThat(subject.existsCurrentOutgoingCertificatesExceptForManifest(ca.getCurrentKeyPair())).isFalse();
    }
}
