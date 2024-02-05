package net.ripe.rpki.domain;

import net.ripe.rpki.commons.crypto.util.KeyPairFactory;

import java.security.KeyPair;
import java.util.function.Supplier;

/**
 * Key pair factory that generates keys using the HSM (in prepdev and production). In development software keys
 * are generated. This one is distinct from {@link SingleUseKeyPairFactory} to avoid overloading the HSM with
 * one-time use keys for the EE certificates of CMS objects.
 */
public class HardwareKeyPairFactory implements Supplier<KeyPair> {
    private final KeyPairFactory keyPairFactory;
    private final CertificationProviderConfigurationData providerConfigurationData;

    public HardwareKeyPairFactory(CertificationProviderConfigurationData providerConfigurationData) {
        this.keyPairFactory = new KeyPairFactory(providerConfigurationData.getKeyPairGeneratorProvider());
        this.providerConfigurationData = providerConfigurationData;
    }
    public HardwareKeyPairFactory(CertificationProviderConfigurationData providerConfigurationData, KeyPairFactory keyPairFactory) {
        this.keyPairFactory = keyPairFactory.withProvider(providerConfigurationData.getSignatureProvider());
        this.providerConfigurationData = providerConfigurationData;
    }

    @Override
    public KeyPair get() {
        return keyPairFactory.generate();
    }

    public String keyPairGeneratorProvider() {
        return providerConfigurationData.getKeyPairGeneratorProvider();
    }

    public String signatureProvider() {
        return providerConfigurationData.getSignatureProvider();
    }
}
