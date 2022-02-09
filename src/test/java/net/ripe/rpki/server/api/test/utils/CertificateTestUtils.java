package net.ripe.rpki.server.api.test.utils;

import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateTest;

public class CertificateTestUtils {

    public static X509ResourceCertificate createX509ResourceCertificateForTest() {
        return X509ResourceCertificateTest.createSelfSignedCaResourceCertificate();
    }
}
