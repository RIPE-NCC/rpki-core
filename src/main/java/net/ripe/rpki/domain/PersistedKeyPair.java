package net.ripe.rpki.domain;

import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.hsm.Keys;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.PostRemove;
import javax.persistence.Transient;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

@Embeddable
public class PersistedKeyPair {

    @Column(name = "keystore", nullable = true)
    private byte[] keyStore;

    @Column(name = "keystore_provider", nullable = true)
    private String keyStoreProvider;

    @Column(name = "signature_provider", nullable = false)
    private String signatureProvider;

    @Column(name = "keystore_type", nullable = true)
    private String keyStoreType;

    @Transient
    private transient KeyPair keyPair;

    protected PersistedKeyPair() {}

    public PersistedKeyPair(KeyPairEntityKeyInfo keyInfo, KeyPairEntitySignInfo signInfo) {
        this.keyStoreProvider = signInfo.getKeyStoreProvider();
        this.signatureProvider = signInfo.getSignatureProvider();
        this.keyStoreType = signInfo.getKeyStoreType();

        // Must be done after provider has been set!
        setKeyPair(keyInfo.getKeyPair());
    }

    private void setKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
        keyStore = Keys.get().ksToBytes(keyPair, keyStoreProvider, signatureProvider, keyStoreType);
    }

    @PostRemove
    public void removeKey() {
        Keys.get().clearKeyStore(keyStore, keyStoreProvider, keyStoreType);
    }

    public PrivateKey getPrivateKey() {
        return getKeyPair().getPrivate();
    }

    public PublicKey getPublicKey() {
        return getKeyPair().getPublic();
    }

    public KeyPair getKeyPair() {
        if (keyPair == null) {
            keyPair = Keys.get().getKeyPairFromKeyStore(keyStore, keyStoreProvider, keyStoreType);
        }
        return keyPair;
    }

    public String getSignatureProvider() {
        return signatureProvider;
    }

    public String getEncodedKeyIdentifier() {
        PublicKey key = getPublicKey();
        return KeyPairUtil.getEncodedKeyIdentifier(key);
    }

    /**
     * Needed by NcipherKeyPairArchiver, nCipher uses filenames based on the keystore
     */
    public String getKeyStoreString() {
        return new String(keyStore);
    }

    public String getKeyStoreProviderString() {
        return keyStoreProvider;
    }

    public KeyStoreProvider getKeyStoreProvider() {
        switch (keyStoreProvider) {
            case "nCipherKM" :
                return KeyStoreProvider.NCIPHER;
            case "nCipherKM.database" :
                return KeyStoreProvider.NCIPHER_DATABASE;
            case "SUN" :
                return KeyStoreProvider.SOFTWARE;
            default :
                return KeyStoreProvider.UNKNOWN;
        }
    }

    public enum KeyStoreProvider {
        NCIPHER,
        NCIPHER_DATABASE,
        SOFTWARE,
        UNKNOWN
    }
}
