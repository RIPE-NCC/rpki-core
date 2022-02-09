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

    @Override
    public void storeEncriptedKeyAndCerts(String s, String s1, byte[] bytes, Certificate[] certificates) {
        ks.storeEncryptedKeyAndCerts(s, s1, bytes, certificates);
    }

    @Override
    public byte[] getEncriptedKey(String s, String s1) {
        return ks.getEncryptedKey(s, s1);
    }
}
