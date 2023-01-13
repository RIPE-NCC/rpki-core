package net.ripe.rpki.services.impl.background;

import com.google.common.collect.Sets;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.ripencc.services.impl.KrillNonHostedPublisherRepositoryBean;
import net.ripe.rpki.server.api.ports.NonHostedPublisherRepositoryService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
        Map<UUID, PublisherRequest> corePublisherRequests = certificateAuthorityViewService.findAllPublisherRequestsFromNonHostedCAs();

        Set<UUID> corePublisherHandles = corePublisherRequests.keySet();
        Set<UUID> krillPublisherHandles = nonHostedPublisherRepositoryService.listPublishers();

        Set<UUID> onlyOnCore = Sets.difference(corePublisherHandles, krillPublisherHandles);
        syncOnlyOnCoreCounter.increment(onlyOnCore.size());
        for (UUID publisherHandle : onlyOnCore) {
            PublisherRequest request = corePublisherRequests.get(publisherHandle);
            log.info("Reprovisioning publisher handle only on Core: {}", publisherHandle);
            try {
                nonHostedPublisherRepositoryService.provisionPublisher(publisherHandle, request);
            } catch (NonHostedPublisherRepositoryService.DuplicateRepositoryException e) {
                // should not happen since we only re-provision missing repositories, but ignore since we
                // consider this an idem-potent operation.
                log.warn("Duplicate repository '{}' while re-provisioning", publisherHandle);
            }
        }

        Set<UUID> onlyOnKrill = Sets.difference(krillPublisherHandles, corePublisherHandles);
        syncOnlyOnKrillCounter.increment(onlyOnCore.size());
        for (UUID publisherHandle : onlyOnKrill) {
            log.info("Cleaning up publisher handle only on Krill: {}", publisherHandle);
            nonHostedPublisherRepositoryService.deletePublisher(publisherHandle);
        }
    }
}
