package net.ripe.rpki.services.impl;

import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.server.api.ports.NonHostedPublisherRepositoryService;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;

@Service
@Profile("test")
public class FakeNonHostedPublisherRepositoryBean implements NonHostedPublisherRepositoryService {
    private final Map<UUID, ImmutablePair<PublisherRequest, RepositoryResponse>> repositories = new TreeMap<>();

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public RepositoryResponse provisionPublisher(UUID publisherHandle, PublisherRequest publisherRequest, String requestId) throws DuplicateRepositoryException {
        if (repositories.containsKey(publisherHandle)) {
            throw new DuplicateRepositoryException(publisherHandle);
        }

        RepositoryResponse repositoryResponse = new RepositoryResponse(
            publisherRequest.getTag(),
            URI.create("https://fake.rpki.example.com/pubserver/").resolve(publisherHandle.toString()),
            publisherHandle.toString(),
            URI.create("rsync://fake.rsync.example.com/repository/").resolve(publisherHandle.toString()),
            Optional.of(URI.create("rrdp://fake.rrdp.example.com/notification.xml")),
            ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT_2
        );

        repositories.put(publisherHandle, ImmutablePair.of(publisherRequest, repositoryResponse));

        return repositoryResponse;
    }

    @Override
    public Set<UUID> listPublishers() {
        return repositories.keySet();
    }

    @Override
    public void deletePublisher(UUID publisherHandle, String requestId) {
        repositories.remove(publisherHandle);
    }

    @Override
    public boolean isInitialized() {
        return true;
    }
}
