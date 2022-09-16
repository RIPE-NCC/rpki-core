package net.ripe.rpki.ripencc.services.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateParser;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.server.api.ports.NonHostedPublisherRepositoryService;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.logging.LoggingFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@Profile("!test")
@ConditionalOnProperty(prefix="non-hosted.publisher.repository", value="enabled", havingValue = "true")
public class KrillNonHostedPublisherRepositoryBean implements NonHostedPublisherRepositoryService {

    public static final String MONITORING_TARGET = "/stats/info";

    //https://krill.docs.nlnetlabs.nl/en/stable/publication-server.html#add-a-publisher
    public static final String PUBD_INITIALIZE = "/api/v1/pubd/init";

    // https://krill.docs.nlnetlabs.nl/en/stable/publication-server.html#initialise-publication-server
    public static final String PUBD_PUBLISHERS = "/api/v1/pubd/publishers";


    private final Client publisherRepositoryClient;
    private final String publisherRepositoryURL;
    private final String apiToken;

    @Inject
    public KrillNonHostedPublisherRepositoryBean(
        @Value("${non-hosted.publisher.repository.url}") String publisherRepositoryURL,
        @Value("${non-hosted.publisher.repository.token}") String apiToken
    ) throws NoSuchAlgorithmException {
        this(publisherRepositoryURL, apiToken, SSLContext.getDefault());
    }

    public KrillNonHostedPublisherRepositoryBean(
        String publisherRepositoryURL,
        String apiToken, SSLContext sslContext
    ) {
        this.publisherRepositoryURL = publisherRepositoryURL;
        this.apiToken = apiToken;

        final ClientConfig clientConfig = new ClientConfig();
        clientConfig.property(ClientProperties.CONNECT_TIMEOUT, 5 * 1000);
        clientConfig.property(ClientProperties.READ_TIMEOUT, 31 * 1000);
        clientConfig.register(new LoggingFeature(Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME),
            Level.INFO, LoggingFeature.Verbosity.PAYLOAD_ANY, 1024));

        publisherRepositoryClient = ClientBuilder.newBuilder()
            .sslContext(sslContext)
            .withConfig(clientConfig)
            .build();

        log.info("Repository publisher points to {}.", publisherRepositoryURL);
    }

    private Invocation.Builder clientForTarget(String pathTarget) {
        return publisherRepositoryClient.target(publisherRepositoryURL)
                .path(pathTarget)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken);
    }

    @Override
    public boolean isAvailable() {
        log.debug("Checking if repository publisher REST API is available");
        try {

            return publisherRepositoryClient
                .target(publisherRepositoryURL)
                .path(MONITORING_TARGET)
                .request(MediaType.APPLICATION_JSON)
                .get().getStatus() == 200;
        } catch (Exception t) {
            return false;
        }
    }


    @Override
    public RepositoryResponse provisionPublisher(UUID publisherHandle, PublisherRequest publisherRequest) {

        PublisherRequest requestWithPublisherHandle = new PublisherRequest(Optional.empty(),
            publisherHandle.toString(), publisherRequest.getPublisherBpkiTa(), Optional.empty());

        PublisherRequestDto krillPublisherRequest =
            new PublisherRequestDto(
                requestWithPublisherHandle.getPublisherBpkiTa().getBase64String(),
                requestWithPublisherHandle.getPublisherHandle());

        Response post = clientForTarget(PUBD_PUBLISHERS).post(Entity.json(krillPublisherRequest));
        RepositoryResponseDto repositoryResponseDto = post.readEntity(RepositoryResponseDto.class);
        return repositoryResponseDto.toRepositoryResponse();
    }

    @Override
    public Set<UUID> listPublishers() {
        return clientForTarget(PUBD_PUBLISHERS).get().readEntity(PublishersDto.class).publishers.stream().flatMap(handle -> {
            try {
                return Stream.of(UUID.fromString(handle.handle));
            } catch (IllegalArgumentException e) {
                return Stream.empty();
            }
        }).collect(Collectors.toSet());
    }

    @Override
    public Response deletePublisher(UUID publisherHandle) {
        return clientForTarget(PUBD_PUBLISHERS + "/" + publisherHandle).delete();
    }


    public boolean isInitialized() {
        try {
            Response response = clientForTarget(PUBD_PUBLISHERS).get();
            return response.getStatus() == 200;
        } catch (Exception t) {
            return false;
        }
    }

    @AllArgsConstructor
    static class PublisherInitDto {
        @JsonProperty("rrdp_base_uri")
        public String rrdpBaseUri;
        @JsonProperty("rsync_jail")
        public String rsyncJail;
    }

    // Krill JSON for request/response does not match exactly with XML
    // See: https://krill.docs.nlnetlabs.nl/en/stable/publication-server.html#add-a-publisher
    static class PublisherRequestDto {
        @JsonProperty("id_cert")
        public String idCert;
        @JsonProperty("publisher_handle")
        public String publisherHandle;

        public PublisherRequestDto(String idCert, String publisherHandle) {
            this.idCert = idCert;
            this.publisherHandle = publisherHandle;
        }
    }


    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RepositoryResponseDto {

        private String tag;
        @JsonProperty("id_cert")
        private String idCert;
        @JsonProperty("publisher_handle")
        private String publisherHandle;
        @JsonProperty("service_uri")
        private String serviceUri;

        @JsonProperty("repo_info")
        private RepositoryInfo repositoryInfo;

        @SneakyThrows
        public RepositoryResponse toRepositoryResponse() {
            return new RepositoryResponse(Optional.of(tag == null ? "" : tag),
                    new URI(serviceUri), publisherHandle,
                    new URI(repositoryInfo.siaBase),
                    Optional.of(new URI(repositoryInfo.rrdpNotificationUri)),
                    getProvisioningIdentityCertificate(idCert));
        }

        private ProvisioningIdentityCertificate getProvisioningIdentityCertificate(final String bpkiTa) {
            final ProvisioningIdentityCertificateParser parser = new ProvisioningIdentityCertificateParser();
            parser.parse(ValidationResult.withLocation("unknown.cer"), Base64.getMimeDecoder().decode(bpkiTa));
            return parser.getCertificate();
        }
    }

    @Getter
    static class RepositoryInfo {
        @JsonProperty("rrdp_notification_uri")
        public String rrdpNotificationUri;
        @JsonProperty("sia_base")
        public String siaBase;
    }

    @Getter
    static class PublishersDto {
        List<PublisherHandleDto> publishers;
    }

    @Getter
    static class PublisherHandleDto {
        String handle;
    }

    @Data
    static class ErrorMsg {
        String label;
        String msg;
        Object args;
    }
}
