package net.ripe.rpki.hsm.thales;

import net.ripe.rpki.hsm.api.KeyStorage;
import net.ripe.rpki.hsm.api.KeyStoreParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ThalesBeans {
    @Bean
    public KeyStoreParameters thalesKeystoreParameters(@Autowired KeyStorage keyStorage) {
        return new ThalesDbKeyStoreParameters(keyStorage);
    }
}
