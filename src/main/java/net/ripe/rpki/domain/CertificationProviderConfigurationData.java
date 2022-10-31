package net.ripe.rpki.domain;

import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;

public class CertificationProviderConfigurationData extends ValueObjectSupport {

	private final String keyStoreProvider;
	private final String keyPairGeneratorProvider;
	private final String signatureProvider;
    private final String keyStoreType;

    public CertificationProviderConfigurationData(String keyStoreProvider, String keyPairGeneratorProvider, String signatureProvider, String keyStoreType) {
    	this.keyStoreProvider = keyStoreProvider;
    	this.keyPairGeneratorProvider = keyPairGeneratorProvider;
    	this.signatureProvider = signatureProvider;
    	this.keyStoreType = keyStoreType;
    }

    public String getKeyStoreProvider() {
    	return keyStoreProvider;
    }

    public String getKeyPairGeneratorProvider() {
    	return keyPairGeneratorProvider;
    }

    public String getSignatureProvider() {
    	return signatureProvider;
    }

    public String getKeyStoreType() {
    	return keyStoreType;
    }
}
