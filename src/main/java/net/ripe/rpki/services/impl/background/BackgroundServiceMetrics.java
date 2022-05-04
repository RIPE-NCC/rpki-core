package net.ripe.rpki.services.impl.background;

import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.NonNull;
import lombok.Value;
import net.ripe.rpki.core.services.background.BackgroundServiceExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component
public class BackgroundServiceMetrics {
    public static final String SERVICE_END_TIME_METRIC = "rpkicore.service.execution.end.time";
    public static final String SERVICE_RESULT_COUNTER_METRIC = "rpkicore.service.result";
    public static final String SERVICE_RESULT_COUNTER_DESCRIPTION = "Service execution status by name and result";

    public static final String TAG_STATUS = "status";
    private static final String TAG_SERVICE = "service";

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Timings> timingMap = new ConcurrentHashMap<>();

    @Autowired
    public BackgroundServiceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void trackStartTime(String serviceName) {
        Timings timings = getTimings(serviceName);
        final long epochSecond = Instant.now().getEpochSecond();
        timings.lastStartedTimes.set(epochSecond);
    }

    public void trackResult(@NonNull String serviceName, @NonNull BackgroundServiceExecutionResult result) {
        switch (result.getStatus()) {
            case FAILURE:
                trackFailure(serviceName);
                break;
            case SUCCESS:
                trackSuccess(serviceName, result);
                break;
            case SKIPPED:
                trackSkipped(serviceName);
        }
    }

    private void trackSuccess(String serviceName, BackgroundServiceExecutionResult result) {
        Timings timings = getTimings(serviceName);
        final long epochSecond = Instant.now().getEpochSecond();
        timings.lastSuccessTimes.set(epochSecond);
        timings.pureDuration.set(result.getPureDuration());
        timings.fullDuration.set(result.getFullDuration());
        timings.successCount.increment();
    }

    /**
     * In some situations we want to track failures directly.
     * @param serviceName
     */
    public void trackFailure(String serviceName) {
        Timings timings = getTimings(serviceName);
        long now = Instant.now().getEpochSecond();
        timings.lastFailedTimes.set(now);
        timings.failureCount.increment();
    }

    private void trackSkipped(String serviceName) {
        Timings timings = getTimings(serviceName);
        timings.skippedCount.increment();
    }

    private Timings getTimings(String serviceName) {
        return timingMap.computeIfAbsent(serviceName, Timings.init(meterRegistry));
    }

    @Value
    private static class Timings {
        final AtomicDouble lastStartedTimes = new AtomicDouble(0);
        final AtomicDouble lastSuccessTimes = new AtomicDouble(0);
        final AtomicDouble lastFailedTimes = new AtomicDouble(0);
        final AtomicDouble pureDuration = new AtomicDouble(0);
        final AtomicDouble fullDuration = new AtomicDouble(0);

        final Counter successCount;
        final Counter failureCount;
        final Counter skippedCount;

        private Timings(@NonNull  String serviceName, @NonNull final MeterRegistry registry) {
            Gauge.builder("rpkicore.service.execution.start.time", lastStartedTimes::get)
                    .description("Last execution start time for each service")
                    .tag(TAG_SERVICE, serviceName)
                    .register(registry);

            Gauge.builder(SERVICE_END_TIME_METRIC, lastSuccessTimes::get)
                    .description("Last execution time for each service")
                    .tag(TAG_SERVICE, serviceName)
                    .tag(TAG_STATUS, "success")
                    .register(registry);
            Gauge.builder(SERVICE_END_TIME_METRIC, lastFailedTimes::get)
                    .tag(TAG_SERVICE, serviceName)
                    .tag(TAG_STATUS, "failed")
                    .register(registry);

            Gauge.builder("rpkicore.service.last.execution.duration.ms", pureDuration::get)
                    .description("Last duration of the service execution")
                    .tag(TAG_SERVICE, serviceName)
                    .register(registry);

            Gauge.builder("rpkicore.service.last.execution.total.duration.ms", fullDuration::get)
                    .description("Last duration of the service execution including waiting for the lock")
                    .tag(TAG_SERVICE, serviceName)
                    .register(registry);

            successCount = Counter.builder(SERVICE_RESULT_COUNTER_METRIC)
                    .description(SERVICE_RESULT_COUNTER_DESCRIPTION)
                    .tag(TAG_SERVICE, serviceName)
                    .tag(TAG_STATUS, "success")
                    .register(registry);

            failureCount = Counter.builder(SERVICE_RESULT_COUNTER_METRIC)
                    .description(SERVICE_RESULT_COUNTER_DESCRIPTION)
                    .tag(TAG_SERVICE, serviceName)
                    .tag(TAG_STATUS, "failed")
                    .register(registry);

            skippedCount = Counter.builder(SERVICE_RESULT_COUNTER_METRIC)
                    .description(SERVICE_RESULT_COUNTER_DESCRIPTION)
                    .tag(TAG_SERVICE, serviceName)
                    .tag(TAG_STATUS, "skipped")
                    .register(registry);
        }

        public static Function<String, Timings> init(@NonNull MeterRegistry registry) {
            return serviceName -> new Timings(serviceName, registry);
        }
    }
}
