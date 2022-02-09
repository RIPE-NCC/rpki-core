package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RoaMetricsService {

    private static final String VRP_COUNT_METRIC = "rpkicore.vrp.count";
    private static final String VRP_COUNT_METRIC_DESCRIPTION = "Number of current VRPs published";

    private final static String ROA_UPDATE_METRICS = "rpkicore.roas.update";
    private final static String ROA_UPDATE_DESCRIPTION = "Accumulative counter of roas added or deleted";

    private final Counter roaAddCounter;
    private final Counter roaDeleteCounter;

    @Autowired
    public RoaMetricsService(MeterRegistry meterRegistry,
                             RoaConfigurationRepository roaConfigurationRepository) {
        Gauge.builder(VRP_COUNT_METRIC, roaConfigurationRepository, RoaConfigurationRepository::countRoaPrefixes)
                .description(VRP_COUNT_METRIC_DESCRIPTION)
                .register(meterRegistry);

        roaAddCounter = Counter.builder(ROA_UPDATE_METRICS)
                .description(ROA_UPDATE_DESCRIPTION)
                .tags("operation", "added")
                .register(meterRegistry);

        roaDeleteCounter = Counter.builder(ROA_UPDATE_METRICS)
                .description(ROA_UPDATE_DESCRIPTION)
                .tags("operation", "deleted")
                .register(meterRegistry);
    }

    public void countAdded(int added) {
        roaAddCounter.increment(added);
    }

    public void countDeleted(int deleted) {
        roaDeleteCounter.increment(deleted);
    }
}
