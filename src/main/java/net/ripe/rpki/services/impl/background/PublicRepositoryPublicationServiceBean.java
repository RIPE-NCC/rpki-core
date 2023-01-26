package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.UTC;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.TrustAnchorPublishedObjectRepository;
import net.ripe.rpki.domain.manifest.ManifestEntity;
import net.ripe.rpki.domain.manifest.ManifestPublicationService;
import net.ripe.rpki.server.api.commands.IssueUpdatedManifestAndCrlCommand;
import net.ripe.rpki.server.api.services.command.CommandService;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.LockModeType;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    /**
     * The maximum number of CAs to update the manifest and CRL for during the publication transaction. If there are
     * more than this number of CAs that need a check they will be picked up during the next run or processed by the
     * {@link ManifestCrlUpdateServiceBean}.
     */
    public static final int MAX_CERTIFICATE_AUTHORITIES_TO_UPDATE = 5;

    /**
     * The maximum time this service should lock certificate authorities while checking for manifest/CRL update.
     * If there are more CAs that need a check they will be picked up during the next run or processed by the
     * {@link ManifestCrlUpdateServiceBean}.
     */
    public static final Duration MAX_UPDATE_DURATION = Duration.standardSeconds(5);

    private final CommandService commandService;
    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final ManifestPublicationService manifestPublicationService;
    private final PublishedObjectRepository publishedObjectRepository;
    private final TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    private final Counter certificateAuthorityCounter;
    private final Counter publishedObjectCounter;

    @Inject
    public PublicRepositoryPublicationServiceBean(
        BackgroundTaskRunner backgroundTaskRunner,
        CommandService commandService,
        CertificateAuthorityRepository certificateAuthorityRepository,
        ManifestPublicationService manifestPublicationService,
        PublishedObjectRepository publishedObjectRepository,
        TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository,
        EntityManager entityManager,
        PlatformTransactionManager transactionManager,
        MeterRegistry meterRegistry) {
        super(backgroundTaskRunner);
        this.commandService = commandService;
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.manifestPublicationService = manifestPublicationService;
        this.publishedObjectRepository = publishedObjectRepository;
        this.trustAnchorPublishedObjectRepository = trustAnchorPublishedObjectRepository;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // Repeatable read, so we get a consistent snapshot of to-be-published objects
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        this.certificateAuthorityCounter = Counter.builder("rpkicore.publication.certificate.authorities")
            .description("The number of certificate authorities with pending publications updated")
            .tag("publication", "update")
            .register(meterRegistry);
        this.publishedObjectCounter = Counter.builder("rpkicore.publication.published.objects")
            .description("The number of published objects marked as published or withdrawn")
            .tag("publication", "update")
            .register(meterRegistry);
    }

    @Override
    public String getName() {
        return "Public Repository Publication Service";
    }

    @Override
    protected void runService(Map<String, String> parameters) {
        DateTime manifestAndCrlValidityCutoff = UTC.dateTime().plus(ManifestEntity.TIME_TO_NEXT_UPDATE_HARD_LIMIT);
        preparePendingCertificateAuthorities(manifestAndCrlValidityCutoff);
        publishPendingCertificateAuthorities(manifestAndCrlValidityCutoff);
    }

    private void preparePendingCertificateAuthorities(DateTime manifestAndCrlValidityCutoff) {
        List<ManagedCertificateAuthority> pendingCertificateAuthorities = findPendingCertificateAuthorities(manifestAndCrlValidityCutoff, Integer.MAX_VALUE);
        log.info("Updating {} CAs with outdated manifest or CRL before publication transaction", pendingCertificateAuthorities.size());

        runParallel(pendingCertificateAuthorities.stream().map(ca -> task(
            () -> commandService.execute(new IssueUpdatedManifestAndCrlCommand(ca.getVersionedId())),
            ex -> {
                if (ex instanceof EntityNotFoundException) {
                    log.info("CA '{}' not found, probably deleted since initial query", ca.getName(), ex);
                } else {
                    log.error("Could not publish material for CA " + ca.getName(), ex);
                }
            }
        )));
    }

    private void publishPendingCertificateAuthorities(DateTime manifestAndCrlValidityCutoff) {
        try {
            transactionTemplate.executeWithoutResult(status -> runTransaction(manifestAndCrlValidityCutoff));
        } catch (TransientDataAccessException e) {
            // The transaction runs with repeatable read isolation level which may cause transient exceptions due
            // to data access ordering. See https://www.postgresql.org/docs/current/transaction-iso.html for
            // more information.
            log.warn("transaction rolled back due to TransientDataAccessException, will be retried during next run. Exception: {}", e.toString());
        }
    }

    private void runTransaction(DateTime manifestAndCrlValidityCutoff) {
        log.info("Started publication transaction");

        List<ManagedCertificateAuthority> pendingCertificateAuthorities =
            findPendingCertificateAuthorities(manifestAndCrlValidityCutoff, MAX_CERTIFICATE_AUTHORITIES_TO_UPDATE + 1);
        if (pendingCertificateAuthorities.size() > MAX_CERTIFICATE_AUTHORITIES_TO_UPDATE) {
            log.info("More than {} CAs to update, aborting publication", MAX_CERTIFICATE_AUTHORITIES_TO_UPDATE);
            return;
        }

        certificateAuthorityCounter.increment(pendingCertificateAuthorities.size());

        Instant timeout = Instant.now().plus(MAX_UPDATE_DURATION);
        log.info("Updating {} CAs with outdated manifest or CRL", pendingCertificateAuthorities.size());

        long updateCountTotal = 0;
        for (ManagedCertificateAuthority ca : pendingCertificateAuthorities) {
            if (timeout.isBeforeNow()) {
                // Process is taking too long, commit current results and wait for next run to process further CAs.
                log.info("Updated {} manifests before running out of time, continuing during next run", updateCountTotal);
                return;
            }

            entityManager.lock(ca, LockModeType.PESSIMISTIC_WRITE);
            updateCountTotal += manifestPublicationService.updateManifestAndCrlIfNeeded(ca);
        }

        // Atomically mark the new set of objects that are publishable.
        int count = publishedObjectRepository.updatePublicationStatus();

        // The trust anchor published object table is already managed atomically by the
        // TrustAnchorResponseProcessor, so here we just mark the new set of objects to
        // be published.
        count += trustAnchorPublishedObjectRepository.updatePublicationStatus();

        if (count > 0) {
            log.info(
                "Published/withdrawn {} objects after updating {} manifests while checking {} CAs",
                count, updateCountTotal, pendingCertificateAuthorities.size()
            );
        }

        publishedObjectCounter.increment(count);
    }

    /**
     * List of certificate authorities that may need a new manifest/CRL. Children are sorted before parents
     * to ensure proper locking order (command handlers always lock the child CA before the parent CA). This
     * reduces the chance of deadlock or a serializable transaction rollback error.
     */
    private List<ManagedCertificateAuthority> findPendingCertificateAuthorities(DateTime manifestAndCrlValidityCutoff, int maxResults) {
        return certificateAuthorityRepository.findAllWithOutdatedManifests(manifestAndCrlValidityCutoff, maxResults)
            .stream()
            .sorted(Comparator.comparingInt(CertificateAuthority::depth).reversed())
            .collect(Collectors.toList());
    }
}
