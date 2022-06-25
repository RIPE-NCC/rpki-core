package net.ripe.rpki.hsm.thales;

import com.thales.esecurity.asg.ripe.dbjce.DBLoadStoreImpl;
import com.thales.esecurity.asg.ripe.dbjce.DBProvider;
import com.thales.esecurity.asg.ripe.dbjce.DatabaseInterface;
import lombok.SneakyThrows;
import net.ripe.rpki.commons.crypto.util.KeyStoreUtil;
import net.ripe.rpki.hsm.api.KeyStoreParameters;
import net.ripe.rpki.hsm.api.KeyStorage;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;

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
    @SneakyThrows
    public void unloadKey(PrivateKey key) {
        Class<DBProvider> clazz = DBProvider.class;
        Method unloadMethod = clazz.getMethod("unload", Key.class);
        unloadMethod.invoke(null, key);
    }

    @Override
    public String vendor() {
        return "Thales";
    }
}
