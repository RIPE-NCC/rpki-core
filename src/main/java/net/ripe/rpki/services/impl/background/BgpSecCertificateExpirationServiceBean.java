package net.ripe.rpki.services.impl.background;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.bgpsec.BgpSecCertificateRepository;
import org.joda.time.DateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

import static net.ripe.rpki.services.impl.background.BackgroundServices.BGPSEC_CERTIFICATE_EXPIRATION_SERVICE;

@Slf4j
@Service(BGPSEC_CERTIFICATE_EXPIRATION_SERVICE)
@ConditionalOnProperty(prefix = "bgpsec", value = "enabled", havingValue = "true")
public class BgpSecCertificateExpirationServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final BgpSecCertificateRepository bgpSecCertificateRepository;
    private final ExpirationCounters expirationCounters;

    @Inject
    public BgpSecCertificateExpirationServiceBean(
            BackgroundTaskRunner backgroundTaskRunner,
            BgpSecCertificateRepository bgpSecCertificateRepository,
            ExpirationCounters expirationCounters
    ) {
        super(backgroundTaskRunner);
        this.bgpSecCertificateRepository = bgpSecCertificateRepository;
        this.expirationCounters = expirationCounters;
    }

    @Override
    public String getName() {
        return "BGPSec certificate expiration service";
    }

    @Override
    protected void runService(Map<String, String> parameters) {
        var counts = bgpSecCertificateRepository.expireOutdatedBgpSecCertificates(DateTime.now());

        expirationCounters.getExpiredBgpSecCertificatesCounter().increment(counts.getExpiredCertificateCount());
        expirationCounters.getWithdrawnObjectsCounter().increment(counts.getWithdrawnObjectCount());

        log.info("Expired {} BGPSec certificates, deleted {} BGPSec entities, withdrew {} published objects",
                counts.getExpiredCertificateCount(), counts.getDeletedBgpSecCount(), counts.getWithdrawnObjectCount());
    }
}

