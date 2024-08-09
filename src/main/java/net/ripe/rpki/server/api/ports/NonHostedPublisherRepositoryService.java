package net.ripe.rpki.server.api.ports;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;


public interface NonHostedPublisherRepositoryService {
    boolean isAvailable();

    RepositoryResponse provisionPublisher(UUID publisherHandle, PublisherRequest publisherRequest, String requestId)
        throws DuplicateRepositoryException;

    default RepositoryResponse provisionPublisher(UUID publisherHandle, PublisherRequest publisherRequest) throws DuplicateRepositoryException {
        return provisionPublisher(publisherHandle, publisherRequest, null);
    }

    Set<UUID> listPublishers();

    void deletePublisher(UUID publisherHandle, String requestId);

    default void deletePublisher(UUID publisherHandle) {
        deletePublisher(publisherHandle, null);
    }

    boolean isInitialized();

    Optional<Publisher> publisherInfo(UUID publisherHandle);

    class DuplicateRepositoryException extends Exception {
        @Getter
        private final UUID publisherHandle;

        public DuplicateRepositoryException(UUID publisherHandle) {
            super("duplicate publisher repository '" + publisherHandle + "'");
            this.publisherHandle = publisherHandle;
        }
    }

    // https://krill.docs.nlnetlabs.nl/en/stable/publication-server.html#show-a-publisher
    @Data
    @With
    @AllArgsConstructor
    class Publisher {
        String handle;
        @JsonProperty("id_cert")
        IdCert idCert;
        @JsonProperty("base_uri")
        String baseUri;
        @JsonProperty("current_files")
        List<PublisherFile> currentFiles;
        Instant lastUpdate;
    }

    @Value
    class IdCert {
        @JsonProperty("public_key")
        String publicKey;
        String base64;
        String hash;
    }

    @Value
    class PublisherFile {
        String base64;
        String uri;
    }
}
