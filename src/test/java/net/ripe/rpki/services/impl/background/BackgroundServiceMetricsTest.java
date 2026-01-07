package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.core.services.background.BackgroundServiceExecutionResult;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.Random;

import static net.ripe.rpki.services.impl.background.BackgroundServiceMetrics.SERVICE_RESULT_COUNTER_METRIC;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class BackgroundServiceMetricsTest {
    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final BackgroundServiceMetrics subject = new BackgroundServiceMetrics(registry);

    @Test
    public void shouldTrackServiceExecutionStart() {
        String service = RandomStringUtils.insecure().nextAlphanumeric(16);
        subject.trackStartTime(service);

        assertThat(valueOfGauge("rpkicore.service.execution.start.time", service)).isGreaterThan(0d);
    }

    @Test
    public void shouldTrackSuccessfulServiceExecution() {
        String service = RandomStringUtils.insecure().nextAlphanumeric(16);
        var random = new SecureRandom();

        BackgroundServiceExecutionResult job = new BackgroundServiceExecutionResult(random.nextLong(), random.nextLong(), BackgroundServiceExecutionResult.Status.SUCCESS);
        subject.trackStartTime(service);
        subject.trackResult(service, job);

        assertThat(valueOfCounter(SERVICE_RESULT_COUNTER_METRIC, service, "status", "success")).isOne();
        assertThat(valueOfCounter(SERVICE_RESULT_COUNTER_METRIC, service, "status", "failed")).isZero();
        assertThat(valueOfCounter(SERVICE_RESULT_COUNTER_METRIC, service, "status", "skipped")).isZero();

        assertThat(valueOfGauge("rpkicore.service.execution.end.time", service, "status", "success")).isGreaterThan(0);
        assertThat(valueOfGauge("rpkicore.service.last.execution.duration.ms", service)).isEqualTo(job.getPureDuration());
        assertThat(valueOfGauge("rpkicore.service.last.execution.total.duration.ms", service)).isEqualTo(job.getFullDuration());
    }

    @Test
    public void shouldTrackFailedServiceExecution() {
        String service = RandomStringUtils.insecure().nextAlphanumeric(16);
        subject.trackStartTime(service);
        subject.trackResult(service, new BackgroundServiceExecutionResult(0, 0, BackgroundServiceExecutionResult.Status.FAILURE));

        assertThat(valueOfCounter(SERVICE_RESULT_COUNTER_METRIC, service, "status", "success")).isZero();
        assertThat(valueOfCounter(SERVICE_RESULT_COUNTER_METRIC, service, "status", "failed")).isOne();
        assertThat(valueOfCounter(SERVICE_RESULT_COUNTER_METRIC, service, "status", "skipped")).isZero();

        assertThat(valueOfGauge("rpkicore.service.execution.end.time", service, "status", "failed")).isGreaterThan(0);
        assertThat(valueOfGauge("rpkicore.service.last.execution.duration.ms", service)).isZero();
        assertThat(valueOfGauge("rpkicore.service.last.execution.total.duration.ms", service)).isZero();
    }

    @Test
    public void shouldTrackSkippedExecution() {
        String service = RandomStringUtils.insecure().nextAlphanumeric(16);
        subject.trackStartTime(service);
        subject.trackResult(service, new BackgroundServiceExecutionResult(0, 0, BackgroundServiceExecutionResult.Status.SKIPPED));

        assertThat(valueOfCounter(SERVICE_RESULT_COUNTER_METRIC, service, "status", "success")).isZero();
        assertThat(valueOfCounter(SERVICE_RESULT_COUNTER_METRIC, service, "status", "failed")).isZero();
        assertThat(valueOfCounter(SERVICE_RESULT_COUNTER_METRIC, service, "status", "skipped")).isOne();

        assertThat(valueOfGauge("rpkicore.service.execution.end.time", service, "status", "success")).isZero();
        assertThat(valueOfGauge("rpkicore.service.execution.end.time", service, "status", "failed")).isZero();
        assertThat(valueOfGauge("rpkicore.service.last.execution.duration.ms", service)).isZero();
        assertThat(valueOfGauge("rpkicore.service.last.execution.total.duration.ms", service)).isZero();
    }

    private double valueOfGauge(String name, String service, String... tags) {
        return registry.get(name).tag("service", service).tags(tags).gauge().value();
    }

    private double valueOfCounter(String name, String service, String... tags) {
        return registry.get(name).tag("service", service).tags(tags).counter().count();
    }
}