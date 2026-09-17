package net.ripe.rpki.services.impl.jpa;

import jakarta.inject.Inject;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.bgpsec.BgpSecCertificate;
import net.ripe.rpki.domain.bgpsec.BgpSecCertificateRepository;
import net.ripe.rpki.domain.bgpsec.BgpSecEntity;
import net.ripe.rpki.domain.bgpsec.BgpSecEntityRepository;
import net.ripe.rpki.server.api.dto.CertificateStatus;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.Collection;

import static net.ripe.rpki.domain.bgpsec.BgpSecCertificateFixtures.createBgpSecCertificateFromCSR;
import static org.assertj.core.api.Assertions.assertThat;

public class JpaBgpSecCertificateRepositoryTest extends CertificationDomainTestCase {

    @Inject
    private JpaBgpSecCertificateRepository subject;

    @Inject
    private BgpSecEntityRepository bgpSecEntityRepository;
    private Long signingKeyPairId;
    private URI directory;

    @Before
    public void setUp() {
        withTx_(() -> {
            clearDatabase();
            ProductionCertificateAuthority ca = createInitialisedProdCaWithRipeResources();
            signingKeyPairId = ca.getCurrentKeyPair().getId();
        });
        directory = URI.create("rsync://localhost/bgpsec/");
    }

    @Test
    public void deleteExpiredBgpSecCertificates_emptyDatabase_returnsZero() {
        int deleted = subject.deleteExpiredBgpSecCertificates(new DateTime(DateTimeZone.UTC).minusDays(1));
        assertThat(deleted).isZero();
    }

    @Test
    public void deleteExpiredBgpSecCertificates_removesOnlyOldEnoughExpiredCertificates() {
        withTx_(()-> {
            KeyPairEntity kp = entityManager.find(KeyPairEntity.class, signingKeyPairId);
            // Already-expired cert (expired 3 days ago, past the 2-day grace period)
            BgpSecCertificate oldExpiredCert = createBgpSecCertificateFromCSR(
                    kp, 1L, DateTime.now().minusYears(2), DateTime.now().minusDays(3), CertificateStatus.EXPIRED);
            // Recently-expired cert (expired 1 day ago, within the 2-day grace period)
            BgpSecCertificate recentlyExpiredCert = createBgpSecCertificateFromCSR(
                    kp, 2L, DateTime.now().minusYears(2), DateTime.now().minusDays(1), CertificateStatus.EXPIRED);
            // Still-current cert — must not be touched
            BgpSecCertificate currentCert = createBgpSecCertificateFromCSR(
                    kp, 3L, DateTime.now(), DateTime.now().plusYears(1), CertificateStatus.CURRENT);
            subject.add(oldExpiredCert);
            subject.add(recentlyExpiredCert);
            subject.add(currentCert);
            entityManager.flush();
            assertThat(subject.findAll()).hasSize(3);
        });

        withTx_(()-> {
            // Delete certs expired more than 2 days ago
            DateTime gracePeriodCutoff = new DateTime(DateTimeZone.UTC).minusDays(2);
            int deleted = subject.deleteExpiredBgpSecCertificates(gracePeriodCutoff);
            assertThat(deleted).isEqualTo(1);
        });

        withTx_(() -> {
            // Only the recently-expired and current certs should remain
            assertThat(subject.findAll()).hasSize(2)
                    .allSatisfy(cert -> assertThat(cert.getSerial().longValue()).isIn(2L, 3L));
        });
    }

    @Test
    public void expireOutdatedBgpSecCertificates_emptyDatabase_returnsZeroCounts() {
        BgpSecCertificateRepository.ExpireBgpSecCertificatesResult result = subject.expireOutdatedBgpSecCertificates(DateTime.now());

        assertThat(result.getExpiredCertificateCount()).isZero();
        assertThat(result.getDeletedBgpSecCount()).isZero();
        assertThat(result.getWithdrawnObjectCount()).isZero();
    }

    @Test
    public void outgoing_bgpsec_certificate_should_change_to_expired_after_not_valid_after_timestamp() {
        withTx_(() -> {
            KeyPairEntity kp = entityManager.find(KeyPairEntity.class, signingKeyPairId);
            BgpSecCertificate currentCert = createBgpSecCertificateFromCSR(
                    kp, 1L, DateTime.now(), DateTime.now().plusYears(1), CertificateStatus.CURRENT);
            subject.add(currentCert);
            bgpSecEntityRepository.add(new BgpSecEntity(
                    Asn.parse("AS64496"), "TestKeyId", 123L, currentCert, "bgpsec-1.cer", directory));
            entityManager.flush();
            assertThat(subject.findAll()).hasSize(1)
                    .allSatisfy(cert -> assertThat(cert.getStatus()).isEqualTo(CertificateStatus.CURRENT));
            assertThat(bgpSecEntityRepository.findAll()).hasSize(1)
                    .allSatisfy(entity -> assertThat(entity.getPublishedObject().getStatus())
                            .isIn(PublicationStatus.TO_BE_PUBLISHED, PublicationStatus.PUBLISHED));
        });

        withTx_(() -> {
            // Nothing should expire yet (now is within validity period)
            DateTime now = new DateTime(DateTimeZone.UTC);
            BgpSecCertificateRepository.ExpireBgpSecCertificatesResult result = subject.expireOutdatedBgpSecCertificates(now);
            assertThat(result.getExpiredCertificateCount()).isZero();
            assertThat(result.getDeletedBgpSecCount()).isZero();
            assertThat(result.getWithdrawnObjectCount()).isZero();
        });

        withTx_(() -> {
            // Verify cert is still CURRENT after no-op expiration
            DateTime now = new DateTime(DateTimeZone.UTC);
            assertThat(subject.findAll()).hasSize(1)
                    .allSatisfy(cert -> {
                        assertThat(cert.getStatus()).isEqualTo(CertificateStatus.CURRENT);
                        assertThat(cert.getNotValidAfter()).isGreaterThanOrEqualTo(now);
                    });
            assertThat(bgpSecEntityRepository.findAll()).hasSize(1);
        });

        withTx_(() -> {
            DateTime afterValidity = new DateTime(DateTimeZone.UTC).plusYears(2);
            BgpSecCertificateRepository.ExpireBgpSecCertificatesResult result = subject.expireOutdatedBgpSecCertificates(afterValidity);
            assertThat(result.getExpiredCertificateCount()).isEqualTo(1);
            assertThat(result.getDeletedBgpSecCount()).isEqualTo(1);
            assertThat(result.getWithdrawnObjectCount()).isEqualTo(1);
        });

        withTx_(() -> {
            // Verify cert is EXPIRED in a fresh transaction
            DateTime afterValidity = new DateTime(DateTimeZone.UTC).plusYears(2);
            assertThat(subject.findAll()).hasSize(1).allSatisfy(cert -> {
                assertThat(cert.getStatus()).isEqualTo(CertificateStatus.EXPIRED);
                assertThat(cert.getNotValidAfter()).isLessThan(afterValidity);
            });
            assertThat(bgpSecEntityRepository.findAll()).isEmpty();
        });

        withTx_(() -> {
            // Running again should have no effect
            DateTime afterValidity = new DateTime(DateTimeZone.UTC).plusYears(2);
            BgpSecCertificateRepository.ExpireBgpSecCertificatesResult result = subject.expireOutdatedBgpSecCertificates(afterValidity);
            assertThat(result.getExpiredCertificateCount()).isZero();
            assertThat(result.getDeletedBgpSecCount()).isZero();
            assertThat(result.getWithdrawnObjectCount()).isZero();
        });
    }

    @Test
    public void multiple_bgpsec_certificates_expire_together() {
        withTx_(() -> {
            KeyPairEntity kp = entityManager.find(KeyPairEntity.class, signingKeyPairId);
            BgpSecCertificate cert1 = createBgpSecCertificateFromCSR(kp, 1L, DateTime.now(), DateTime.now().plusYears(1), CertificateStatus.CURRENT);
            BgpSecCertificate cert2 = createBgpSecCertificateFromCSR(kp, 2L, DateTime.now(), DateTime.now().plusYears(1), CertificateStatus.CURRENT);
            BgpSecCertificate cert3 = createBgpSecCertificateFromCSR(kp, 3L, DateTime.now(), DateTime.now().plusYears(1), CertificateStatus.CURRENT);
            subject.add(cert1);
            subject.add(cert2);
            subject.add(cert3);
            bgpSecEntityRepository.add(new BgpSecEntity(Asn.parse("AS64496"), "KeyId1", 1L, cert1, "bgpsec-1.cer", directory));
            bgpSecEntityRepository.add(new BgpSecEntity(Asn.parse("AS64497"), "KeyId2", 2L, cert2, "bgpsec-2.cer", directory));
            bgpSecEntityRepository.add(new BgpSecEntity(Asn.parse("AS64498"), "KeyId3", 3L, cert3, "bgpsec-3.cer", directory));
            entityManager.flush();
            assertThat(subject.findAll()).hasSize(3);
            assertThat(bgpSecEntityRepository.findAll()).hasSize(3);
        });

        withTx_(() -> {
            DateTime afterValidity = new DateTime(DateTimeZone.UTC).plusYears(2);
            BgpSecCertificateRepository.ExpireBgpSecCertificatesResult result = subject.expireOutdatedBgpSecCertificates(afterValidity);
            assertThat(result.getExpiredCertificateCount()).isEqualTo(3);
            assertThat(result.getDeletedBgpSecCount()).isEqualTo(3);
            assertThat(result.getWithdrawnObjectCount()).isEqualTo(3);
        });

        withTx_(() -> {
            // Verify entity states in a fresh transaction
            assertThat(subject.findAll()).hasSize(3)
                    .allSatisfy(cert -> assertThat(cert.getStatus()).isEqualTo(CertificateStatus.EXPIRED));
            assertThat(bgpSecEntityRepository.findAll()).isEmpty();
        });
    }

    @Test
    public void only_current_certificates_expire() {
        withTx_(() -> {
            KeyPairEntity kp = entityManager.find(KeyPairEntity.class, signingKeyPairId);
            BgpSecCertificate currentCert = createBgpSecCertificateFromCSR(kp, 1L, DateTime.now(), DateTime.now().plusYears(1), CertificateStatus.CURRENT);
            BgpSecCertificate expiredCert = createBgpSecCertificateFromCSR(kp, 2L, DateTime.now().minusYears(2), DateTime.now().minusYears(1), CertificateStatus.EXPIRED);
            BgpSecCertificate revokedCert = createBgpSecCertificateFromCSR(kp, 3L, DateTime.now(), DateTime.now().plusYears(1), CertificateStatus.REVOKED);
            subject.add(currentCert);
            subject.add(expiredCert);
            subject.add(revokedCert);
            bgpSecEntityRepository.add(new BgpSecEntity(Asn.parse("AS64496"), "KeyId1", 1L, currentCert, "bgpsec-1.cer", directory));
            entityManager.flush();
            assertThat(subject.findAll()).hasSize(3);
            assertThat(bgpSecEntityRepository.findAll()).hasSize(1);
        });

        withTx_(() -> {
            DateTime afterValidity = new DateTime(DateTimeZone.UTC).plusYears(2);
            BgpSecCertificateRepository.ExpireBgpSecCertificatesResult result = subject.expireOutdatedBgpSecCertificates(afterValidity);
            assertThat(result.getExpiredCertificateCount()).isEqualTo(2);
            assertThat(result.getDeletedBgpSecCount()).isEqualTo(1);
            assertThat(result.getWithdrawnObjectCount()).isEqualTo(1);
        });

        withTx_(() -> {
            Collection<BgpSecCertificate> allCerts = subject.findAll();
            assertThat(allCerts).hasSize(3);
            assertThat(allCerts).allSatisfy(cert -> assertThat(cert.getStatus()).isEqualTo(CertificateStatus.EXPIRED));
        });
    }

    @Test
    public void published_objects_are_updated_correctly() {
        withTx_(() -> {
            KeyPairEntity kp = entityManager.find(KeyPairEntity.class, signingKeyPairId);
            BgpSecCertificate cert = createBgpSecCertificateFromCSR(kp, 1L, DateTime.now(), DateTime.now().plusYears(1), CertificateStatus.CURRENT);
            subject.add(cert);
            bgpSecEntityRepository.add(new BgpSecEntity(Asn.parse("AS64496"), "KeyId1", 1L, cert, "bgpsec-1.cer", directory));
            entityManager.flush();
            assertThat(publishedObjectRepository.findAll())
                    .filteredOn(po -> po.getUri().getPath().contains("bgpsec-1.cer"))
                    .hasSize(1)
                    .allSatisfy(po -> assertThat(po.getStatus())
                            .isIn(PublicationStatus.TO_BE_PUBLISHED, PublicationStatus.PUBLISHED));
        });

        withTx_(() -> {
            DateTime afterValidity = new DateTime(DateTimeZone.UTC).plusYears(2);
            BgpSecCertificateRepository.ExpireBgpSecCertificatesResult result = subject.expireOutdatedBgpSecCertificates(afterValidity);
            assertThat(result.getExpiredCertificateCount()).isEqualTo(1);
            assertThat(result.getWithdrawnObjectCount()).isEqualTo(1);
        });

        withTx_(() -> assertThat(bgpSecEntityRepository.findAll()).isEmpty());
    }

    @Test
    public void partial_expiration_leaves_non_expired_certificates() {
        withTx_(() -> {
            KeyPairEntity kp = entityManager.find(KeyPairEntity.class, signingKeyPairId);
            BgpSecCertificate earlyExpiringCert = createBgpSecCertificateFromCSR(kp, 1L, DateTime.now(), DateTime.now().plusDays(1), CertificateStatus.CURRENT);
            BgpSecCertificate lateExpiringCert = createBgpSecCertificateFromCSR(kp, 2L, DateTime.now(), DateTime.now().plusYears(2), CertificateStatus.CURRENT);
            subject.add(earlyExpiringCert);
            subject.add(lateExpiringCert);
            bgpSecEntityRepository.add(new BgpSecEntity(Asn.parse("AS64496"), "KeyId1", 1L, earlyExpiringCert, "bgpsec-1.cer", directory));
            bgpSecEntityRepository.add(new BgpSecEntity(Asn.parse("AS64497"), "KeyId2", 2L, lateExpiringCert, "bgpsec-2.cer", directory));
            entityManager.flush();
            assertThat(subject.findAll()).hasSize(2);
            assertThat(bgpSecEntityRepository.findAll()).hasSize(2);
        });

        withTx_(() -> {
            // Expire at a time when only one certificate is expired (after day 1, before 2 years)
            DateTime partialExpiry = new DateTime(DateTimeZone.UTC).plusDays(10);
            BgpSecCertificateRepository.ExpireBgpSecCertificatesResult result = subject.expireOutdatedBgpSecCertificates(partialExpiry);
            assertThat(result.getExpiredCertificateCount()).isEqualTo(1);
            assertThat(result.getDeletedBgpSecCount()).isEqualTo(1);
            assertThat(result.getWithdrawnObjectCount()).isEqualTo(1);
        });

        withTx_(() -> {
            // Verify entity states in a fresh transaction
            Collection<BgpSecCertificate> allCerts = subject.findAll();
            assertThat(allCerts).hasSize(2);
            assertThat(allCerts)
                    .filteredOn(cert -> cert.getSerial().longValue() == 1L)
                    .allSatisfy(cert -> assertThat(cert.getStatus()).isEqualTo(CertificateStatus.EXPIRED));
            assertThat(allCerts)
                    .filteredOn(cert -> cert.getSerial().longValue() == 2L)
                    .allSatisfy(cert -> assertThat(cert.getStatus()).isEqualTo(CertificateStatus.CURRENT));
            assertThat(bgpSecEntityRepository.findAll()).hasSize(1);
        });
    }

}
