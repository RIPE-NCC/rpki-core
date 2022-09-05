package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.UTC;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.TrustAnchorPublishedObjectRepository;
import net.ripe.rpki.domain.manifest.ManifestEntity;
import net.ripe.rpki.domain.roa.RoaEntityService;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import java.util.Comparator;
import java.util.List;
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
     * The maximum number of CAs to update the manifest and CRL for. If there are more than this number of CAs
     * that need a check they will be picked up during the next run or processed by the
     * {@link ManifestCrlUpdateServiceBean}.
     */
    public static final int MAX_CERTIFICATE_AUTHORITIES_TO_UPDATE = 50;

    /**
     * The maximum time this service should lock certificate authorities while checking for manifest/CRL update.
     * If there are more CAs that need a check they will be picked up during the next run or processed by the
     * {@link ManifestCrlUpdateServiceBean}.
     */
    public static final Duration MAX_UPDATE_DURATION = Duration.standardSeconds(5);

    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final CertificateManagementService certificateManagementService;
    private final RoaEntityService roaEntityService;
    private final PublishedObjectRepository publishedObjectRepository;
    private final TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    private final Counter certificateAuthorityCounter;
    private final Counter publishedObjectCounter;

    public PublicRepositoryPublicationServiceBean(
        ActiveNodeService propertyService,
        CertificateAuthorityRepository certificateAuthorityRepository,
        CertificateManagementService certificateManagementService,
        RoaEntityService roaEntityService,
        PublishedObjectRepository publishedObjectRepository,
        TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository,
        EntityManager entityManager,
        PlatformTransactionManager transactionManager,
        MeterRegistry meterRegistry) {
        super(propertyService);
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.certificateManagementService = certificateManagementService;
        this.roaEntityService = roaEntityService;
        this.publishedObjectRepository = publishedObjectRepository;
        this.trustAnchorPublishedObjectRepository = trustAnchorPublishedObjectRepository;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // Repeatable read so we get a consistent snapshot of to-be-published objects
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
    protected void runService() {
        try {
            transactionTemplate.executeWithoutResult((status) -> runTransaction());
        } catch (TransientDataAccessException e) {
            // The transaction runs with repeatable read isolation level which may cause transient exceptions due
            // to data access ordering. See https://www.postgresql.org/docs/current/transaction-iso.html for
            // more information.
            log.warn("transaction rolled back due to TransientDataAccessException, will be retried during next run. Exception: {}", e.toString());
        }
    }

    private void runTransaction() {
        DateTime manifestAndCrlValidityCutoff = UTC.dateTime().plus(ManifestEntity.TIME_TO_NEXT_UPDATE_HARD_LIMIT);

        // List of certificate authorities that may need a new manifest/CRL. Children are sorted before parents
        // to ensure proper locking order (command handlers always lock the child CA before the parent CA). This
        // reduces the chance of deadlock or a serializable transaction rollback error.
        List<ManagedCertificateAuthority> pendingCertificateAuthorities =
            certificateAuthorityRepository.findAllWithOutdatedManifests(manifestAndCrlValidityCutoff, MAX_CERTIFICATE_AUTHORITIES_TO_UPDATE)
                .stream()
                .sorted(Comparator.comparingInt(CertificateAuthority::depth).reversed())
                .collect(Collectors.toList());

        certificateAuthorityCounter.increment(pendingCertificateAuthorities.size());

        Instant timeout = Instant.now().plus(MAX_UPDATE_DURATION);
        log.info("Updating {} CAs with outdated manifest or CRL", pendingCertificateAuthorities.size());

        long updateCountTotal = 0;
        for (ManagedCertificateAuthority ca : pendingCertificateAuthorities) {
            entityManager.lock(ca, LockModeType.PESSIMISTIC_WRITE);
            roaEntityService.updateRoasIfNeeded(ca);
            updateCountTotal += certificateManagementService.updateManifestAndCrlIfNeeded(ca);
            // The manifest and CRL are now up-to-date and the CA is locked, so we clear the check needed flag.
            ca.manifestAndCrlCheckCompleted();

            if (timeout.isBeforeNow()) {
                // Process is taking too long, commit current results and wait for next run to process further CAs.
                log.info("Updated {} manifests before running out of time, continuing during next run", updateCountTotal);
                return;
            }
        }

        if (!certificateAuthorityRepository.findAllWithOutdatedManifests(manifestAndCrlValidityCutoff, 1).isEmpty()) {
            log.info("Not all certificate authorities are ready for publication, continuing during next run");
            return;
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
}
