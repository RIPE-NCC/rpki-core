package net.ripe.rpki.hsm.thales;

import com.thales.esecurity.asg.ripe.dbjce.DBLoadStoreImpl;
import com.thales.esecurity.asg.ripe.dbjce.DatabaseInterface;
import net.ripe.rpki.commons.crypto.util.KeyStoreUtil;
import net.ripe.rpki.hsm.api.KeyStoreParameters;
import net.ripe.rpki.hsm.api.KeyStorage;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;

public class ThalesDbKeyStoreParameters implements KeyStoreParameters {
    private final DatabaseInterface databaseInterface;

    public ThalesDbKeyStoreParameters(KeyStorage keyStorage) {
        this.databaseInterface = new ThalesDbAdapter(keyStorage);
    }

    @Override
    public KeyStore.LoadStoreParameter getDbLoadStore(InputStream content) throws IOException {
        return new DBLoadStoreImpl(databaseInterface, content, KeyStoreUtil.KEYSTORE_PASSPHRASE);
    }

    @Override
    public String vendor() {
        return "Thales";
    }
}