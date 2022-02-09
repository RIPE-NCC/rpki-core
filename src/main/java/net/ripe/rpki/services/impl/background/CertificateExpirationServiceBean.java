package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

import javax.inject.Inject;

@Slf4j
@Service("certificateExpirationService")
public class CertificateExpirationServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final ResourceCertificateRepository resourceCertificateRepository;

    private final Counter expiredOutgoingResourceCertificatesCounter;
    private final Counter deletedRoasCounter;
    private final Counter withdrawnObjectsCounter;

    @Inject
    public CertificateExpirationServiceBean(ActiveNodeService propertyService, ResourceCertificateRepository resourceCertificateRepository, MeterRegistry meterRegistry) {
        super(propertyService);
        this.resourceCertificateRepository = resourceCertificateRepository;

        this.expiredOutgoingResourceCertificatesCounter = Counter.builder("rpkicore.expired.outgoing.resource.certificates")
            .description("The number of certificate authorities with pending publications updated")
            .register(meterRegistry);
        this.deletedRoasCounter = Counter.builder("rpkicore.deleted.roas.due.to.expired.certificate")
            .description("The number of ROAs deleted due to the EE certificates expiring")
            .register(meterRegistry);
        this.withdrawnObjectsCounter = Counter.builder("rpkicore.withdrawn.published.objects.due.to.expired.certificate")
            .description("The number of published objects withdrawn due to the EE certificates expiring")
            .register(meterRegistry);
    }

    @Override
    public String getName() {
        return "Certificate Expiration Service";
    }

    @Override
    protected void runService() {
        ResourceCertificateRepository.ExpireOutgoingResourceCertificatesResult counts = resourceCertificateRepository.expireOutgoingResourceCertificates(DateTime.now());

        expiredOutgoingResourceCertificatesCounter.increment(counts.getExpiredCertificateCount());
        deletedRoasCounter.increment(counts.getDeletedRoaCount());
        withdrawnObjectsCounter.increment(counts.getWithdrawnObjectCount());

        log.info(
            "expired {} outgoing resource certificates, deleted {} ROA entities, withdrew {} published objects",
            counts.getExpiredCertificateCount(), counts.getDeletedRoaCount(), counts.getWithdrawnObjectCount()
        );
    }
}
