package net.ripe.rpki.domain.manifest;

import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.util.PregeneratedKeyPairFactory;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.PublishedObject;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Transactional
public class ManifestEntityTest extends CertificationDomainTestCase {

    private static final URI PUBLICATION_DIRECTORY = URI.create("rsync://example.com/rpki");

    private ManagedCertificateAuthority ca;
    private ManifestEntity subject;
    private DateTime now;
    private IncomingResourceCertificate incomingCertificate;
    private KeyPairEntity currentKeyPair;
    private Collection<PublishedObject> initialEntries;
    private KeyPair eeKeyPair;
    private OutgoingResourceCertificate eeCertificate;
    private PublishedObject publishedObject1;
    private PublishedObject publishedObject2;

    @Before
    public void setUp() {
        clearDatabase();

        now = getUtcNowWithoutMillis();
        DateTimeUtils.setCurrentMillisFixed(now.getMillis());

        ca = createInitialisedProdCaWithRipeResources();
        currentKeyPair = ca.getCurrentKeyPair();
        subject = new ManifestEntity(currentKeyPair);
        incomingCertificate = currentKeyPair.getCurrentIncomingCertificate();
        publishedObject1 = new PublishedObject(currentKeyPair, "foo.crl", new byte[]{1, 2, 3, 4}, true, PUBLICATION_DIRECTORY, new ValidityPeriod());
        publishedObject2 = new PublishedObject(currentKeyPair, "foo.roa", new byte[]{5, 6, 7, 8}, true, PUBLICATION_DIRECTORY, new ValidityPeriod());
        initialEntries = Collections.singleton(publishedObject1);

        eeKeyPair = PregeneratedKeyPairFactory.getInstance().generate();
        CertificateIssuanceRequest request = subject.requestForManifestEeCertificate(eeKeyPair);
        ValidityPeriod validityPeriod = new ValidityPeriod(now, now.plus(CertificateManagementServiceImpl.TIME_TO_NEXT_UPDATE));
        eeCertificate = certificateManagementService.issueSingleUseEeResourceCertificate(ca, request, validityPeriod, currentKeyPair);

        subject.update(eeCertificate, eeKeyPair, "SunRsaSign", initialEntries);
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
        assertTrue(subject.isUpdateNeeded(now, initialEntries, incomingCertificate));
    }

    @Test
    public void shouldRequireUpdateWhenCloseToNextUpdateTime() {
        DateTime now = this.now.plusHours(8).plusMinutes(1);

        assertTrue(subject.isUpdateNeeded(now, initialEntries, incomingCertificate));
    }

    @Test
    public void shouldNotUpdateTooSoon() {
        DateTime now = this.now.plusHours(8);

        assertFalse(subject.isUpdateNeeded(now, initialEntries, incomingCertificate));
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

        assertTrue(subject.isUpdateNeeded(now, initialEntries, ca.getCurrentKeyPair().getCurrentIncomingCertificate()));
    }

    @Test
    public void shouldRequireUpdateWhenManifestEntriesChange() {
        assertFalse("no update when entries are the same", subject.isUpdateNeeded(now, initialEntries, incomingCertificate));
        assertTrue("update required when entries change",
                subject.isUpdateNeeded(now, Collections.singleton(publishedObject2), incomingCertificate));
    }

    @Test
    public void shouldRemoveReferenceFromPublishedObjectWhenRemoved() {
        assertThat(publishedObject1.getContainingManifest()).isEqualTo(subject);
        subject.update(eeCertificate, eeKeyPair, "SunRsaSign", Collections.emptyList());
        assertThat(publishedObject1.getContainingManifest()).isNull();
    }

    @Test
    public void shouldAddReferenceFromPublishedObjectWhenAdded() {
        assertThat(publishedObject2.getContainingManifest()).isNull();
        subject.update(eeCertificate, eeKeyPair, "SunRsaSign", Arrays.asList(publishedObject1, publishedObject2));
        assertThat(publishedObject1.getContainingManifest()).isEqualTo(subject);
        assertThat(publishedObject2.getContainingManifest()).isEqualTo(subject);
    }
}
