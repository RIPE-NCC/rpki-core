package net.ripe.rpki.domain;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.net.URI;
import java.util.Arrays;

import static net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest.TEST_KEY_PAIR;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ResourceCertificateBuilderTest {

    private ResourceCertificateBuilder subject;


    @Before
    public void setUp() {
        subject = new ResourceCertificateBuilder();

        subject.withSubjectDN(new X500Principal("CN=zz.subject")).withIssuerDN(new X500Principal("CN=zz.issuer"));
        subject.withSerial(BigInteger.ONE);
        subject.withSubjectPublicKey(TEST_KEY_PAIR.getPublic());
        subject.withSigningKeyPair(TestObjects.TEST_KEY_PAIR_2);
        DateTime now = new DateTime(DateTimeZone.UTC);
        subject.withValidityPeriod(new ValidityPeriod(now, new DateTime(now.getYear()+1,1,1,0,0,0,0, DateTimeZone.UTC)));
        subject.withResources(ImmutableResourceSet.parse("10/8"));
        subject.withCrlDistributionPoints(URI.create("rsync://localhost/crl.crl"));

        subject.withCa(true).withEmbedded(false);
        subject.withoutAuthorityInformationAccess();
        subject.withFilename("test-certificate.cer");
        URI uri = URI.create("rsync://localhost");
        subject.withSubjectInformationAccess(
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY, uri),
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST, uri.resolve("manifest.mft")));
        subject.withParentPublicationDirectory(uri.resolve("/parent/"));
    }

    @Test
    public void shouldBuildResourceCertificate() {
        OutgoingResourceCertificate resourceCertificate = subject.build();

        assertNotNull(resourceCertificate);
    }

    @Test
    public void shouldIssueCaCertificateWithKeyUsageCertSignAndCrlCertSign() {
        X509ResourceCertificate resourceCertificate = subject.build().getCertificate();

        boolean[] keyUsage = resourceCertificate.getCertificate().getKeyUsage();
        // For KeyUsage flags order see bouncy castle KeyUsage class
        assertTrue(Arrays.equals(new boolean[] { false, false, false, false, false, true, true, false, false }, keyUsage));

    }

    @Test
    public void shouldIssueEeCertificateWithKeyUsageDigitalSignature() {
        URI uri = URI.create("rsync://localhost");
        subject.withCa(false).withEmbedded(true).
                withSubjectInformationAccess(
                        new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_SIGNED_OBJECT, uri)
                );

        X509ResourceCertificate resourceCertificate = subject.build().getCertificate();

        boolean[] keyUsage = resourceCertificate.getCertificate().getKeyUsage();
        // For KeyUsage flags order see bouncy castle KeyUsage class
        assertTrue(Arrays.equals(new boolean[] { true, false, false, false, false, false, false, false, false }, keyUsage));
    }
}
