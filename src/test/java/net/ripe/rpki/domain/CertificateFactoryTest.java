package net.ripe.rpki.domain;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.commons.crypto.util.BouncyCastleUtil;
import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509RouterCertificateParser;
import net.ripe.rpki.domain.bgpsec.BgpSecCertificateRepository;
import net.ripe.rpki.domain.inmemory.InMemoryResourceCertificateRepository;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import javax.security.auth.x500.X500Principal;
import java.net.URI;

import static net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest.SECOND_TEST_KEY_PAIR;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CertificateFactoryTest {

    public static final X509CertificateInformationAccessDescriptor[] SIA = {new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_SIGNED_OBJECT, URI.create("rsync://localhost"))};

    private ManagedCertificateAuthority ca;
    private KeyPairEntity currentKeyPair;
    private CertificateFactory subject;

    @Before
    public void setUp() {
        ca = TestObjects.createInitialisedProdCaWithRipeResources();
        currentKeyPair = ca.getCurrentKeyPair();
        var providerConfigurationData = mock(CertificationProviderConfigurationData.class);
        var bgpSecCertificateRepository = mock(BgpSecCertificateRepository.class);
        subject = new CertificateFactory(new InMemoryResourceCertificateRepository(), bgpSecCertificateRepository, providerConfigurationData);
    }

    @Test
    public void shouldIssueEECertificateWithAuthorityKeyIdentifier() {
        CertificateIssuanceRequest request = new CertificateIssuanceRequest(ResourceExtension.ofResources(ImmutableResourceSet.parse("10.0.0.0/8")), new X500Principal("CN=test"), SECOND_TEST_KEY_PAIR.getPublic(), SIA);

        OutgoingResourceCertificate endEntity = subject.issueAndPersistSingleUseEeResourceCertificate(request, TestObjects.TEST_VALIDITY_PERIOD, currentKeyPair);

        byte[] expectedAKI = BouncyCastleUtil.createAuthorityKeyIdentifier(currentKeyPair.getPublicKey()).getKeyIdentifierOctets();
        byte[] resultAKI = endEntity.getCertificate().getAuthorityKeyIdentifier();

        assertArrayEquals(expectedAKI, resultAKI);
    }

    @Test
    public void should_use_aia_of_signing_cert_for_ee_certificates() {
        CertificateIssuanceRequest request = new CertificateIssuanceRequest(ResourceExtension.ofResources(TestObjects.DEFAULT_PRODUCTION_CA_RESOURCES), new X500Principal("CN=test"), currentKeyPair.getPublicKey(), SIA);

        OutgoingResourceCertificate cert = subject.issueAndPersistSingleUseEeResourceCertificate(request,
                TestObjects.TEST_VALIDITY_PERIOD, currentKeyPair);

        X509CertificateInformationAccessDescriptor[] aia = cert.getAia();
        assertEquals(1, aia.length);
        assertEquals(currentKeyPair.getCurrentIncomingCertificate().getPublicationUri(), aia[0].getLocation());
    }

    @Test
    public void shouldConfigureCrlDistributionPointsForSingleUseEeCertificates() {
        CertificateIssuanceRequest request = new CertificateIssuanceRequest(ResourceExtension.ofResources(TestObjects.DEFAULT_PRODUCTION_CA_RESOURCES), new X500Principal("CN=test"), currentKeyPair.getPublicKey(), SIA);
        OutgoingResourceCertificate cert = subject.issueAndPersistSingleUseEeResourceCertificate(request, TestObjects.TEST_VALIDITY_PERIOD, currentKeyPair);

        assertArrayEquals(new URI[]{currentKeyPair.crlLocationUri()}, cert.getCrlDistributionPoints());
    }

    @Test
    public void shouldIssueEndEntityResourceCertificate() {
        CertificateIssuanceRequest request = new CertificateIssuanceRequest(ResourceExtension.ofResources(ImmutableResourceSet.parse("10.0.0.0/8")), new X500Principal("CN=test"), currentKeyPair.getPublicKey(), SIA);
        OutgoingResourceCertificate endEntity = subject.issueAndPersistSingleUseEeResourceCertificate(request, TestObjects.TEST_VALIDITY_PERIOD, currentKeyPair);
        assertTrue(endEntity.getCertificate().isEe());
        assertNull(endEntity.getPublishedObject());
        assertEquals(currentKeyPair.getCurrentIncomingCertificate().getSubject(), endEntity.getIssuer());
        assertEquals(ImmutableResourceSet.parse("10.0.0.0/8"), endEntity.getResources());
        assertNull(endEntity.getPublicationUri());
    }

    @Test
    public void issueAndPersistBgpSecCertificate() throws Exception {
        var resourceCertificateRepository = mock(ResourceCertificateRepository.class);
        var bgpSecCertificateRepository = mock(BgpSecCertificateRepository.class);
        var providerConfigurationData = mock(CertificationProviderConfigurationData.class);
        when(providerConfigurationData.getSignatureProvider()).thenReturn(KeyPairFactory.DEFAULT_RSA_KEYPAIR_GENERATOR_PROVIDER);

        var factory = new CertificateFactory(resourceCertificateRepository, bgpSecCertificateRepository, providerConfigurationData);

        var signingKeyPair = mock(KeyPairEntity.class);
        IncomingResourceCertificate currentCert = mock(IncomingResourceCertificate.class);
        when(signingKeyPair.getCurrentIncomingCertificate()).thenReturn(currentCert);
        when(signingKeyPair.getKeyPair()).thenReturn(KeyPairFactory.rsa().generate());
        when(signingKeyPair.crlLocationUri()).thenReturn(new URI("http://example.com/ca.crl"));

        when(currentCert.getPublicationUri()).thenReturn(new URI("http://example.com/ca.cer"));
        when(currentCert.getSubject()).thenReturn(new X500Principal("CN=parent"));
        final URI uri = URI.create("rsync://localhost");
        when(currentCert.getSia()).thenReturn(new X509CertificateInformationAccessDescriptor[]{
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY, uri),
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST, uri.resolve("manifest.mft"))
        });

        CertificateIssuanceRequest request = mock(CertificateIssuanceRequest.class);
        when(request.getSubjectDN()).thenReturn(new X500Principal("CN=example.com"));
        when(request.getSubjectPublicKey()).thenReturn(KeyPairFactory.bgpSec().generate().getPublic());

        var now = truncateToSecond(Instant.now());
        var future = truncateToSecond(now.plus(Duration.standardDays(30)));
        var r = factory.issueAndPersistBgpSecCertificate(
                request,
                Asn.parse("AS65000"),
                new ValidityPeriod(now, future),
                signingKeyPair
        );

        X509RouterCertificateParser parser = new X509RouterCertificateParser();
        parser.parse("test", r.getDerEncoded());
        var c = parser.getCertificate();
        Assertions.assertTrue(c.isRouter());
        Assertions.assertEquals(IpResourceSet.parse("AS65000"), c.getResources());
        Assertions.assertEquals(c.getValidityPeriod().getNotValidBefore(), now.toDateTime(DateTimeZone.UTC));
        Assertions.assertEquals(c.getValidityPeriod().getNotValidAfter(), future.toDateTime(DateTimeZone.UTC));
    }

    private static Instant truncateToSecond(Instant instant) {
        long millis = instant.getMillis();
        return instant.withMillis(millis - (millis % 1000));
    }

}
