package net.ripe.rpki.server.api.ports;

import lombok.Getter;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;

import java.util.Set;
import java.util.UUID;

public interface NonHostedPublisherRepositoryService {
    boolean isAvailable();

    RepositoryResponse provisionPublisher(UUID publisherHandle, PublisherRequest publisherRequest)
        throws DuplicateRepositoryException;

    Set<UUID> listPublishers();

    void deletePublisher(UUID publisherHandle);

    boolean isInitialized();

    class DuplicateRepositoryException extends Exception {
        @Getter
        private final UUID publisherHandle;

        public DuplicateRepositoryException(UUID publisherHandle) {
            super("duplicate publisher repository '" + publisherHandle + "'");
            this.publisherHandle = publisherHandle;
        }
    }
}
