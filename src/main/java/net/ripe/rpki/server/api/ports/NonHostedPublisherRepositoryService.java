package net.ripe.rpki.server.api.ports;

import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;

import javax.ws.rs.core.Response;
import java.util.Set;
import java.util.UUID;

public interface NonHostedPublisherRepositoryService {
    boolean isAvailable();

    RepositoryResponse provisionPublisher(UUID publisherHandle, PublisherRequest publisherRequest);

    Set<UUID> listPublishers();

    Response deletePublisher(UUID publisherHandle);

    boolean isInitialized();
}