package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.ripe.rpki.core.services.background.ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.inject.Inject;

@Service("publishedObjectCleanUpService")
public class PublishedObjectCleanUpServiceBean extends ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private static final Logger LOG = LoggerFactory.getLogger(PublishedObjectCleanUpServiceBean.class);

    private final TransactionTemplate transactionTemplate;
    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final PublishedObjectRepository publishedObjectRepository;
    private final ResourceCertificateRepository resourceCertificateRepository;
    private final Counter deletedNonHostedPublicKeysCounter;

    private int daysBeforeCleanUp = 2;

    @Inject
    public PublishedObjectCleanUpServiceBean(ActiveNodeService activeNodeService,
                                             CertificateAuthorityRepository certificateAuthorityRepository,
                                             PublishedObjectRepository publishedObjectRepository,
                                             ResourceCertificateRepository resourceCertificateRepository,
                                             PlatformTransactionManager transactionManager,
                                             MeterRegistry meterRegistry) {
        super(activeNodeService);
        this.publishedObjectRepository = publishedObjectRepository;
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);

        this.deletedNonHostedPublicKeysCounter = Counter.builder("rpkicore.deleted.non.hosted.public.keys.without.signing.certificate")
            .description("The number of deleted non-hosted public keys without signing certificate")
            .register(meterRegistry);

    }

    @Override
    protected void runService() {
        DateTime expirationTime = new DateTime(DateTimeZone.UTC).minusDays(daysBeforeCleanUp);
        transactionTemplate.executeWithoutResult((status) -> {
            int certificateCount = resourceCertificateRepository.deleteExpiredOutgoingResourceCertificates(expirationTime);
            LOG.info("Deleted {} expired certificates with not valid after before {}", certificateCount, expirationTime);

            int publishedObjectCount = publishedObjectRepository.deleteExpiredObjects(expirationTime);
            LOG.info("Deleted {} withdrawn published objects with not valid after before {}", publishedObjectCount, expirationTime);

            int deletedNonHostedPublicKeyCount = certificateAuthorityRepository.deleteNonHostedPublicKeysWithoutSigningCertificates();
            deletedNonHostedPublicKeysCounter.increment(deletedNonHostedPublicKeyCount);
            LOG.info("Deleted {} non-hosted public keys without signing certificates", deletedNonHostedPublicKeyCount);
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
