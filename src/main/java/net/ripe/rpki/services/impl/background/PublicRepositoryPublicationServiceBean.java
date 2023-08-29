package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.UTC;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.manifest.ManifestEntity;
import net.ripe.rpki.server.api.commands.IssueUpdatedManifestAndCrlCommand;
import net.ripe.rpki.server.api.services.command.CommandService;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.inject.Inject;
import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

import static net.ripe.rpki.services.impl.background.BackgroundServices.PUBLIC_REPOSITORY_PUBLICATION_SERVICE;

/**
 * Updates all needed CRLs and manifests to generate a new, consistent set of published RPKI objects. The update
 * is done inside a single transaction to ensure that no inconsistent set of objects can be published.
 * <p>
 * The actual publishing to RRDP or RSYNC is done in separate background services.
 */
@Service(PUBLIC_REPOSITORY_PUBLICATION_SERVICE)
@Slf4j
public class PublicRepositoryPublicationServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final CommandService commandService;
    private final CertificateAuthorityRepository certificateAuthorityRepository;

    private final TransactionTemplate transactionTemplate;
    private final PublishedObjectRepository publishedObjectRepository;
    private final TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository;
    private final Counter certificateAuthorityCounter;

    @Inject
    public PublicRepositoryPublicationServiceBean(
        BackgroundTaskRunner backgroundTaskRunner,
        CommandService commandService,
        CertificateAuthorityRepository certificateAuthorityRepository,
        TransactionTemplate transactionTemplate, PublishedObjectRepository publishedObjectRepository, TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository,
        MeterRegistry meterRegistry) {
        super(backgroundTaskRunner);
        this.commandService = commandService;
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.transactionTemplate = transactionTemplate;
        this.publishedObjectRepository = publishedObjectRepository;
        this.trustAnchorPublishedObjectRepository = trustAnchorPublishedObjectRepository;

        this.certificateAuthorityCounter = Counter.builder("rpkicore.publication.certificate.authorities")
            .description("The number of certificate authorities with pending publications updated")
            .tag("publication", "update")
            .register(meterRegistry);
    }

    @Override
    public String getName() {
        return "Public Repository Publication Service";
    }

    @Override
    protected void runService(Map<String, String> parameters) {
        // Avoid generating a delta on each run by truncating the cutoff validity time to a full hour. If there are
        // ASPA/ROA objects to be published a CA will be immediately published, otherwise an hour boundary must be
        // crossed. This reduces the number of generated RRDP deltas.
        DateTime manifestAndCrlValidityCutoff = UTC.dateTime().plus(ManifestEntity.TIME_TO_NEXT_UPDATE_SOFT_LIMIT)
            .withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0);
        transactionTemplate.executeWithoutResult(status -> trustAnchorPublishedObjectRepository.updatePublicationStatus());
        publishPendingCertificateAuthorities(manifestAndCrlValidityCutoff);
        transactionTemplate.executeWithoutResult(status -> publishedObjectRepository.withdrawObjectsForDeletedKeys());
    }

    private void publishPendingCertificateAuthorities(DateTime manifestAndCrlValidityCutoff) {
        Collection<ManagedCertificateAuthority> pendingCertificateAuthorities =
            certificateAuthorityRepository.findAllWithOutdatedManifests(true, manifestAndCrlValidityCutoff, Integer.MAX_VALUE);
        log.info("Publishing {} CAs with updated configuration or outdated manifest/CRL", pendingCertificateAuthorities.size());
        certificateAuthorityCounter.increment(pendingCertificateAuthorities.size());

        SortedMap<Integer, List<ManagedCertificateAuthority>> groupedByDepth =
            pendingCertificateAuthorities.stream()
                .collect(Collectors.groupingBy(CertificateAuthority::depth, TreeMap::new, Collectors.toList()));

        // Publish top-down to ensure the parent CA's certificates are always available before publishing child CA
        // certificates. Otherwise, a child CA certificate may be invalid due to over-claiming resources.
        for (List<ManagedCertificateAuthority> cas : groupedByDepth.values()) {
            runParallel(cas.stream().map(ca -> task(
                () -> commandService.execute(new IssueUpdatedManifestAndCrlCommand(ca.getVersionedId())),
                ex -> {
                    if (ex instanceof EntityNotFoundException) {
                        log.info("CA '{}' not found, probably deleted since initial query", ca.getName(), ex);
                    } else {
                        log.error("Could not publish material for CA '{}'", ca.getName(), ex);
                    }
                }
            )));
        }
    }
}
