package net.ripe.rpki.services.impl.jpa;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.PublicationStatus;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.domain.roa.RoaEntityRepository;
import net.ripe.rpki.server.api.commands.UpdateRoaConfigurationCommand;
import net.ripe.rpki.server.api.dto.OutgoingResourceCertificateStatus;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import javax.security.auth.x500.X500Principal;
import javax.transaction.Transactional;
import java.util.Collections;

import static net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest.TEST_KEY_PAIR;
import static org.assertj.core.api.Assertions.assertThat;

public class JpaResourceCertificateRepositoryTest extends CertificationDomainTestCase {

    @Inject
    private JpaResourceCertificateRepository subject;

    @Inject
    private RoaEntityRepository roaEntityRepository;

    @Before
    public void setUp() {
        transactionTemplate.executeWithoutResult((status) -> clearDatabase());
    }

    @Test
    @Transactional
    public void expireOutgoingResourceCertificates() {
        assertThat(subject.expireOutgoingResourceCertificates(DateTime.now())).isEqualTo(
            new ResourceCertificateRepository.ExpireOutgoingResourceCertificatesResult(0, 0, 0)
        );
    }

    @Test
    public void outgoing_resource_certificate_should_change_to_expired_after_not_valid_after_timestamp() {
        HostedCertificateAuthority ca = transactionTemplate.execute((status) -> createInitialisedProdCaWithRipeResources());
        commandService.execute(new UpdateRoaConfigurationCommand(
            ca.getVersionedId(),
            Collections.singleton(new RoaConfigurationPrefixData(Asn.parse("AS3333"), IpRange.parse("10.0.0.0/8"), null)),
            Collections.emptyList()));

        // CA certificate, EE certificate for ROA
        assertThat(subject.findAllBySigningKeyPair(ca.getCurrentKeyPair()))
            .hasSize(2)
            .allSatisfy(cert -> assertThat(cert.getStatus()).isEqualTo(OutgoingResourceCertificateStatus.CURRENT));

        // ROA
        assertThat(roaEntityRepository.findAll()).hasSize(1);

        // CA certificate, ROA
        assertThat(publishedObjectRepository.findAll())
            .hasSize(2)
            .allSatisfy(po -> assertThat(po.getStatus()).isEqualTo(PublicationStatus.TO_BE_PUBLISHED));

        transactionTemplate.executeWithoutResult((status) -> {
            // Nothing to expire, so nothing changes.
            DateTime now = new DateTime(DateTimeZone.UTC);
            ResourceCertificateRepository.ExpireOutgoingResourceCertificatesResult expired = subject.expireOutgoingResourceCertificates(now);

            assertThat(expired.getExpiredCertificateCount()).isEqualTo(0);
            assertThat(expired.getDeletedRoaCount()).isEqualTo(0);
            assertThat(expired.getWithdrawnObjectCount()).isEqualTo(0);

            assertThat(subject.findAllBySigningKeyPair(ca.getCurrentKeyPair()))
                .hasSize(2)
                .allSatisfy(cert -> {
                    assertThat(cert.getNotValidAfter()).isGreaterThanOrEqualTo(now);
                    assertThat(cert.getStatus()).isEqualTo(OutgoingResourceCertificateStatus.CURRENT);
                });
            assertThat(roaEntityRepository.findAll()).hasSize(1);
            assertThat(publishedObjectRepository.findAll())
                .hasSize(2)
                .allSatisfy(po -> assertThat(po.getStatus()).isEqualTo(PublicationStatus.TO_BE_PUBLISHED));
        });

        transactionTemplate.executeWithoutResult((status) -> {
            // Outgoing resource certificates expire, ROA gets deleted, published objects withdrawn
            DateTime afterValidity = new DateTime(DateTimeZone.UTC).plusYears(2);
            ResourceCertificateRepository.ExpireOutgoingResourceCertificatesResult expired = subject.expireOutgoingResourceCertificates(afterValidity);

            assertThat(expired.getExpiredCertificateCount()).isEqualTo(2);
            assertThat(expired.getDeletedRoaCount()).isEqualTo(1);
            assertThat(expired.getWithdrawnObjectCount()).isEqualTo(2);

            assertThat(subject.findAllBySigningKeyPair(ca.getCurrentKeyPair()))
                .hasSize(2)
                .allSatisfy(cert -> {
                    assertThat(cert.getNotValidAfter()).isLessThan(afterValidity);
                    assertThat(cert.getStatus()).isEqualTo(OutgoingResourceCertificateStatus.EXPIRED);
                });
            assertThat(roaEntityRepository.findAll()).hasSize(0);
            assertThat(publishedObjectRepository.findAll())
                .hasSize(2)
                .allSatisfy(po -> assertThat(po.getStatus()).isEqualTo(PublicationStatus.WITHDRAWN));
        });

        transactionTemplate.executeWithoutResult((status) -> {
            // Running the expiration again should not have any affect
            DateTime afterValidity = new DateTime(DateTimeZone.UTC).plusYears(2);
            ResourceCertificateRepository.ExpireOutgoingResourceCertificatesResult expired = subject.expireOutgoingResourceCertificates(afterValidity);

            assertThat(expired.getExpiredCertificateCount()).isEqualTo(0);
            assertThat(expired.getDeletedRoaCount()).isEqualTo(0);
            assertThat(expired.getWithdrawnObjectCount()).isEqualTo(0);
        });
    }

    @Test
    @Transactional
    public void deleteExpiredOutgoingResourceCertificates() {
        assertThat(subject.deleteExpiredOutgoingResourceCertificates(new DateTime(DateTimeZone.UTC).minusDays(1))).isEqualTo(0);
    }

    @Test
    @Transactional
    public void countNonExpiredOutgoingCertificates() {
        ProductionCertificateAuthority ca = createInitialisedProdOrgCaWithRipeResources(certificateManagementService);
        entityManager.persist(ca);

        assertThat(subject.countNonExpiredOutgoingCertificates(TEST_KEY_PAIR.getPublic(), ca.getCurrentKeyPair())).isZero();
    }
}