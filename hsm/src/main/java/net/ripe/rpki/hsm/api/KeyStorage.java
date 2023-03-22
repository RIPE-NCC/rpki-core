package net.ripe.rpki.hsm.api;

import java.security.cert.Certificate;
import java.util.Enumeration;

public interface KeyStorage {
    String getVersion();

    void storeEncryptedKeyAndCerts(String keyStoreName, String alias, byte[] keyBlob, Certificate[] certificates);

    byte[] getEncryptedKey(String keyStoreName, String alias);

    Certificate getCertificate(String keyStoreName, String alias);

    Certificate[] getCertificateChain(String keyStoreName, String alias);

    boolean containsAlias(String keyStoreName, String alias);

    void deleteEntry(String keyStoreName, String alias);

    Enumeration<String> aliases(String keyStoreName);

    int keystoreSize(String keyStoreName);

    void storeHmacKey(String keyStoreName, byte[] hmacBlob);

    byte[] getHmacKey(String keyStoreName);

    Enumeration<String> listKeyStores();
}
