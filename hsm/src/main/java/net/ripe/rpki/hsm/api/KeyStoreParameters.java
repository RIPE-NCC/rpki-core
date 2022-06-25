package net.ripe.rpki.hsm.api;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;

public interface KeyStoreParameters {
    KeyStore.LoadStoreParameter getDbLoadStore(InputStream content) throws IOException;

    void unloadKey(PrivateKey key);

    String vendor();
}
