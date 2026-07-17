package net.ripe.rpki.domain.naming;

import net.ripe.ipresource.Asn;
import net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
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

    @Test
    public void shouldCreateBgpSecFilenameWithHexEncodedAsnAndRouterId() {
        UuidRepositoryObjectNamingStrategy subject = new UuidRepositoryObjectNamingStrategy();
        PublicKey publicKey = KeyPairFactoryTest.TEST_KEY_PAIR.getPublic();
        X509ResourceCertificate certificate = mock(X509ResourceCertificate.class);
        when(certificate.getPublicKey()).thenReturn(publicKey);

        String expected = UuidRepositoryObjectNamingStrategy.getDashSafeEncodedPublicKeyHash(publicKey)
            + "-bgpsec-0000002a-0000002b.cer";

        assertEquals(expected, subject.bgpSecFilename(certificate, Asn.parse("AS42"), 43L));
    }

    @Test
    public void shouldCreateBgpSecFilenameWithoutRouterIdSuffixWhenRouterIdIsNull() {
        UuidRepositoryObjectNamingStrategy subject = new UuidRepositoryObjectNamingStrategy();
        PublicKey publicKey = KeyPairFactoryTest.TEST_KEY_PAIR.getPublic();
        X509ResourceCertificate certificate = mock(X509ResourceCertificate.class);
        when(certificate.getPublicKey()).thenReturn(publicKey);

        String expected = UuidRepositoryObjectNamingStrategy.getDashSafeEncodedPublicKeyHash(publicKey)
            + "-bgpsec-0000002a.cer";

        assertEquals(expected, subject.bgpSecFilename(certificate, Asn.parse("AS42"), null));
    }

}

