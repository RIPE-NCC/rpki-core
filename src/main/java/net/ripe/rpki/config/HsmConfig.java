package net.ripe.rpki.config;

import net.ripe.rpki.hsm.Keys;
import net.ripe.rpki.hsm.api.KeyStoreParameters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class HsmConfig {
    @Bean
    public Keys keys(Optional<KeyStoreParameters> keyStoreParameters) {
        return Keys.initialize(keyStoreParameters);
    }
}
