package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@Getter
public class ExpirationCounters {

    private final Counter expiredOutgoingResourceCertificatesCounter;
    private final Counter deletedRoasCounter;
    private final Counter deletedAspasCounter;
    private final Counter withdrawnObjectsCounter;
    private final Counter expiredBgpSecCertificatesCounter;
    private final Counter deletedBgpSecCertificatesCounter;

    public ExpirationCounters(MeterRegistry meterRegistry) {
        this.expiredOutgoingResourceCertificatesCounter = Counter.builder("rpkicore.expired.outgoing.resource.certificates")
                .description("The number of certificate authorities with pending publications updated")
                .register(meterRegistry);
        this.deletedRoasCounter = Counter.builder("rpkicore.deleted.roas.due.to.expired.certificate")
                .description("The number of ROAs deleted due to the EE certificates expiring")
                .register(meterRegistry);
        this.deletedAspasCounter = Counter.builder("rpkicore.deleted.aspas.due.to.expired.certificate")
                .description("The number of ASPAs deleted due to the EE certificates expiring")
                .register(meterRegistry);
        this.withdrawnObjectsCounter = Counter.builder("rpkicore.withdrawn.published.objects.due.to.expired.certificate")
                .description("The number of published objects withdrawn due to the EE certificates expiring")
                .register(meterRegistry);
        this.expiredBgpSecCertificatesCounter = Counter.builder("rpkicore.expired.bgpsec.certificates")
                .description("Number of expired BGPSec certificates")
                .register(meterRegistry);
        this.deletedBgpSecCertificatesCounter = Counter.builder("rpkicore.deleted.bgpsec.certificates")
                .description("Number of BGPSec certificate rows deleted after the grace period")
                .register(meterRegistry);
    }
}
