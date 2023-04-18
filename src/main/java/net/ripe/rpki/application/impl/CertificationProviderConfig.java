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
        @Value("${keystore.type}") String keyStoreType,
        @Value("${fs.keystore.provider:${keystore.provider}}")
        String fsKeyStoreProvider,
        @Value("${fs.keypair.generator.provider:${keypair.generator.provider}}")
        String fsKeyPairGeneratorProvider,
        @Value("${fs.signature.provider:${signature.provider}}")
        String fsSignatureProvider,
        @Value("${fs.keystore.type:${keystore.type}}")
        String fsKeyStoreType
    ) {
        return new CertificationProviderConfigurationData(
            keyStoreProvider, keyPairGeneratorProvider, signatureProvider, keyStoreType,
            fsKeyStoreProvider, fsKeyPairGeneratorProvider, fsSignatureProvider, fsKeyStoreType);
    }
}
