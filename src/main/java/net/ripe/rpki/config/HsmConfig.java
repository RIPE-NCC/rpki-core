package net.ripe.rpki.config;

import net.ripe.rpki.hsm.Keys;
import net.ripe.rpki.hsm.api.KeyStoreParameters;
import net.ripe.rpki.ripencc.ui.daemon.health.checks.CryptoChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.Optional;

@Configuration
public class HsmConfig {

    private final CryptoChecker cryptoChecker;

    public HsmConfig(CryptoChecker cryptoChecker) {
        this.cryptoChecker = cryptoChecker;
    }

    /**
     * This bean cannot be lazy since we use a global variable to access it after initialization.
     */
    @Lazy(false)
    @Bean
    public Keys keys(Optional<KeyStoreParameters> keyStoreParameters) {
        final Keys keys = Keys.initialize(keyStoreParameters);
        cryptoChecker.checkCryptoWorks();
        return keys;
    }
}
