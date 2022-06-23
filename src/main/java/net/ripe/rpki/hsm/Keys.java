package net.ripe.rpki.hsm;

import net.ripe.rpki.commons.crypto.util.KeyStoreException;
import net.ripe.rpki.commons.crypto.util.KeyStoreUtil;
import net.ripe.rpki.hsm.api.KeyStoreParameters;
import net.ripe.rpki.util.JdbcDBComponent;

import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyStore;
import java.util.Optional;

public class Keys {
    private static Keys instance;

    public static Keys get() {
        if (instance == null) {
            throw new IllegalStateException("'Keys' is not initialized.");
        }
        return instance;
    }

    public static Keys initialize(Optional<KeyStoreParameters> keyStoreParameters) {
        instance = new Keys(keyStoreParameters);
        return instance;
    }

    private Optional<KeyStoreParameters> keyStoreParameters = Optional.empty();

    public Keys(Optional<KeyStoreParameters> keyStoreParameters) {
        this.keyStoreParameters = keyStoreParameters;
    }

    public byte[] ksToBytes(KeyPair keyPair, String keyStoreProvider, String signatureProvider, String keyStoreType) {
        final KeyStore ks = isDbProvider(keyStoreProvider) ?
            KeyStoreUtil.createKeyStoreForKeyPair(keyPair, keyStoreProvider, signatureProvider, keyStoreType, this::loadHsmDatabaseKeyStore) :
            KeyStoreUtil.createKeyStoreForKeyPair(keyPair, keyStoreProvider, signatureProvider, keyStoreType);

        return KeyStoreUtil.storeKeyStore(ks);
    }

    public void clearKeyStore(byte[] keyStore, String keyStoreProvider, String keyStoreType) {
        if (isDbProvider(keyStoreProvider)) {
            KeyStoreUtil.clearKeyStore(keyStore, keyStoreProvider, keyStoreType, this::loadHsmDatabaseKeyStore);
        } else {
            JdbcDBComponent.afterCommit(() -> KeyStoreUtil.clearKeyStore(keyStore, keyStoreProvider, keyStoreType));
        }
    }

    public KeyPair getKeyPairFromKeyStore(byte[] keyStore, String keyStoreProvider, String keyStoreType) {
        return isDbProvider(keyStoreProvider) ?
            KeyStoreUtil.getKeyPairFromKeyStore(keyStore, keyStoreProvider, keyStoreType, this  ::loadHsmDatabaseKeyStore) :
            KeyStoreUtil.getKeyPairFromKeyStore(keyStore, keyStoreProvider, keyStoreType);
    }

    public boolean isDbProvider(String keyStoreProvider) {
        return "DBProvider".equals(keyStoreProvider);
    }

    public String keystoreVendor() {
        return keyStoreParameters.map(KeyStoreParameters::vendor).orElse("none");
    }

    private void loadHsmDatabaseKeyStore(final KeyStore keyStore) {
        loadHsmDatabaseKeyStore(keyStore, null);
    }

    private void loadHsmDatabaseKeyStore(final KeyStore keyStore, final InputStream content) {
        try {
            keyStore.load(keyStoreParameters.get().getDbLoadStore(content));
        } catch (Exception e) {
            throw new KeyStoreException(e);
        }
    }

}
