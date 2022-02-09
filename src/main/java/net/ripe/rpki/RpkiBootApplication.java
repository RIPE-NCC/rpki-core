package net.ripe.rpki;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mail.MailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Profile("!test")
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
@EnableJpaRepositories("net.ripe.rpki.domain")
@EntityScan({"net.ripe.rpki.domain", "net.ripe.rpki.ripencc.cache", "net.ripe.rpki.ripencc.support.persistence"})
@Slf4j
public class RpkiBootApplication {
    // Swagger configuration.
    public static final String API_KEY_REFERENCE = "API key";
    public static final String USER_ID_REFERENCE = "User audit";

    public static void main(String... args) {
        SpringApplication.run(RpkiBootApplication.class, args);
    }

    @Bean
    @Primary
    public KeyPairFactory serviceFactory(@Value("${keypair.generator.provider}") final String keyPairGenerator) {
        return new KeyPairFactory(keyPairGenerator);
    }

    @Bean(name = "oneTimeKeyPairFactory")
    public KeyPairFactory oneTimeKeyPairFactory() {
        return new KeyPairFactory("SunRsaSign");
    }


    @Bean
    public MailSender mailSender(@Value("${mail.host}") String host,
                                 @Value("${mail.port}") int port) {
        final JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        return mailSender;
    }

}
