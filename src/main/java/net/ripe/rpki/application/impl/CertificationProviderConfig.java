package net.ripe.rpki.application.impl;

import net.ripe.rpki.domain.CertificationProviderConfigurationData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CertificationProviderConfig {
    @Bean
    public CertificationProviderConfigurationData certificationProviderConfigurationData(
        @Value("${keystore.provider}") String keyStoreProvider,
        @Value("${keypair.generator.provider}") String keyPairGeneratorProvider,
        @Value("${signature.provider}") String signatureProvider,
        @Value("${keystore.type}") String keyStoreType
    ) {
        return new CertificationProviderConfigurationData(
                keyStoreProvider,
                keyPairGeneratorProvider,
                signatureProvider,
                keyStoreType
        );
    }
}
