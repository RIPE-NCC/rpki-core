package net.ripe.rpki.domain.manifest;

import io.micrometer.core.instrument.DistributionSummary;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.commons.crypto.util.PregeneratedKeyPairFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.crl.CrlEntity;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.roa.*;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.util.Collections;
import java.util.List;

import static net.ripe.rpki.domain.manifest.ManifestPublicationService.RPKI_CA_GENERATED_CRL_SIZE_METRIC_NAME;
import static net.ripe.rpki.domain.manifest.ManifestPublicationService.RPKI_CA_GENERATED_MANIFEST_SIZE_METRIC_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Transactional
@Rollback
public class ManifestPublicationServiceTest extends CertificationDomainTestCase {

    private DateTime now;
    private ManagedCertificateAuthority ca;

    private KeyPairEntity currentKeyPair;
    private ManifestPublicationService subject;

    @Autowired
    private RoaConfigurationRepository roaConfigurationRepository;
    @Autowired
    private RoaEntityRepository roaEntityRepository;
    @Autowired
    protected RoaEntityService roaEntityService;

    @Before
    public void setUp() {
        clearDatabase();

        now = DateTime.now(DateTimeZone.UTC);

        ca = createInitialisedProdCaWithRipeResources();
        currentKeyPair = ca.getCurrentKeyPair();

        subject = manifestPublicationService;
    }

    @Test
    public void should_create_initial_manifest_and_crl_on_first_publish() {
        long count = subject.updateManifestAndCrlIfNeeded(ca);

        assertThat(count).describedAs("updated key pairs").isEqualTo(1);

        CrlEntity crlEntity = crlEntityRepository.findByKeyPair(currentKeyPair);
        assertNotNull(crlEntity);
        assertNotNull(crlEntity.getPublishedObject());

        ManifestEntity manifestEntity = manifestEntityRepository.findByKeyPairEntity(currentKeyPair);
        assertNotNull(manifestEntity);

        X509Crl crl = crlEntity.getCrl();
        ManifestCms manifest = manifestEntity.getManifestCms();

        assertEquals("crl next update time matches manifest", crl.getNextUpdateTime(), manifest.getNextUpdateTime());
        assertEquals("manifest next update time matches certificate not valid after", manifest.getNextUpdateTime(), manifest.getCertificate().getValidityPeriod().getNotValidAfter());

        assertEquals("crl is empty", 0, crl.getRevokedCertificates().size());
        assertTrue("manifest contains crl", manifest.verifyFileContents("TEST-KEY.crl", crl.getEncoded()));

        assertThat(meterRegistry.find(RPKI_CA_GENERATED_MANIFEST_SIZE_METRIC_NAME).meter()).isInstanceOfSatisfying(DistributionSummary.class, summary -> {
            assertThat(summary.count()).isEqualTo(1);
            assertThat(summary.max()).isEqualTo(manifest.getEncoded().length);
        });
        assertThat(meterRegistry.find(RPKI_CA_GENERATED_CRL_SIZE_METRIC_NAME).meter()).isInstanceOfSatisfying(DistributionSummary.class, summary -> {
            assertThat(summary.count()).isEqualTo(1);
            assertThat(summary.max()).isEqualTo(crl.getEncoded().length);
        });

        assertThat(subject.updateManifestAndCrlIfNeeded(ca)).describedAs("no update needed").isZero();
    }

    @Test
    public void should_update_both_manifest_and_crl_when_crl_needs_update() {
        subject.updateManifestAndCrlIfNeeded(ca);

        X509Crl originalCrl = crlEntityRepository.findByKeyPair(currentKeyPair).getCrl();
        assertEquals("original crl is empty", 0, originalCrl.getRevokedCertificates().size());

        ManifestCms originalManifest = manifestEntityRepository.findByKeyPairEntity(currentKeyPair).getManifestCms();
        assertTrue("original manifest contains original crl", originalManifest.verifyFileContents("TEST-KEY.crl", originalCrl.getEncoded()));

        // Hack to work around idx_uniq_published_object_filename_to_be_published, since flush order of JPA is not defined
        publishedObjectRepository.findAll().forEach(po -> po.published());
        entityManager.flush();

        URI uri = URI.create("rsync://localhost");
        CertificateIssuanceRequest request = new CertificateIssuanceRequest(ResourceExtension.allInherited(), new X500Principal("CN=test"), PregeneratedKeyPairFactory.getInstance().generate().getPublic(),  new X509CertificateInformationAccessDescriptor[]{new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_SIGNED_OBJECT, uri)});
        OutgoingResourceCertificate outgoingResourceCertificate = singleUseEeCertificateFactory.issueSingleUseEeResourceCertificate(request, new ValidityPeriod(now, now.plusHours(10)), currentKeyPair);
        outgoingResourceCertificate.revoke();

        subject.updateManifestAndCrlIfNeeded(ca);

        X509Crl updatedCrl = crlEntityRepository.findByKeyPair(currentKeyPair).getCrl();
        ManifestCms updatedManifest = manifestEntityRepository.findByKeyPairEntity(currentKeyPair).getManifestCms();

        assertTrue("crl updated", originalCrl.getNumber().compareTo(updatedCrl.getNumber()) < 0);
        assertTrue("updated crl contains revoked certificate", updatedCrl.isRevoked(outgoingResourceCertificate.getCertificate().getCertificate()));
        assertTrue("updated crl contains original manifest certificate", updatedCrl.isRevoked(originalManifest.getCertificate().getCertificate()));
        assertEquals("updated crl contains no other entries",2, updatedCrl.getRevokedCertificates().size());

        assertTrue("manifest updated", originalManifest.getNumber().compareTo(updatedManifest.getNumber()) < 0);
        assertFalse("updated manifest does not contain original crl", updatedManifest.verifyFileContents("TEST-KEY.crl", originalCrl.getEncoded()));
        assertTrue("updated manifest contains updated crl", updatedManifest.verifyFileContents("TEST-KEY.crl", updatedCrl.getEncoded()));

        assertEquals("updated crl next update time matches updated manifest", updatedCrl.getNextUpdateTime(), updatedManifest.getNextUpdateTime());
        assertEquals("updated manifest next update time matches certificate not valid after", updatedManifest.getNextUpdateTime(), updatedManifest.getCertificate().getValidityPeriod().getNotValidAfter());
    }

    @Test
    public void should_update_both_manifest_and_crl_when_manifest_needs_update() {
        subject.updateManifestAndCrlIfNeeded(ca);

        X509Crl originalCrl = crlEntityRepository.findByKeyPair(currentKeyPair).getCrl();
        assertEquals("original crl is empty", 0, originalCrl.getRevokedCertificates().size());

        ManifestCms originalManifest = manifestEntityRepository.findByKeyPairEntity(currentKeyPair).getManifestCms();
        assertTrue("original manifest contains original crl", originalManifest.verifyFileContents("TEST-KEY.crl", originalCrl.getEncoded()));

        // Hack to work around idx_uniq_published_object_filename_to_be_published, since flush order of JPA is not defined
        publishedObjectRepository.findAll().forEach(po -> po.published());
        entityManager.flush();

        roaConfigurationRepository.getOrCreateByCertificateAuthority(ca).addPrefix(Collections.singleton(new RoaConfigurationPrefix(Asn.parse("AS3333"), IpRange.parse("10.0.0.0/8"))));
        roaEntityService.updateRoasIfNeeded(ca);

        subject.updateManifestAndCrlIfNeeded(ca);

        List<RoaEntity> roas = roaEntityRepository.findCurrentByCertificateAuthority(ca);
        assertEquals("single roa issued", 1, roas.size());
        RoaEntity roaEntity = roas.get(0);

        X509Crl updatedCrl = crlEntityRepository.findByKeyPair(currentKeyPair).getCrl();
        ManifestCms updatedManifest = manifestEntityRepository.findByKeyPairEntity(currentKeyPair).getManifestCms();

        assertTrue("crl updated", originalCrl.getNumber().compareTo(updatedCrl.getNumber()) < 0);
        assertTrue("updated crl contains original manifest certificate", updatedCrl.isRevoked(originalManifest.getCertificate().getCertificate()));
        assertEquals("updated crl contains no other entries",1, updatedCrl.getRevokedCertificates().size());

        assertTrue("manifest updated", originalManifest.getNumber().compareTo(updatedManifest.getNumber()) < 0);
        assertFalse("updated manifest does not contain original crl", updatedManifest.verifyFileContents("TEST-KEY.crl", originalCrl.getEncoded()));
        assertTrue("updated manifest contains updated crl", updatedManifest.verifyFileContents("TEST-KEY.crl", updatedCrl.getEncoded()));
        assertTrue("updated manifest contains roa", updatedManifest.verifyFileContents(roaEntity.getPublishedObject().getFilename(), roaEntity.getRoaCms().getEncoded()));

        assertEquals("updated crl next update time matches updated manifest", updatedCrl.getNextUpdateTime(), updatedManifest.getNextUpdateTime());
        assertEquals("updated manifest next update time matches certificate not valid after", updatedManifest.getNextUpdateTime(), updatedManifest.getCertificate().getValidityPeriod().getNotValidAfter());
    }
}
