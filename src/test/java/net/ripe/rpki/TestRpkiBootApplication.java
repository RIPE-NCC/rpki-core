package net.ripe.rpki;

import net.ripe.rpki.commons.crypto.util.PregeneratedKeyPairFactory;
import net.ripe.rpki.domain.CertificationProviderConfigurationData;
import net.ripe.rpki.domain.HardwareKeyPairFactory;
import net.ripe.rpki.domain.SingleUseKeyPairFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mail.MailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@SpringBootApplication
@ComponentScan(value = "net.ripe.rpki", lazyInit = true)
@EnableJpaRepositories("net.ripe.rpki.domain")
@EntityScan({"net.ripe.rpki.domain", "net.ripe.rpki.ripencc.cache", "net.ripe.rpki.ripencc.support.persistence"})
public class TestRpkiBootApplication {

    public static void main(String... args) {
        SpringApplication.run(TestRpkiBootApplication.class, args);
    }

    @Bean
    public HardwareKeyPairFactory hardwareKeyPairFactory(CertificationProviderConfigurationData certificationProviderConfigurationData) {
        return new HardwareKeyPairFactory(certificationProviderConfigurationData, PregeneratedKeyPairFactory.getInstance());
    }

    @Bean
    public SingleUseKeyPairFactory singleUseKeyPairFactory() {
        return new SingleUseKeyPairFactory(PregeneratedKeyPairFactory.getInstance());
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
