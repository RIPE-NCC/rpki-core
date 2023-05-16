package net.ripe.rpki.domain;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.commons.crypto.util.BouncyCastleUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.domain.inmemory.InMemoryResourceCertificateRepository;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.net.URI;

import static net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest.SECOND_TEST_KEY_PAIR;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SingleUseEeCertificateFactoryTest {

    public static final X509CertificateInformationAccessDescriptor[] SIA = {new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_SIGNED_OBJECT, URI.create("rsync://localhost"))};

    private ManagedCertificateAuthority ca;
    private KeyPairEntity currentKeyPair;
    private SingleUseEeCertificateFactory subject;

    @Before
    public void setUp() {
        ca = TestObjects.createInitialisedProdCaWithRipeResources();
        currentKeyPair = ca.getCurrentKeyPair();

        subject = new SingleUseEeCertificateFactory(new InMemoryResourceCertificateRepository());
    }

    @Test
    public void shouldIssueEECertificateWithAuthorityKeyIdentifier() {
        CertificateIssuanceRequest request = new CertificateIssuanceRequest(ResourceExtension.ofResources(ImmutableResourceSet.parse("10.0.0.0/8")), new X500Principal("CN=test"), SECOND_TEST_KEY_PAIR.getPublic(), SIA);

        OutgoingResourceCertificate endEntity = subject.issueSingleUseEeResourceCertificate(request, TestObjects.TEST_VALIDITY_PERIOD, currentKeyPair);

        byte[] expectedAKI = BouncyCastleUtil.createAuthorityKeyIdentifier(currentKeyPair.getPublicKey()).getKeyIdentifier();
        byte[] resultAKI = endEntity.getCertificate().getAuthorityKeyIdentifier();

        assertArrayEquals(expectedAKI, resultAKI);
    }

    @Test
    public void should_use_aia_of_signing_cert_for_ee_certificates() {
        CertificateIssuanceRequest request = new CertificateIssuanceRequest(ResourceExtension.ofResources(TestObjects.DEFAULT_PRODUCTION_CA_RESOURCES), new X500Principal("CN=test"), currentKeyPair.getPublicKey(), SIA);

        OutgoingResourceCertificate cert = subject.issueSingleUseEeResourceCertificate(request,
            TestObjects.TEST_VALIDITY_PERIOD, currentKeyPair);

        X509CertificateInformationAccessDescriptor[] aia = cert.getAia();
        assertEquals(1, aia.length);
        assertEquals(currentKeyPair.getCurrentIncomingCertificate().getPublicationUri(), aia[0].getLocation());
    }

    @Test
    public void shouldConfigureCrlDistributionPointsForSingleUseEeCertificates() {
        CertificateIssuanceRequest request = new CertificateIssuanceRequest(ResourceExtension.ofResources(TestObjects.DEFAULT_PRODUCTION_CA_RESOURCES), new X500Principal("CN=test"), currentKeyPair.getPublicKey(), SIA);
        OutgoingResourceCertificate cert = subject.issueSingleUseEeResourceCertificate(request, TestObjects.TEST_VALIDITY_PERIOD, currentKeyPair);

        assertArrayEquals(new URI[]{currentKeyPair.crlLocationUri()}, cert.getCrlDistributionPoints());
    }

    @Test
    public void shouldIssueEndEntityResourceCertificate() {
        CertificateIssuanceRequest request = new CertificateIssuanceRequest(ResourceExtension.ofResources(ImmutableResourceSet.parse("10.0.0.0/8")), new X500Principal("CN=test"), currentKeyPair.getPublicKey(), SIA);
        OutgoingResourceCertificate endEntity = subject.issueSingleUseEeResourceCertificate(request, TestObjects.TEST_VALIDITY_PERIOD, currentKeyPair);
        assertTrue(endEntity.getCertificate().isEe());
        assertNull(endEntity.getPublishedObject());
        assertEquals(currentKeyPair.getCurrentIncomingCertificate().getSubject(), endEntity.getIssuer());
        assertEquals(ImmutableResourceSet.parse("10.0.0.0/8"), endEntity.getResources());
        assertNull(endEntity.getPublicationUri());
    }

}
