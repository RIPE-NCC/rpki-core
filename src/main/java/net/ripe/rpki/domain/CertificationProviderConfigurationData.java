package net.ripe.rpki.domain;

import lombok.Getter;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;

@Getter
public class CertificationProviderConfigurationData extends ValueObjectSupport {

	private final String keyStoreProvider;
	private final String keyPairGeneratorProvider;
	private final String signatureProvider;
    private final String keyStoreType;

    private final String fsKeyStoreProvider;
	private final String fsKeyPairGeneratorProvider;
	private final String fsSignatureProvider;
    private final String fsKeyStoreType;

    public CertificationProviderConfigurationData(String keyStoreProvider,
                                                  String keyPairGeneratorProvider,
                                                  String signatureProvider,
                                                  String keyStoreType,
                                                  String fsKeyStoreProvider,
                                                  String fsKeyPairGeneratorProvider,
                                                  String fsSignatureProvider,
                                                  String fsKeyStoreType) {
    	this.keyStoreProvider = keyStoreProvider;
    	this.keyPairGeneratorProvider = keyPairGeneratorProvider;
    	this.signatureProvider = signatureProvider;
    	this.keyStoreType = keyStoreType;
        this.fsKeyStoreProvider = fsKeyStoreProvider;
        this.fsKeyPairGeneratorProvider = fsKeyPairGeneratorProvider;
        this.fsSignatureProvider = fsSignatureProvider;
        this.fsKeyStoreType = fsKeyStoreType;
    }

    public boolean hasDifferentProviders() {
        return !keyStoreProvider.equals(fsKeyStoreProvider) ||
            !keyPairGeneratorProvider.equals(fsKeyPairGeneratorProvider) ||
            !signatureProvider.equals(fsSignatureProvider) ||
            !keyStoreType.equals(fsKeyStoreType);
    }

}
