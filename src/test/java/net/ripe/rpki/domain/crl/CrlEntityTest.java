package net.ripe.rpki.domain.crl;

import net.ripe.rpki.commons.FixedDateRule;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.RevokedCertificateEntry;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.stream.Stream;


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
        ManagedCertificateAuthority ca = createInitialisedProdCaWithRipeResources();
        keyPair = ca.getCurrentKeyPair();
        subject = new CrlEntity(keyPair);
    }

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void shouldGenerateEmptyCrlWhenThereAreNoRevokedCertificates() {
        subject.update(validityPeriod, kp -> getRevokedCertificates(kp, validityPeriod.getNotValidBefore()));
        assertTrue(subject.getCrl().getRevokedCertificates().isEmpty());
        assertEquals(2L, subject.getNextNumber());
    }

    @Test
    public void shouldIncludeRevokedCertificate() {
        OutgoingResourceCertificate revokedCertificate = resourceCertificateRepository.findLatestOutgoingCertificate(keyPair.getPublicKey(), keyPair);
        revokedCertificate.revoke();

        subject.update(validityPeriod, kp -> getRevokedCertificates(kp, validityPeriod.getNotValidBefore()));
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

        subject.update(validityPeriod, kp -> getRevokedCertificates(kp, validityPeriod.getNotValidBefore()));
        assertTrue(subject.getCrl().getRevokedCertificates().isEmpty());
    }

    @Test
    public void shouldNotUpdateWhenRecentCrlIsStillValid() {
        subject.update(validityPeriod, kp -> getRevokedCertificates(kp, validityPeriod.getNotValidBefore()));

        now = now.plusHours(8);
        assertFalse(subject.isUpdateNeeded(now, kp -> getRevokedCertificates(kp, now)));
    }

    @Test
    public void shouldUpdateWhenCurrentCrlWillExpireWithinGracePeriod() {
        subject.update(validityPeriod, kp -> getRevokedCertificates(kp, validityPeriod.getNotValidBefore()));

        now = now.plusHours(8).plusMinutes(1);
        assertTrue(subject.isUpdateNeeded(now, kp -> getRevokedCertificates(kp, now)));
    }

    @Test
    public void shouldUpdateWhenNewEntryNeedsToBeAdded() {
        subject.update(validityPeriod, kp -> getRevokedCertificates(kp, validityPeriod.getNotValidBefore()));
        OutgoingResourceCertificate revokedCertificate = resourceCertificateRepository.findLatestOutgoingCertificate(keyPair.getPublicKey(), keyPair);
        revokedCertificate.revoke();

        assertTrue(subject.isUpdateNeeded(validityPeriod.getNotValidBefore(), kp -> getRevokedCertificates(kp, validityPeriod.getNotValidBefore())));
    }

    @Test
    public void shouldNotUpdateWhenRevokedCertificateExpires() {
        OutgoingResourceCertificate certificateToRevoke = resourceCertificateRepository.findLatestOutgoingCertificate(keyPair.getPublicKey(), keyPair);
        certificateToRevoke.revoke();

        now = certificateToRevoke.getNotValidAfter().minusHours(1);
        subject.update(new ValidityPeriod(now, now.plusHours(24)), kp -> getRevokedCertificates(kp, now));

        DateTimeUtils.setCurrentMillisFixed(certificateToRevoke.getNotValidAfter().plusHours(1).getMillis());
        assertFalse(subject.isUpdateNeeded(now, kp -> getRevokedCertificates(kp, now)));
    }

    private Collection<RevokedCertificateEntry> getRevokedCertificates(KeyPairEntity keyPair, DateTime now) {
        return Stream.concat(
            resourceCertificateRepository.findRevokedCertificatesWithValidityTimeAfterNowBySigningKeyPair(keyPair, now).stream(),
            bgpSecCertificateRepository.findRevokedCertificatesWithValidityTimeAfterNowBySigningKeyPair(keyPair, now).stream()
        ).toList();
    }
}
