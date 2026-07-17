package net.ripe.rpki.services.impl.background;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

import java.util.Map;

import static net.ripe.rpki.services.impl.background.BackgroundServices.CERTIFICATE_EXPIRATION_SERVICE;

@Slf4j
@Service(CERTIFICATE_EXPIRATION_SERVICE)
public class CertificateExpirationServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final ResourceCertificateRepository resourceCertificateRepository;
    private final ExpirationCounters expirationCounters;

    @Inject
    public CertificateExpirationServiceBean(
            BackgroundTaskRunner backgroundTaskRunner,
            ResourceCertificateRepository resourceCertificateRepository,
            ExpirationCounters expirationCounters
    ) {
        super(backgroundTaskRunner);
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.expirationCounters = expirationCounters;
    }

    @Override
    public String getName() {
        return "Certificate Expiration Service";
    }

    @Override
    protected void runService(Map<String, String> parameters) {
        var counts = resourceCertificateRepository.expireOutgoingResourceCertificates(DateTime.now());
        expirationCounters.getExpiredOutgoingResourceCertificatesCounter().increment(counts.getExpiredCertificateCount());
        expirationCounters.getDeletedRoasCounter().increment(counts.getDeletedRoaCount());
        expirationCounters.getDeletedAspasCounter().increment(counts.getDeletedAspaCount());
        expirationCounters.getWithdrawnObjectsCounter().increment(counts.getWithdrawnObjectCount());

        log.info(
                "expired {} outgoing resource certificates, deleted {} ROA entities, deleted {} ASPA entities, withdrew {} published objects",
                counts.getExpiredCertificateCount(), counts.getDeletedRoaCount(), counts.getDeletedAspaCount(), counts.getWithdrawnObjectCount()
        );
    }
}
