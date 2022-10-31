package net.ripe.rpki.domain;

import net.ripe.rpki.commons.crypto.util.BouncyCastleUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class CertificateAuthorityTest {

    private ManagedCertificateAuthority subject;
    private KeyPairEntity keyPair;

    @Before
    public void setUp() {
        subject = TestObjects.createInitialisedProdCaWithRipeResources();
        keyPair = subject.getCurrentKeyPair();
    }

    @Test
    public void shouldGetUuidWithNewCertificateAuthority() {
        UUID uuid = subject.getUuid();
        assertNotNull(uuid);
    }

    @Test
    public void shouldIssueCertificateWithSubjectKeyIdentifier() {
        IncomingResourceCertificate cert = subject.getCurrentIncomingCertificate();

        byte[] expectedSKI = BouncyCastleUtil.createSubjectKeyIdentifier(keyPair.getPublicKey()).getKeyIdentifier();
        byte[] resultSKI = cert.getCertificate().getSubjectKeyIdentifier();
        assertArrayEquals(expectedSKI, resultSKI);
    }

}
