package net.ripe.rpki.publication.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
@Slf4j
public class PublishingServerClient {

    public static final MediaType PUBLICATION_MEDIA_TYPE = new MediaType("application", "rpki-publication");
    public static final String CLIENT_ID_PARAM = "clientId";

    private final WebClient client;

    @Autowired
    public PublishingServerClient(@Qualifier("publishingClient") WebClient publishingClient)  {
        this.client = publishingClient;
    }

    public Mono<String> publish(URI publishingServerUrl, String xml, String clientId) {
        URI uri = UriComponentsBuilder.fromUri(publishingServerUrl).queryParam(CLIENT_ID_PARAM, clientId).build().toUri();
        return client.post()
                .uri(uri)
                .accept(PUBLICATION_MEDIA_TYPE)
                .contentType(PUBLICATION_MEDIA_TYPE)
                .bodyValue(xml)
                .retrieve()
                .bodyToMono(String.class);
    }
}
