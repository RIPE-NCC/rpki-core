package net.ripe.rpki.domain.manifest;

import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.util.PregeneratedKeyPairFactory;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementServiceImpl;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.KeyPair;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Transactional
public class ManifestEntityTest extends CertificationDomainTestCase {

    private static final Map<String, byte[]> INITIAL_ENTRIES = Collections.singletonMap("foo.crl", new byte[] { 1, 2, 3, 4 });

    private HostedCertificateAuthority ca;
    private ManifestEntity subject;

    private DateTime now;
    private IncomingResourceCertificate incomingCertificate;
    private KeyPairEntity currentKeyPair;

    @Before
    public void setUp() {
        clearDatabase();

        now = getUtcNowWithoutMillis();
        DateTimeUtils.setCurrentMillisFixed(now.getMillis());

        ca = createInitialisedProdCaWithRipeResources();
        currentKeyPair = ca.getCurrentKeyPair();
        subject = new ManifestEntity(currentKeyPair);
        incomingCertificate = currentKeyPair.getCurrentIncomingCertificate();

        KeyPair eeKeyPair = PregeneratedKeyPairFactory.getInstance().generate();
        CertificateIssuanceRequest request = subject.requestForManifestEeCertificate(eeKeyPair);
        ValidityPeriod validityPeriod = new ValidityPeriod(now, now.plus(CertificateManagementServiceImpl.TIME_TO_NEXT_UPDATE));
        OutgoingResourceCertificate eeCertificate = certificateManagementService.issueSingleUseEeResourceCertificate(ca, request, validityPeriod, currentKeyPair);

        subject.update(eeCertificate, eeKeyPair, INITIAL_ENTRIES);
    }

    private DateTime getUtcNowWithoutMillis() {
        return new DateTime(new DateTime().getMillis() / DateTimeConstants.MILLIS_PER_SECOND  * DateTimeConstants.MILLIS_PER_SECOND, DateTimeZone.UTC);
    }

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void shouldCreateEeCertificateOnUpdate() {
        OutgoingResourceCertificate certificate = subject.getCertificate();
        assertNotNull(certificate);
        assertTrue("is ee", certificate.getCertificate().isEe());
        assertTrue("resources inherited", certificate.getCertificate().isResourceSetInherited());
        assertTrue("resources", certificate.getResources().isEmpty());
    }

    @Test
    public void shouldRequireUpdateWhenNoCmsGeneratedYet() {
        subject = new ManifestEntity(currentKeyPair);

        assertNull(subject.getManifestCms());
        assertTrue(subject.isUpdateNeeded(now, INITIAL_ENTRIES, incomingCertificate));
    }

    @Test
    public void shouldRequireUpdateWhenCloseToNextUpdateTime() {
        DateTime now = this.now.plusHours(8).plusMinutes(1);

        assertTrue(subject.isUpdateNeeded(now, INITIAL_ENTRIES, incomingCertificate));
    }

    @Test
    public void shouldNotUpdateTooSoon() {
        DateTime now = this.now.plusHours(8);

        assertFalse(subject.isUpdateNeeded(now, INITIAL_ENTRIES, incomingCertificate));
    }

    @Test
    public void shouldUseNowToPlus24HoursForUpdateTimes() {
        assertEquals(now, subject.getManifestCms().getThisUpdateTime());
        assertEquals(now.plusHours(24), subject.getManifestCms().getNextUpdateTime());
    }

    @Test
    public void shouldMatchEeCertificateValidityPeriod() {
        ManifestCms manifestCms = subject.getManifestCms();
        ValidityPeriod certificateValidityPeriod = manifestCms.getCertificate().getValidityPeriod();
        assertEquals(certificateValidityPeriod.getNotValidBefore(), manifestCms.getThisUpdateTime());
        assertEquals(certificateValidityPeriod.getNotValidAfter(), manifestCms.getNextUpdateTime());
    }

    @Test
    public void shouldRequireUpdateWhenIncomingResourceCertificatePublicationUriChanges() {
        ca.processCertificateIssuanceResponse(
            new CertificateIssuanceResponse(
                ca.getCurrentIncomingCertificate().getCertificate(),
                URI.create("rsync://updated/location.cer")
            ),
            resourceCertificateRepository
        );

        assertTrue(subject.isUpdateNeeded(now, INITIAL_ENTRIES, ca.getCurrentKeyPair().getCurrentIncomingCertificate()));
    }

    @Test
    public void shouldRequireUpdateWhenManifestEntriesChange() {
        assertFalse("no update when entries are the same", subject.isUpdateNeeded(now, INITIAL_ENTRIES, incomingCertificate));
        assertTrue("update required when entries change",
                subject.isUpdateNeeded(now, Collections.singletonMap("foo.roa", new byte[0]), incomingCertificate));
    }
}
