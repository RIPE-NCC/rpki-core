package net.ripe.rpki.hsm.thales;

import com.google.common.base.Verify;
import com.thales.esecurity.asg.ripe.dbjce.DBProvider;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.hsm.api.KeyStorage;
import net.ripe.rpki.hsm.api.KeyStoreParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.security.Security;

@Conditional(ThalesBeans.AnyDBProviderUsage.class)
@Slf4j
@Configuration
public class ThalesBeans {
    /**
     * Configuring the security provider in java.security and then getting it by name does not work on JDK 11.
     *
     * Explicitly initialise security provider.
     */
    @PostConstruct
    public void initializeProvider() {
        if (Security.getProvider("DBProvider") == null) {
            log.info("Adding DBProvider security provider which requires a connection to hardserver in a healthy state on localhost TCP/9004.");
            Security.addProvider(new DBProvider());
            // Provider should now be present.
            Verify.verify(Security.getProvider("DBProvider") != null);
        }
    }

    @Bean
    public KeyStoreParameters thalesKeystoreParameters(@Autowired KeyStorage keyStorage) {
        return new ThalesDbKeyStoreParameters(keyStorage);
    }

    public static class AnyDBProviderUsage extends AnyNestedCondition {
        public AnyDBProviderUsage() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @ConditionalOnProperty(value = "keystore.provider", havingValue = "DBProvider", matchIfMissing = true)
        static class KeystoreProviderDBPRovider {
        }

        @ConditionalOnProperty(value = "keypair.generator.provider", havingValue = "DBProvider", matchIfMissing = true)
        static class KeypairGeneratorProviderDBProvider {
        }

        @ConditionalOnProperty(value = "signature.provider", havingValue = "DBProvider", matchIfMissing = true)
        static class SignatureProviderDBPRovider {
        }

        @ConditionalOnProperty(value = "keystore.type", havingValue = "nCipher.database", matchIfMissing = true)
        static class KeystoreTypeNCipherDatabase {
        }
    }
}
