package net.ripe.rpki.hsm.api;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;

public interface KeyStoreParameters {
    KeyStore.LoadStoreParameter getDbLoadStore(InputStream content) throws IOException;

    String vendor();
}
