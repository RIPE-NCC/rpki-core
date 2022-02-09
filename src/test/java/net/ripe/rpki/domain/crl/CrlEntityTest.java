package net.ripe.rpki.domain.crl;

import net.ripe.rpki.commons.FixedDateRule;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


@Transactional
public class CrlEntityTest extends CertificationDomainTestCase {

    @Rule
    public FixedDateRule fixedDateRule = new FixedDateRule(new DateTime(2008, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC));

    private KeyPairEntity keyPair;
    private CrlEntity subject;
    private DateTime now;
    private ValidityPeriod validityPeriod;

    @Before
    public void setUp() {
        clearDatabase();

        now = DateTime.now(DateTimeZone.UTC);
        validityPeriod = new ValidityPeriod(now, now.plusHours(24));
        HostedCertificateAuthority ca = createInitialisedProdCaWithRipeResources();
        keyPair = ca.getCurrentKeyPair();
        subject = new CrlEntity(keyPair);
    }

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void shouldGenerateEmptyCrlWhenThereAreNoRevokedCertificates() {
        subject.update(validityPeriod, resourceCertificateRepository);
        assertTrue(subject.getCrl().getRevokedCertificates().isEmpty());
        assertEquals(2L, subject.getNextNumber());
    }

    @Test
    public void shouldIncludeRevokedCertificate() {
        OutgoingResourceCertificate revokedCertificate = resourceCertificateRepository.findLatestOutgoingCertificate(keyPair.getPublicKey(), keyPair);
        revokedCertificate.revoke();

        subject.update(validityPeriod, resourceCertificateRepository);
        assertEquals(1, subject.getCrl().getRevokedCertificates().size());
        assertNotNull(subject.getCrl().getRevokedCertificate(revokedCertificate.getSerial()));
    }

    @Test
    public void shouldExcludeExpiredRevokedCertificate() {
        OutgoingResourceCertificate revokedCertificate = resourceCertificateRepository.findLatestOutgoingCertificate(keyPair.getPublicKey(), keyPair);
        revokedCertificate.revoke();
        assertTrue(revokedCertificate.isRevoked());

        now = new DateTime(2012, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        revokedCertificate.expire(now);
        assertTrue(revokedCertificate.isExpired());

        subject.update(validityPeriod, resourceCertificateRepository);
        assertTrue(subject.getCrl().getRevokedCertificates().isEmpty());
    }

    @Test
    public void shouldNotUpdateWhenRecentCrlIsStillValid() {
        subject.update(validityPeriod, resourceCertificateRepository);

        now = now.plusHours(8);
        assertFalse(subject.isUpdateNeeded(now, resourceCertificateRepository));
    }

    @Test
    public void shouldUpdateWhenCurrentCrlWillExpireWithinGracePeriod() {
        subject.update(validityPeriod, resourceCertificateRepository);

        now = now.plusHours(8).plusMinutes(1);
        assertTrue(subject.isUpdateNeeded(now, resourceCertificateRepository));
    }

    @Test
    public void shouldUpdateWhenNewEntryNeedsToBeAdded() {
        subject.update(validityPeriod, resourceCertificateRepository);
        OutgoingResourceCertificate revokedCertificate = resourceCertificateRepository.findLatestOutgoingCertificate(keyPair.getPublicKey(), keyPair);
        revokedCertificate.revoke();

        assertTrue(subject.isUpdateNeeded(validityPeriod.getNotValidBefore(), resourceCertificateRepository));
    }

    @Test
    public void shouldNotUpdateWhenKeyIsRevoked() {
        subject.update(validityPeriod, resourceCertificateRepository);

        OutgoingResourceCertificate revokedCertificate = resourceCertificateRepository.findLatestOutgoingCertificate(keyPair.getPublicKey(), keyPair);
        revokedCertificate.revoke();
        keyPair.deactivate();

        keyPair.revoke(publishedObjectRepository);

        assertFalse(subject.isUpdateNeeded(now, resourceCertificateRepository));
    }

    @Test
    public void shouldNotUpdateWhenRevokedCertificateExpires() {
        OutgoingResourceCertificate certificateToRevoke = resourceCertificateRepository.findLatestOutgoingCertificate(keyPair.getPublicKey(), keyPair);
        certificateToRevoke.revoke();

        now = certificateToRevoke.getNotValidAfter().minusHours(1);
        subject.update(new ValidityPeriod(now, now.plusHours(24)), resourceCertificateRepository);

        DateTimeUtils.setCurrentMillisFixed(certificateToRevoke.getNotValidAfter().plusHours(1).getMillis());
        assertFalse(subject.isUpdateNeeded(now, resourceCertificateRepository));
    }

    @Ignore
    @Test
    public void shouldOnlyContainCertificatesSignedBySameKeyPair() {
        // TODO: Move to integration test (do key roll, re-publish, check CRL for revoked manifest)
    }
}
