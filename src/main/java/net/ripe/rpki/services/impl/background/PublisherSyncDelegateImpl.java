package net.ripe.rpki.services.impl.background;

import com.google.common.collect.Sets;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.NonHostedPublisherRepository;
import net.ripe.rpki.ripencc.services.impl.KrillNonHostedPublisherRepositoryBean;
import net.ripe.rpki.server.api.ports.NonHostedPublisherRepositoryService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import jakarta.inject.Inject;
import javax.security.auth.x500.X500Principal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnBean(KrillNonHostedPublisherRepositoryBean.class)
public class PublisherSyncDelegateImpl implements PublisherSyncDelegate {
    private final CertificateAuthorityViewService certificateAuthorityViewService;
    private final NonHostedPublisherRepositoryService nonHostedPublisherRepositoryService;

    private final Counter syncOnlyOnCoreCounter;
    private final Counter syncOnlyOnKrillCounter;

    @Inject
    public PublisherSyncDelegateImpl(
            CertificateAuthorityViewService certificateAuthorityViewService,
            NonHostedPublisherRepositoryService nonHostedPublisherRepositoryService,
            MeterRegistry meterRegistry){

        this.certificateAuthorityViewService = certificateAuthorityViewService;
        this.nonHostedPublisherRepositoryService = nonHostedPublisherRepositoryService;
        this.syncOnlyOnCoreCounter = Counter.builder("rpkicore.publishers.only.on.core")
                .description("The number publishers only on core that are provisioned on sync")
                .register(meterRegistry);

        this.syncOnlyOnKrillCounter = Counter.builder("rpkicore.publisher.only.on.krill")
                .description("The number publishers only on krill that are deleted on sync")
                .register(meterRegistry);
    }

    @Override
    public void runService() {
        List<NonHostedPublisherRepository> corePublisherRepositories = certificateAuthorityViewService.findAllPublisherRequestsFromNonHostedCAs();

        Set<UUID> corePublisherHandles = corePublisherRepositories.stream()
                .map(NonHostedPublisherRepository::getPublisherHandle)
                .collect(Collectors.toSet());
        Set<UUID> krillPublisherHandles = nonHostedPublisherRepositoryService.listPublishers();

        List<NonHostedPublisherRepository> onlyOnCore = corePublisherRepositories.stream()
                .filter(repository -> !krillPublisherHandles.contains(repository.getPublisherHandle()))
                .toList();

        Set<UUID> onlyOnKrill = Sets.difference(krillPublisherHandles, corePublisherHandles);

        log.info("There are {} publisher handles only on Core and {} publisher handles only on Krill", onlyOnCore.size(), onlyOnKrill.size());
        syncOnlyOnCoreCounter.increment(onlyOnCore.size());
        for (NonHostedPublisherRepository repository : onlyOnCore) {
            UUID publisherHandle = repository.getPublisherHandle();
            X500Principal caName = repository.getCertificateAuthority().getName();
            log.info("Reprovisioning publisher handle only on Core: {} for CA '{}'", publisherHandle, caName);
            try {
                nonHostedPublisherRepositoryService.provisionPublisher(publisherHandle, repository.getPublisherRequest());
            } catch (NonHostedPublisherRepositoryService.DuplicateRepositoryException e) {
                // should not happen since we only re-provision missing repositories, but ignore since we
                // consider this an idem-potent operation.
                log.warn("Duplicate repository '{}' for CA '{}' while re-provisioning", publisherHandle, caName);
            }
        }

        syncOnlyOnKrillCounter.increment(onlyOnCore.size());
        for (UUID publisherHandle : onlyOnKrill) {
            log.info("Cleaning up publisher handle only on Krill: {}", publisherHandle);
            nonHostedPublisherRepositoryService.deletePublisher(publisherHandle);
        }
    }
}
