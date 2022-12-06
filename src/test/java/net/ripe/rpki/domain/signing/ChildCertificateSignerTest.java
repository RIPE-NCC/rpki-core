package net.ripe.rpki.domain.signing;

import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.util.BouncyCastleUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.net.URI;

import static net.ripe.ipresource.ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ChildCertificateSignerTest {

    private static final KeyPairEntity SIGNING_KEY_PAIR = TestObjects.createActiveKeyPair( "signingKeyPair");

    private static final URI CHILD_PUBLICATION_URI = URI.create("rsync://localhost/repository/RIPE/child/");
    private static final URI MANIFEST_URI = CHILD_PUBLICATION_URI.resolve("manifest.mft");
    private static final X509CertificateInformationAccessDescriptor[] SIA = {
            new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST, MANIFEST_URI),
            new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY, CHILD_PUBLICATION_URI)
    };
    private static final KeyPairEntity SUBJECT_KEY_PAIR = TestObjects.createTestKeyPair("subjectKeyPair");

    private static final X509CertificateInformationAccessDescriptor[] EXPECTED_CHILD_CERT_AIA = {
            new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_CA_CA_ISSUERS, SIGNING_KEY_PAIR.getCurrentIncomingCertificate().getPublicationUri()),
    };

    public static final CertificateIssuanceRequest TEST_REQUEST = new CertificateIssuanceRequest(
            ALL_PRIVATE_USE_RESOURCES,
            new X500Principal("cn=nl.bluelight"),
            SUBJECT_KEY_PAIR.getPublicKey(),
            SIA);

    private ChildCertificateSigner subject = new ChildCertificateSigner();
    private DateTime now = new DateTime(DateTimeZone.UTC);
    private ValidityPeriod validityPeriod = new ValidityPeriod(now, now.plusDays(180));

    @Test
    public void shouldIssueChildCaCertificateWithCorrectCrlAndAiaPointers() {
        OutgoingResourceCertificate resourceCertificate = subject.buildOutgoingResourceCertificate(TEST_REQUEST, validityPeriod, SIGNING_KEY_PAIR, BigInteger.ONE);

        assertEquals(SIGNING_KEY_PAIR.crlLocationUri(), resourceCertificate.getCertificate().getCrlUri());
        assertArrayEquals(EXPECTED_CHILD_CERT_AIA, resourceCertificate.getAia());
    }

    @Test
    public void shouldIssueChildCaCertificateWithSiaFromRequest() {
        OutgoingResourceCertificate resourceCertificate = subject.buildOutgoingResourceCertificate(TEST_REQUEST, validityPeriod, SIGNING_KEY_PAIR, BigInteger.ONE);
        assertArrayEquals(SIA, resourceCertificate.getSia());
    }

    @Test
    public void shouldIssueChildCaCertificateWithCorrectAKI() {
        OutgoingResourceCertificate resourceCertificate = subject.buildOutgoingResourceCertificate(TEST_REQUEST, validityPeriod, SIGNING_KEY_PAIR, BigInteger.ONE);

        AuthorityKeyIdentifier expectedAki = BouncyCastleUtil.createAuthorityKeyIdentifier(SIGNING_KEY_PAIR.getPublicKey());
        assertArrayEquals(expectedAki.getKeyIdentifier(), resourceCertificate.getCertificate().getAuthorityKeyIdentifier());
    }

    @Test
    public void shouldIssueChildCaCertificateWithSpecifiedValidityPeriod() {
        OutgoingResourceCertificate resourceCertificate = subject.buildOutgoingResourceCertificate(TEST_REQUEST, validityPeriod, SIGNING_KEY_PAIR, BigInteger.ONE);
        assertEquals(validityPeriod, resourceCertificate.getValidityPeriod());
    }

    @Test
    public void shouldIssueChildCaCertificateWithOurIssuerSubject() {
        OutgoingResourceCertificate resourceCertificate = subject.buildOutgoingResourceCertificate(TEST_REQUEST, validityPeriod, SIGNING_KEY_PAIR, BigInteger.ONE);
        assertEquals(SIGNING_KEY_PAIR.getCurrentIncomingCertificate().getSubject(), resourceCertificate.getIssuer());
    }

}
