package net.ripe.rpki.domain;

/**
 * Just a little helper to construct this KeyPairEntity a little more comfortably 
 * TODO: make this embedded?
 */
public class KeyPairEntitySignInfo {
    
    private String keyStoreProvider;
    private String signatureProvider;
    private String keyStoreType;

    public KeyPairEntitySignInfo(String keyStoreProvider, String signatureProvider, String keyStoreType) {
        this.keyStoreProvider = keyStoreProvider;
        this.signatureProvider = signatureProvider;
        this.keyStoreType = keyStoreType;
    }

    public String getKeyStoreProvider() {
        return keyStoreProvider;
    }

    public String getSignatureProvider() {
        return signatureProvider;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }
}