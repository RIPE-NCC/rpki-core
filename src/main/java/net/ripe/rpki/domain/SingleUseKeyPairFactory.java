package net.ripe.rpki.domain;

import net.ripe.rpki.commons.crypto.util.KeyPairFactory;

import java.security.KeyPair;
import java.util.function.Supplier;

/**
 * Key pair factory that generates keys using the software <code>SunRsaSign</code> algorithm. These keys should only
 * be used for single-use signing of CMS objects. See the {@link HardwareKeyPairFactory} for generating keys that
 * should be safely stored by the HSM in production.
 */
public class SingleUseKeyPairFactory implements Supplier<KeyPair> {
    private static final String SINGLE_USE_KEY_PAIR_PROVIDER = "SunRsaSign";

    private final KeyPairFactory keyPairFactory;

    public SingleUseKeyPairFactory() {
        this.keyPairFactory = new KeyPairFactory(keyPairGeneratorProvider());
    }

    public SingleUseKeyPairFactory(KeyPairFactory keyPairFactory) {
        this.keyPairFactory = keyPairFactory.withProvider(keyPairGeneratorProvider());
    }

    @Override
    public KeyPair get() {
        return keyPairFactory.generate();
    }

    public String keyPairGeneratorProvider() {
        return SINGLE_USE_KEY_PAIR_PROVIDER;
    }

    public String signatureProvider() {
        return SINGLE_USE_KEY_PAIR_PROVIDER;
    }
}
