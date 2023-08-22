package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.inject.Inject;

import java.util.Map;

import static net.ripe.rpki.services.impl.background.BackgroundServices.PUBLISHED_OBJECT_CLEAN_UP_SERVICE;

@Slf4j
@Service(PUBLISHED_OBJECT_CLEAN_UP_SERVICE)
public class PublishedObjectCleanUpServiceBean extends ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final TransactionTemplate transactionTemplate;
    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final PublishedObjectRepository publishedObjectRepository;
    private final ResourceCertificateRepository resourceCertificateRepository;
    private final Counter deletedNonHostedPublicKeysCounter;

    private int daysBeforeCleanUp = 2;

    @Inject
    public PublishedObjectCleanUpServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                             CertificateAuthorityRepository certificateAuthorityRepository,
                                             PublishedObjectRepository publishedObjectRepository,
                                             ResourceCertificateRepository resourceCertificateRepository,
                                             PlatformTransactionManager transactionManager,
                                             MeterRegistry meterRegistry) {
        super(backgroundTaskRunner);
        this.publishedObjectRepository = publishedObjectRepository;
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);

        this.deletedNonHostedPublicKeysCounter = Counter.builder("rpkicore.deleted.non.hosted.public.keys.without.signing.cert")
            .description("The number of deleted non-hosted public keys without signing certificate")
            .register(meterRegistry);
    }

    @Override
    protected void runService(Map<String, String> parameters) {
        DateTime expirationTime = new DateTime(DateTimeZone.UTC).minusDays(daysBeforeCleanUp);
        transactionTemplate.executeWithoutResult((status) -> {
            int certificateCount = resourceCertificateRepository.deleteExpiredOutgoingResourceCertificates(expirationTime);
            log.info("Deleted {} expired certificates with not valid after before {}", certificateCount, expirationTime);

            int publishedObjectCount = publishedObjectRepository.deleteExpiredObjects(expirationTime);
            log.info("Deleted {} withdrawn published objects with not valid after before {}", publishedObjectCount, expirationTime);

            int deletedNonHostedPublicKeyCount = certificateAuthorityRepository.deleteNonHostedPublicKeysWithoutSigningCertificates();
            deletedNonHostedPublicKeysCounter.increment(deletedNonHostedPublicKeyCount);
            log.info("Deleted {} non-hosted public keys without signing certificates", deletedNonHostedPublicKeyCount);
        });
    }

    @Override
    public String getName() {
        return "Published Object clean up service";
    }

    public void setDaysBeforeCleanUp(int daysBeforeCleanUp) {
        this.daysBeforeCleanUp = daysBeforeCleanUp;
    }
}
