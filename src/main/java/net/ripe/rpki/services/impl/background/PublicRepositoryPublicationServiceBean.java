package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.UTC;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.TrustAnchorPublishedObjectRepository;
import net.ripe.rpki.domain.manifest.ManifestEntity;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import java.util.Collection;

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

    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final CertificateManagementService certificateManagementService;
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
        PublishedObjectRepository publishedObjectRepository,
        TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository,
        EntityManager entityManager,
        PlatformTransactionManager transactionManager,
        MeterRegistry meterRegistry) {
        super(propertyService);
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.certificateManagementService = certificateManagementService;
        this.publishedObjectRepository = publishedObjectRepository;
        this.trustAnchorPublishedObjectRepository = trustAnchorPublishedObjectRepository;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // Repeatable read so we get a consistent snapshot of to-be-published objects
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        this.certificateAuthorityCounter = Counter.builder("rpkicore.publication")
            .description("The number of certificate authorities with pending publications updated")
            .tag("publication", "update")
            .register(meterRegistry);
        this.publishedObjectCounter = Counter.builder("rpkicore.publication")
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
        Collection<HostedCertificateAuthority> pendingCertificateAuthorities = certificateAuthorityRepository.findAllWithOutdatedManifests(
            UTC.dateTime().plus(ManifestEntity.TIME_TO_NEXT_UPDATE_HARD_LIMIT)
        );

        certificateAuthorityCounter.increment(pendingCertificateAuthorities.size());
        log.info("Checking {} CAs for manifest updates with publishable objects", pendingCertificateAuthorities.size());

        // When batch processing many JPA entities we need to keep the JPA session small. Otherwise checking
        // for modified objects before every JPA query becomes very slow (quadratic behavior as the session
        // grows in size). Not doing this can increase the runtime of this services to many hours!
        entityManager.clear();

        Instant timeout = null;
        long updateCountTotal = 0;
        for (HostedCertificateAuthority ca : pendingCertificateAuthorities) {
            // Associate with JPA session
            ca = entityManager.merge(ca);
            // Generate new CRL and manifest if needed
            long updateCount = certificateManagementService.updateManifestAndCrlIfNeeded(ca);
            if (updateCount > 0) {
                // The manifest and CRL are now up-to-date and the CA is locked, so we clear the check needed flag.
                ca.manifestAndCrlCheckCompleted();
            }

            updateCountTotal += updateCount;

            // Only after an actual update do we hold locks. To limit the locking duration we set a timeout
            // on the first updated CA.
            if (timeout == null && updateCountTotal > 0) {
                timeout = Instant.now().plus(Duration.standardSeconds(10));
            }

            entityManager.flush();
            entityManager.clear();

            if (timeout != null && timeout.isBeforeNow()) {
                // Process is taking too long, commit current results and wait for next run to process further CAs.
                log.info("Updated {} manifests before running out of time, continuing during next run", updateCountTotal);
                return;
            }
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
