package net.ripe.rpki.hsm.api;

import java.security.cert.Certificate;
import java.util.Enumeration;

public interface KeyStorage {
    String getVersion();

    void storeEncryptedKeyAndCerts(String s, String s1, byte[] bytes, Certificate[] certificates);

    byte[] getEncryptedKey(String s, String s1);

    Certificate getCertificate(String s, String s1);

    Certificate[] getCertificateChain(String s, String s1);

    boolean containsAlias(String s, String s1);

    void deleteEntry(String s, String s1);

    Enumeration<String> aliases(String s);

    int keystoreSize(String s);

    void storeHmacKey(String s, byte[] bytes);

    byte[] getHmacKey(String s);

    Enumeration<String> listKeyStores();
}
