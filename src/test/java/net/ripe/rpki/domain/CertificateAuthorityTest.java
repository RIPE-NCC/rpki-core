package net.ripe.rpki.domain;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.util.BouncyCastleUtil;
import net.ripe.rpki.commons.crypto.util.PregeneratedKeyPairFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementService;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementServiceImpl;
import net.ripe.rpki.util.MemoryDBComponent;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.net.URI;
import java.util.UUID;

import static net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest.SECOND_TEST_KEY_PAIR;
import static net.ripe.rpki.domain.CertificationDomainTestCase.DEFAULT_PRODUCTION_CA_RESOURCES;
import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class CertificateAuthorityTest {

    public static final X509CertificateInformationAccessDescriptor[] SIA = {new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_SIGNED_OBJECT, URI.create("rsync://localhost"))};
    private CertificateManagementService certificateManagementService;
    @Mock private ResourceCertificateRepository resourceCertificateRepository;
    @Mock private PublishedObjectRepository publishedObjectRepository;

    private HostedCertificateAuthority subject;
    private KeyPairEntity keyPair;

    @Before
    public void setUp() {
        certificateManagementService = new CertificateManagementServiceImpl(resourceCertificateRepository, publishedObjectRepository, new MemoryDBComponent(), null, null, PregeneratedKeyPairFactory.getInstance(), new SimpleMeterRegistry());
        subject = CertificationDomainTestCase.createInitialisedProdCaWithRipeResources(certificateManagementService);
        keyPair = subject.getCurrentKeyPair();
    }

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void shouldGetUuidWithNewCertificateAuthority() {
        UUID uuid = subject.getUuid();
        assertNotNull(uuid);
    }

    @Test
    public void shouldIncrementAndTrackLastIssuedSerial() {
        assertEquals(BigInteger.ONE, subject.getLastIssuedSerial());

        CertificateIssuanceRequest request = new CertificateIssuanceRequest(IpResourceSet.parse("10.0.0.0/8"), new X500Principal("CN=test"), keyPair.getPublicKey(), SIA);
        OutgoingResourceCertificate ee = certificateManagementService.issueSingleUseEeResourceCertificate(subject,
                request, TestObjects.TEST_VALIDITY_PERIOD, subject.getCurrentKeyPair());

        assertEquals(BigInteger.valueOf(2), ee.getSerial());
        assertEquals(ee.getSerial(), subject.getLastIssuedSerial());
    }

    @Test
    public void shouldIssueCertificateWithSubjectKeyIdentifier() {
        IncomingResourceCertificate cert = subject.getCurrentIncomingCertificate();

        byte[] expectedSKI = BouncyCastleUtil.createSubjectKeyIdentifier(keyPair.getPublicKey()).getKeyIdentifier();
        byte[] resultSKI = cert.getCertificate().getSubjectKeyIdentifier();
        assertArrayEquals(expectedSKI, resultSKI);
    }

    @Test
    public void shouldIssueEECertificateWithAuthorityKeyIdentifier() {
        CertificateIssuanceRequest request = new CertificateIssuanceRequest(IpResourceSet.parse("10.0.0.0/8"), new X500Principal("CN=test"), SECOND_TEST_KEY_PAIR.getPublic(), SIA);

        OutgoingResourceCertificate endEntity = TestServices.createCertificateManagementService(resourceCertificateRepository, publishedObjectRepository).issueSingleUseEeResourceCertificate(subject, request, TestObjects.TEST_VALIDITY_PERIOD, subject.getCurrentKeyPair());

        byte[] expectedAKI = BouncyCastleUtil.createAuthorityKeyIdentifier(subject.getCurrentKeyPair().getPublicKey()).getKeyIdentifier();
        byte[] resultAKI = endEntity.getCertificate().getAuthorityKeyIdentifier();

        assertArrayEquals(expectedAKI, resultAKI);
    }

    @Test
    public void should_use_aia_of_signing_cert_for_ee_certificates() {
        CertificateIssuanceRequest request = new CertificateIssuanceRequest(DEFAULT_PRODUCTION_CA_RESOURCES, new X500Principal("CN=test"), keyPair.getPublicKey(), SIA);

        OutgoingResourceCertificate cert = TestServices.createCertificateManagementService(resourceCertificateRepository, publishedObjectRepository).issueSingleUseEeResourceCertificate(subject, request,
                TestObjects.TEST_VALIDITY_PERIOD, keyPair);

        X509CertificateInformationAccessDescriptor[] aia = cert.getAia();
        assertEquals(1, aia.length);
        assertEquals(keyPair.getCurrentIncomingCertificate().getPublicationUri(), aia[0].getLocation());
    }

    @Test
    public void shouldConfigureCrlDistributionPointsForSingleUseEeCertificates() {
        CertificateIssuanceRequest request = new CertificateIssuanceRequest(DEFAULT_PRODUCTION_CA_RESOURCES, new X500Principal("CN=test"), keyPair.getPublicKey(), SIA);
        OutgoingResourceCertificate cert = TestServices.createCertificateManagementService(resourceCertificateRepository, publishedObjectRepository).issueSingleUseEeResourceCertificate(subject, request, TestObjects.TEST_VALIDITY_PERIOD, keyPair);

        assertArrayEquals(new URI[]{keyPair.crlLocationUri()}, cert.getCrlDistributionPoints());
    }

    @Test
    public void shouldIssueEndEntityResourceCertificate() {
        CertificateIssuanceRequest request = new CertificateIssuanceRequest(IpResourceSet.parse("10.0.0.0/8"), new X500Principal("CN=test"), keyPair.getPublicKey(), SIA);
        OutgoingResourceCertificate endEntity = TestServices.createCertificateManagementService(resourceCertificateRepository, publishedObjectRepository).issueSingleUseEeResourceCertificate(subject, request, TestObjects.TEST_VALIDITY_PERIOD, subject.getCurrentKeyPair());
        assertTrue(endEntity.getCertificate().isEe());
        assertNull(endEntity.getPublishedObject());
        assertEquals(subject.getCurrentIncomingCertificate().getSubject(), endEntity.getIssuer());
        assertEquals(IpResourceSet.parse("10.0.0.0/8"), endEntity.getResources());
        assertNull(endEntity.getPublicationUri());
        assertEquals(subject.getLastIssuedSerial(), endEntity.getSerial());
    }

}
