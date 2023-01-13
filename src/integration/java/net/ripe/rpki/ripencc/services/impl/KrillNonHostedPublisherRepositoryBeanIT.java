package net.ripe.rpki.ripencc.services.impl;

import com.google.common.io.Resources;
import lombok.SneakyThrows;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequestSerializer;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.server.api.ports.NonHostedPublisherRepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class KrillNonHostedPublisherRepositoryBeanIT {

    @Inject
    private NonHostedPublisherRepositoryService subject;

    private PublisherRequest publisherRequest;

    @BeforeEach
    public void init() {
        String requestXML = readFromFile("/repository-publisher/publisher_request.xml");
        publisherRequest = new PublisherRequestSerializer().deserialize(requestXML);
    }

    @Test
    public void shouldRegisterPublisher() throws NonHostedPublisherRepositoryService.DuplicateRepositoryException {
        UUID publisherHandle = UUID.randomUUID();
        RepositoryResponse repositoryResponse = subject.provisionPublisher(publisherHandle, publisherRequest);
        assertThat(publisherHandle.toString()).isEqualTo(repositoryResponse.getPublisherHandle());
    }

    @Test
    public void shouldListRegisteredPublishers() throws NonHostedPublisherRepositoryService.DuplicateRepositoryException {
        UUID publisherHandle = UUID.randomUUID();
        subject.provisionPublisher(publisherHandle, publisherRequest);

        Set<UUID> publishers = subject.listPublishers();
        assertThat(publishers.contains(publisherHandle)).isTrue();
    }

    @Test
    public void shouldDeleteRegisteredPublisher() {
        UUID[] uuids = new UUID[]{UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        Set<UUID> pubs = Stream.of(uuids).collect(Collectors.toSet());
        pubs.forEach(uuid -> {
            try {
                subject.provisionPublisher(uuid, publisherRequest);
            } catch (NonHostedPublisherRepositoryService.DuplicateRepositoryException e) {
                throw new RuntimeException(e);
            }
        });

        Set<UUID> createdPublishers = subject.listPublishers();
        assertThat(createdPublishers.containsAll(pubs)).isTrue();

        pubs.forEach(uuid -> subject.deletePublisher(uuid));

        Set<UUID> publishers = subject.listPublishers();
        assertThat(publishers.stream().noneMatch(pubs::contains)).isTrue();
    }

    @SneakyThrows
    private String readFromFile(String resourcePath) {
        final Resource resource = new ClassPathResource(resourcePath);
        return Resources.toString(resource.getURL(), StandardCharsets.UTF_8);
    }
}
