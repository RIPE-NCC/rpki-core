package net.ripe.rpki.publication.server;

import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import org.glassfish.jersey.logging.LoggingFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import java.io.FileInputStream;
import java.net.URI;
import java.security.KeyStore;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
@Slf4j
public class PublishingServerClient {

    public static final MediaType PUBLICATION_MEDIA_TYPE = new MediaType("application", "rpki-publication");
    public static final String CLIENT_ID_PARAM = "clientId";

    private final Client publishingServer;
    private final char[] keyStorePassword;
    private final char[] trustStorePassword;
    private final String keyStoreLocation;
    private final String trustStoreLocation;

    @Autowired
    public PublishingServerClient(@Value("${publication.client.keystore}") String keyStoreLocation,
                                  @Value("${publication.client.keystore.password}") String keyStorePassword,
                                  @Value("${publication.client.truststore}") String trustStoreLocation,
                                  @Value("${publication.client.truststore.password}") String trustStorePassword)  {
        this.keyStoreLocation = keyStoreLocation;
        this.trustStoreLocation = trustStoreLocation;
        this.keyStorePassword = stringToCharArrayOrNull(keyStorePassword);
        this.trustStorePassword = stringToCharArrayOrNull(trustStorePassword);

        try {
            // TODO Implement this
//            client.addFilter(new JerseyClientRetryHandler());

            publishingServer = client();
        } catch (Exception e) {
            log.error("Can't create http client for publication server:", e);
            throw new RuntimeException(e);
        }
    }

    private char[] stringToCharArrayOrNull(String string) {
        return string == null ? null : string.toCharArray();
    }

    private Client client() throws Exception {

        final ClientConfig config = new ClientConfig();
        config.property(ClientProperties.READ_TIMEOUT, 10 * 60 * 1000);
        config.property(ClientProperties.CONNECT_TIMEOUT, 5 * 1000);
        config.register(new LoggingFeature(Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME),
            Level.INFO, LoggingFeature.Verbosity.PAYLOAD_ANY, 1024));

        final KeyManager[] keyManagers = initKeyManager(keyStoreLocation, keyStorePassword);
        final TrustManager[] trustManagers = initTrustManager(trustStoreLocation, trustStorePassword);

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

        return clientBuilder.build();
    }

    private TrustManager[] initTrustManager(String tsLocation, char[] tsPassword) throws Exception {
        if (tsLocation == null || tsLocation.isEmpty()) return null;

        KeyStore ksTrust = KeyStore.getInstance("JKS");
        log.info("Loading pub server's SSL certificate for pub server from '{}'", tsLocation);
        ksTrust.load(new FileInputStream(tsLocation), tsPassword);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ksTrust);
        log.info("loaded {} keys (TrustmanagerFactory). Configured {} TrustManagers {}/{}.", ksTrust.size(), tmf.getTrustManagers().length, tmf.getAlgorithm(), tmf.getProvider());
        return tmf.getTrustManagers();
    }

    private KeyManager[] initKeyManager(String ksLocation, char[] ksPassword) throws Exception {
        if (ksLocation == null || ksLocation.isEmpty()) return null;

        KeyStore ksKeys = KeyStore.getInstance("JKS");
        log.info("Loading client SSL certificate for pub server from '{}'", ksLocation);
        ksKeys.load(new FileInputStream(ksLocation), ksPassword);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ksKeys, ksPassword);
        log.info("loaded {} keys (KeymanagerFactory). Configured {} KeyManagers {}/{}.", ksKeys.size(), kmf.getKeyManagers().length, kmf.getAlgorithm(), kmf.getProvider());

        return kmf.getKeyManagers();
    }

    public String publish(URI publishingServerUrl, String xml, String clientId) {
        return publishingServer.target(publishingServerUrl)
                .queryParam(CLIENT_ID_PARAM, clientId)
                .request(PUBLICATION_MEDIA_TYPE)
                .post(Entity.entity(xml, PUBLICATION_MEDIA_TYPE))
                .readEntity(String.class);
    }
}

/**
 * HostnameVerifier that skips the hostname checks.
 *
 * <strong>Only use in combination with mutual TLS</strong>.
 */
class AllowAllHostnameVerifier implements HostnameVerifier {
    @SuppressWarnings("java:S5527")
    @Override
    public boolean verify(String s, SSLSession sslSession) {
        return true;
    }
}