package net.ripe.rpki.domain.naming;

import net.ripe.ipresource.Asn;
import net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509RouterCertificate;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.security.PublicKey;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class UuidRepositoryObjectNamingStrategyTest {

    @Test
    public void shouldUseHexEncodedSubjectKeyIdentifierForCertificateFileName() {

        UuidRepositoryObjectNamingStrategy subject = new UuidRepositoryObjectNamingStrategy();
        PublicKey publicKey = KeyPairFactoryTest.TEST_KEY_PAIR.getPublic();

        X500Principal expected = new X500Principal("CN=" + KeyPairUtil.getAsciiHexEncodedPublicKeyHash(publicKey));

        assertEquals(expected, subject.caCertificateSubject(publicKey));
    }

    @Test
    public void shouldUseBase64EncodedEeCertificateSubjectPublicKeyIdentifierForRoaFileName() {

        UuidRepositoryObjectNamingStrategy subject = new UuidRepositoryObjectNamingStrategy();

        PublicKey publicKey = KeyPairFactoryTest.TEST_KEY_PAIR.getPublic();
        OutgoingResourceCertificate eeCertificate = mock(OutgoingResourceCertificate.class);
        when(eeCertificate.getSubjectPublicKey()).thenReturn(publicKey);

        String expected = KeyPairUtil.getEncodedKeyIdentifier(publicKey) + ".roa";
        if (expected.startsWith("-")) expected = "1" + expected;

        assertEquals(expected, subject.roaFileName(eeCertificate));
    }

    private final UuidRepositoryObjectNamingStrategy namingStrategy = new UuidRepositoryObjectNamingStrategy();
    private final X509ResourceCertificate caCertificate = caCertificate(KeyPairFactoryTest.TEST_KEY_PAIR.getPublic());
    private final X509RouterCertificate bgpSecCertificate = bgpSecCertificate(KeyPairFactoryTest.SECOND_TEST_KEY_PAIR.getPublic());

    @Test
    public void shouldCreateBgpSecFilenameAsBsPrefixedUrlSafeBase64Sha256Hash() {
        String filename = namingStrategy.bgpSecFilename(caCertificate, bgpSecCertificate, Asn.parse("AS42"), 43L);
        assertTrue(filename, filename.matches("bs-[A-Za-z0-9_-]{43}\\.cer"));
    }

    @Test
    public void shouldCreateDifferentBgpSecFilenameWhenAnyInputDiffers() {
        String filename = namingStrategy.bgpSecFilename(caCertificate, bgpSecCertificate, Asn.parse("AS42"), 43L);

        assertNotEquals(filename, namingStrategy.bgpSecFilename(
            caCertificate(KeyPairFactoryTest.SECOND_TEST_KEY_PAIR.getPublic()), bgpSecCertificate, Asn.parse("AS42"), 43L));
        assertNotEquals(filename, namingStrategy.bgpSecFilename(
            caCertificate, bgpSecCertificate(KeyPairFactoryTest.TEST_KEY_PAIR.getPublic()), Asn.parse("AS42"), 43L));
        assertNotEquals(filename, namingStrategy.bgpSecFilename(caCertificate, bgpSecCertificate, Asn.parse("AS43"), 43L));
        assertNotEquals(filename, namingStrategy.bgpSecFilename(caCertificate, bgpSecCertificate, Asn.parse("AS42"), null));
        assertNotEquals(filename, namingStrategy.bgpSecFilename(caCertificate, bgpSecCertificate, null, 43L));
        assertNotEquals(filename, namingStrategy.bgpSecFilename(caCertificate, bgpSecCertificate, Asn.parse("AS42"), 44L));
    }

    private static X509ResourceCertificate caCertificate(PublicKey publicKey) {
        X509ResourceCertificate certificate = mock(X509ResourceCertificate.class);
        when(certificate.getPublicKey()).thenReturn(publicKey);
        return certificate;
    }

    private static X509RouterCertificate bgpSecCertificate(PublicKey publicKey) {
        X509RouterCertificate certificate = mock(X509RouterCertificate.class);
        when(certificate.getPublicKey()).thenReturn(publicKey);
        return certificate;
    }

}

