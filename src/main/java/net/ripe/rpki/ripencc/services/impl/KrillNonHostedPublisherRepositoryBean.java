package net.ripe.rpki.ripencc.services.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateParser;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.domain.CertificateAuthorityException;
import net.ripe.rpki.server.api.ports.NonHostedPublisherRepositoryService;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.logging.LoggingFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@Profile("!test")
@ConditionalOnProperty(prefix="non-hosted.publisher.repository", value="enabled", havingValue = "true")
public class KrillNonHostedPublisherRepositoryBean implements NonHostedPublisherRepositoryService {

    // https://krill.docs.nlnetlabs.nl/en/stable/cli.html#krillc-health
    public static final String MONITORING_TARGET = "/api/v1/authorized";


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
    ) {
        this.publisherRepositoryURL = publisherRepositoryURL;
        this.apiToken = apiToken;

        final ClientConfig clientConfig = new ClientConfig();
        clientConfig.property(ClientProperties.CONNECT_TIMEOUT, 5 * 1000);
        clientConfig.property(ClientProperties.READ_TIMEOUT, 31 * 1000);



        publisherRepositoryClient = ClientBuilder.newBuilder()
            .withConfig(clientConfig)
            .build();

        log.info("Repository publisher points to {}.", publisherRepositoryURL);
    }

    private Invocation.Builder clientForTarget(String pathTarget) {
        log.debug("API call of core -> krill {}{}", publisherRepositoryURL, pathTarget);
        return publisherRepositoryClient.target(publisherRepositoryURL)
                .path(pathTarget)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken);
    }

    @Override
    public boolean isAvailable() {
        log.debug("Checking if krill REST API is available");
        try {
            // Unauthenticated endpoint which accepts requests with authorisation as well.
            return clientForTarget(MONITORING_TARGET).get().getStatus() == 200;
        } catch (Exception t) {
            log.debug("Requesting repository publisher REST API failed: {}", t.toString());
            return false;
        }
    }

    @Override
    public RepositoryResponse provisionPublisher(UUID publisherHandle, PublisherRequest publisherRequest) throws DuplicateRepositoryException {

        PublisherRequest requestWithPublisherHandle = new PublisherRequest(Optional.empty(),
            publisherHandle.toString(), publisherRequest.getPublisherBpkiTa(), Optional.empty());

        PublisherRequestDto krillPublisherRequest =
            new PublisherRequestDto(
                requestWithPublisherHandle.getPublisherBpkiTa().getBase64String(),
                requestWithPublisherHandle.getPublisherHandle());

        try (Response post = clientForTarget(PUBD_PUBLISHERS).post(Entity.json(krillPublisherRequest))) {
            if (post.getStatus() == HttpStatus.OK.value() || post.getStatus() == HttpStatus.CREATED.value()) {
                RepositoryResponseDto repositoryResponseDto = post.readEntity(RepositoryResponseDto.class);
                return repositoryResponseDto.toRepositoryResponse();
            } else if (post.getStatus() == HttpStatus.BAD_REQUEST.value()) {
                KrillErrorData error = post.readEntity(KrillErrorData.class);
                switch (error.label) {
                    case "pub-duplicate":
                        throw new DuplicateRepositoryException(publisherHandle);
                    default:
                        throw new CertificateAuthorityException(
                            String.format("krill call failed with %d: %s: %s", post.getStatus(), post.getStatusInfo(), error)
                        );
                }
            } else {
                throw new CertificateAuthorityException(
                    String.format("krill call failed with %d: %s", post.getStatus(), post.getStatusInfo())
                );
            }
        }
    }

    @Override
    public Set<UUID> listPublishers() {
        try (Response response = clientForTarget(PUBD_PUBLISHERS).get()) {
            if (HttpStatus.Series.resolve(response.getStatus()) != HttpStatus.Series.SUCCESSFUL) {
                throw new CertificateAuthorityException(String.format("krill call failed with %d: %s", response.getStatus(), response.getStatusInfo()));
            }
            return response.readEntity(PublishersDto.class).publishers.stream().flatMap(handle -> {
                try {
                    return Stream.of(UUID.fromString(handle.handle));
                } catch (IllegalArgumentException e) {
                    return Stream.empty();
                }
            }).collect(Collectors.toSet());
        }
    }

    @Override
    public void deletePublisher(UUID publisherHandle) {
        try (Response response = clientForTarget(PUBD_PUBLISHERS + "/" + publisherHandle).delete()) {
            HttpStatus.Series series = HttpStatus.Series.resolve(response.getStatus());
            switch (series != null ? series : HttpStatus.Series.SERVER_ERROR) {
                case INFORMATIONAL: case SUCCESSFUL: case REDIRECTION:
                    break;
                case CLIENT_ERROR:
                    KrillErrorData error = response.readEntity(KrillErrorData.class);
                    throw new CertificateAuthorityException(String.format("krill client error %d: %s: %s", response.getStatus(), response.getStatusInfo(), error));
                case SERVER_ERROR:
                    throw new CertificateAuthorityException(String.format("krill server error %d: %s", response.getStatus(), response.getStatusInfo()));
            }
        }
    }


    public boolean isInitialized() {
        try (Response response = clientForTarget(PUBD_PUBLISHERS).get()) {
            return HttpStatus.Series.resolve(response.getStatus()) == HttpStatus.Series.SUCCESSFUL;
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
    @AllArgsConstructor
    static class PublisherRequestDto {
        @JsonProperty("id_cert")
        public String idCert;
        @JsonProperty("publisher_handle")
        public String publisherHandle;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class KrillErrorData {
        private String label;
        private String msg;
        private Map<String, String> args;
    }


    @Data
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

        public RepositoryResponse toRepositoryResponse() {
            return new RepositoryResponse(Optional.of(tag == null ? "" : tag),
                    URI.create(serviceUri), publisherHandle,
                    URI.create(repositoryInfo.siaBase),
                    Optional.of(URI.create(repositoryInfo.rrdpNotificationUri)),
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
