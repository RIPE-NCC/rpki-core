package net.ripe.rpki.hsm.thales;

import com.thales.esecurity.asg.ripe.dbjce.DatabaseInterface;
import lombok.experimental.Delegate;
import net.ripe.rpki.hsm.api.KeyStorage;

import java.security.cert.Certificate;

public class ThalesDbAdapter implements DatabaseInterface {

    @Delegate
    private final KeyStorage ks;

    public ThalesDbAdapter(KeyStorage ks) {
        this.ks = ks;
    }

    // rename and call delegate
    @Override
    public void storeEncriptedKeyAndCerts(String keyStoreName, String alias, byte[] keyblob, Certificate[] certificates) {
        ks.storeEncryptedKeyAndCerts(keyStoreName, alias, keyblob, certificates);
    }

    // rename and call delegate
    @Override
    public byte[] getEncriptedKey(String keyStoreName, String alias) {
        return ks.getEncryptedKey(keyStoreName, alias);
    }
}
