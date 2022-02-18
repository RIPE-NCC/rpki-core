package net.ripe.rpki.publication.server;

import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.logging.LoggingFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Configuration
@Slf4j
public class PublicationServerClientConfig {
    @Bean("publishingServer")
    public Client publishingServer(
        @Value("${publication.client.keystore}") String keyStoreLocation,
        @Value("${publication.client.keystore.password}") String keyStorePassword,
        @Value("${publication.client.truststore}") String trustStoreLocation,
        @Value("${publication.client.truststore.password}") String trustStorePassword
    ) throws CertificateException, IOException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        final ClientConfig config = new ClientConfig();
        config.property(ClientProperties.READ_TIMEOUT, 10 * 60 * 1000);
        config.property(ClientProperties.CONNECT_TIMEOUT, 5 * 1000);
        config.register(new LoggingFeature(Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME),
                Level.INFO, LoggingFeature.Verbosity.PAYLOAD_ANY, 1024));

        final KeyManager[] keyManagers = initKeyManager(keyStoreLocation, stringToCharArrayOrNull(keyStorePassword));
        final TrustManager[] trustManagers = initTrustManager(trustStoreLocation, stringToCharArrayOrNull(trustStorePassword));

        final ClientBuilder clientBuilder = ClientBuilder
                .newBuilder()
                .withConfig(config);

        if (keyManagers != null || trustManagers != null) {
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            return clientBuilder
                    .sslContext(sslContext)
                    .hostnameVerifier(new AllowAllHostnameVerifier()).build();
        }

        log.warn("Building pub server client w/o SSL");
        return clientBuilder.build();
    }

    private TrustManager[] initTrustManager(String tsLocation, char[] tsPassword) throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
        if (tsLocation == null || tsLocation.isEmpty()) return null;

        KeyStore ksTrust = KeyStore.getInstance("JKS");
        log.info("Loading pub server's SSL certificate for pub server from '{}'", tsLocation);
        ksTrust.load(new FileInputStream(tsLocation), tsPassword);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ksTrust);
        log.info("loaded {} keys (TrustmanagerFactory). Configured {} TrustManagers {}/{}.", ksTrust.size(), tmf.getTrustManagers().length, tmf.getAlgorithm(), tmf.getProvider());
        return tmf.getTrustManagers();
    }

    private KeyManager[] initKeyManager(String ksLocation, char[] ksPassword) throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        if (ksLocation == null || ksLocation.isEmpty()) return null;

        KeyStore ksKeys = KeyStore.getInstance("JKS");
        log.info("Loading client SSL certificate for pub server from '{}'", ksLocation);
        ksKeys.load(new FileInputStream(ksLocation), ksPassword);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ksKeys, ksPassword);
        log.info("loaded {} keys (KeymanagerFactory). Configured {} KeyManagers {}/{}.", ksKeys.size(), kmf.getKeyManagers().length, kmf.getAlgorithm(), kmf.getProvider());

        return kmf.getKeyManagers();
    }

    private char[] stringToCharArrayOrNull(String string) {
        return string == null ? null : string.toCharArray();
    }
}
