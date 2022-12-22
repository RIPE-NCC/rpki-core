package net.ripe.rpki.publication.server;

import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Optional;

@Configuration
@Slf4j
public class PublishingServerClientConfig {
    @Bean("publishingClient")
    public WebClient publishingClient(
            @Value("${publication.client.keystore}") String keyStoreLocation,
            @Value("${publication.client.keystore.password}") String keyStorePassword,
            @Value("${publication.client.keystore.alias}") String keyStoreAlias,
            @Value("${publication.client.truststore}") String trustStoreLocation,
            @Value("${publication.client.truststore.password}") String trustStorePassword,
            WebClient.Builder builder
    ) throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, UnrecoverableEntryException {
        Optional<PrivateKeyEntry> clientKey = loadPrivateKeyFromKeyStore(keyStoreLocation, keyStorePassword, keyStoreAlias);
        TrustManagerFactory trustManager = initTrustManager(trustStoreLocation, stringToCharArrayOrNull(trustStorePassword));

        if (trustManager != null) {
            SslContextBuilder sslContextBuilder = SslContextBuilder.forClient()
                    .trustManager(trustManager);
            clientKey.ifPresent(
                    entry -> sslContextBuilder.keyManager(entry.getPrivateKey(), (X509Certificate) entry.getCertificate())
            );
            SslContext sslContext = sslContextBuilder.build();
            HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(2))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(EpollChannelOption.TCP_KEEPIDLE, 300)
                .option(EpollChannelOption.TCP_KEEPINTVL, 60)
                .option(EpollChannelOption.TCP_KEEPCNT, 8)
                .secure(config -> config.sslContext(sslContext));
            builder.clientConnector(new ReactorClientHttpConnector(httpClient));
            builder.codecs(config -> config.defaultCodecs().maxInMemorySize(512 * 1024 * 1024));
            log.info("Configured {} TLS for publication server WebClient", clientKey.map(x -> "mutual").orElse("one-way"));
        } else {
            log.warn("No trust store configured. Skipping TLS configuration for publication server WebClient");
        }
        return builder.build();
    }

    private TrustManagerFactory initTrustManager(String tsLocation, char[] tsPassword) throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
        if (tsLocation == null || tsLocation.isEmpty()) return null;

        KeyStore ksTrust = KeyStore.getInstance("JKS");
        log.info("Loading pub server's SSL certificate for pub server from '{}'", tsLocation);
        ksTrust.load(new FileInputStream(tsLocation), tsPassword);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ksTrust);
        log.info("loaded {} keys (TrustmanagerFactory). Configured {} TrustManagers {}/{}.", ksTrust.size(), tmf.getTrustManagers().length, tmf.getAlgorithm(), tmf.getProvider());
        return tmf;
    }

    private Optional<PrivateKeyEntry> loadPrivateKeyFromKeyStore(String file, String password, String alias) throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, UnrecoverableEntryException {
        if (file == null || file.isEmpty()) return Optional.empty();

        KeyStore ks = KeyStore.getInstance("JKS");
        log.info("Loading publication server client SSL certificate '{}' from '{}'", alias, file);
        try (InputStream in = new FileInputStream(file)) {
            ks.load(in, stringToCharArrayOrNull(password));
        }
        Optional<PrivateKeyEntry> entry = Optional.ofNullable(
                (PrivateKeyEntry) ks.getEntry(alias, new PasswordProtection(stringToCharArrayOrNull(password)))
        );
        if (!entry.isPresent()) {
            log.warn("KeyStore {} does not have an alias {}. mTLS to publication server will be disabled.", file, alias);
        }
        return entry;
    }

    private char[] stringToCharArrayOrNull(String string) {
        return string == null ? null : string.toCharArray();
    }
}
