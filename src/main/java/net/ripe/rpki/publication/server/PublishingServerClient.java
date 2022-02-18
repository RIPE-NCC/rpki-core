package net.ripe.rpki.publication.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import java.net.URI;

@Component
@Slf4j
public class PublishingServerClient {

    public static final MediaType PUBLICATION_MEDIA_TYPE = new MediaType("application", "rpki-publication");
    public static final String CLIENT_ID_PARAM = "clientId";

    private final Client publishingServer;

    @Autowired
    public PublishingServerClient(@Qualifier("publishingServer") Client publishingServer)  {
        this.publishingServer = publishingServer;
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