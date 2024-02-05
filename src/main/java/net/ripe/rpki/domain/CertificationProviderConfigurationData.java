package net.ripe.rpki.domain;

import lombok.Data;

@Data
public class CertificationProviderConfigurationData {
	private final String keyStoreProvider;
	private final String keyPairGeneratorProvider;
	private final String signatureProvider;
    private final String keyStoreType;
}
