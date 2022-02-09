package net.ripe.rpki.services.impl.background;

import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;
import net.ripe.rpki.core.services.background.BackgroundServiceTimings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component
public class BackgroundServiceMetrics {

    private static final String SERVICE_TAG = "service";

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Timings> timingMap = new ConcurrentHashMap<>();

    @Autowired
    public BackgroundServiceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void started(String serviceName) {
        Timings timings = getTimings(serviceName);
        final long epochSecond = Instant.now().getEpochSecond();
        timings.lastStartedTimes.set(epochSecond);
    }

    public void finished(String serviceName, BackgroundServiceTimings result) {
        Timings timings = getTimings(serviceName);
        final long epochSecond = Instant.now().getEpochSecond();
        timings.lastSuccessTimes.set(epochSecond);
        timings.pureDuration.set(result.getPureDuration());
        timings.fullDuration.set(result.getFullDuration());
    }

    public void failed(String serviceName) {
        Timings timings = getTimings(serviceName);
        long now = Instant.now().getEpochSecond();
        timings.lastFailedTimes.set(now);
    }

    private Timings getTimings(String serviceName) {
        return timingMap.computeIfAbsent(serviceName, Timings.init(meterRegistry));
    }

    @Value
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    private static class Timings {
        AtomicDouble lastStartedTimes = new AtomicDouble(0);
        AtomicDouble lastSuccessTimes = new AtomicDouble(0);
        AtomicDouble lastFailedTimes = new AtomicDouble(0);
        AtomicDouble pureDuration = new AtomicDouble(0);
        AtomicDouble fullDuration = new AtomicDouble(0);

        public static Function<String, Timings> init(MeterRegistry registry) {
            return serviceName -> {
                Timings t = new Timings();

                Gauge.builder("rpkicore.service.execution.start.time", t.lastStartedTimes::get)
                        .description("Last execution start time for each service")
                        .tag(SERVICE_TAG, serviceName)
                        .register(registry);

                String endTimeMetric = "rpkicore.service.execution.end.time";
                Gauge.builder(endTimeMetric, t.lastSuccessTimes::get)
                        .description("Last execution time for each service")
                        .tag(SERVICE_TAG, serviceName)
                        .tag("status", "success")
                        .register(registry);
                Gauge.builder(endTimeMetric, t.lastFailedTimes::get)
                        .tag(SERVICE_TAG, serviceName)
                        .tag("status", "failed")
                        .register(registry);

                Gauge.builder("rpkicore.service.last.execution.duration.ms", t.pureDuration::get)
                        .description("Duration of the service execution")
                        .tag(SERVICE_TAG, serviceName)
                        .register(registry);

                Gauge.builder("rpkicore.service.last.execution.total.duration.ms", t.fullDuration::get)
                        .description("Duration of the service execution including waiting for the lock")
                        .tag(SERVICE_TAG, serviceName)
                        .register(registry);

                return t;
            };
        }
    }
}
