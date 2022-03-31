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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Duration;

@Configuration
@Slf4j
public class PublishingServerClientConfig {
    @Bean("publishingClient")
    public WebClient publishingClient(
            @Value("${publication.client.keystore}") String keyStoreLocation,
            @Value("${publication.client.keystore.password}") String keyStorePassword,
            @Value("${publication.client.truststore}") String trustStoreLocation,
            @Value("${publication.client.truststore.password}") String trustStorePassword,
            WebClient.Builder builder
    ) throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyManagerFactory keyManager = initKeyManager(keyStoreLocation, stringToCharArrayOrNull(keyStorePassword));
        TrustManagerFactory trustManager = initTrustManager(trustStoreLocation, stringToCharArrayOrNull(trustStorePassword));

        if (keyManager != null || trustManager != null) {
            final SslContext sslContext = SslContextBuilder.forClient().keyManager(keyManager).trustManager(trustManager).build();
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
            log.info("Configured pub-server WebClient w/ mTLS");
        } else {
            log.warn("Skipping mTLS configuration for pub-server WebClient");
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

    private KeyManagerFactory initKeyManager(String ksLocation, char[] ksPassword) throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        if (ksLocation == null || ksLocation.isEmpty()) return null;

        KeyStore ksKeys = KeyStore.getInstance("JKS");
        log.info("Loading client SSL certificate for pub server from '{}'", ksLocation);
        ksKeys.load(new FileInputStream(ksLocation), ksPassword);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ksKeys, ksPassword);
        log.info("loaded {} keys (KeymanagerFactory). Configured {} KeyManagers {}/{}.", ksKeys.size(), kmf.getKeyManagers().length, kmf.getAlgorithm(), kmf.getProvider());
        return kmf;
    }

    private char[] stringToCharArrayOrNull(String string) {
        return string == null ? null : string.toCharArray();
    }
}
