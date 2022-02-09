package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.core.services.background.BackgroundServiceTimings;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

public class BackgroundServiceMetricsTest {
    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final BackgroundServiceMetrics subject = new BackgroundServiceMetrics(registry);

    @Test
    public void shouldTrackServiceExecutionStart() {
        String service = RandomStringUtils.randomAlphanumeric(16);
        subject.started(service);

        assertThat(valueOfGauge("rpkicore.service.execution.start.time", service), greaterThan(0d));
    }

    @Test
    public void shouldTrackSuccessfulServiceExecution() {
        String service = RandomStringUtils.randomAlphanumeric(16);
        BackgroundServiceTimings job = new BackgroundServiceTimings(RandomUtils.nextLong(), RandomUtils.nextLong());
        subject.started(service);
        subject.finished(service, job);

        assertThat(valueOfGauge("rpkicore.service.execution.end.time", service, "status", "success"), greaterThan(0d));
        assertThat(valueOfGauge("rpkicore.service.last.execution.duration.ms", service), equalTo((double) job.getPureDuration()));
        assertThat(valueOfGauge("rpkicore.service.last.execution.total.duration.ms", service), equalTo((double) job.getFullDuration()));
    }

    @Test
    public void shouldTrackFailedServiceExecution() {
        String service = RandomStringUtils.randomAlphanumeric(16);
        subject.started(service);
        subject.failed(service);

        assertThat(valueOfGauge("rpkicore.service.execution.end.time", service, "status", "failed"), greaterThan(0d));
        assertThat(valueOfGauge("rpkicore.service.last.execution.duration.ms", service), equalTo(0d));
        assertThat(valueOfGauge("rpkicore.service.last.execution.total.duration.ms", service), equalTo(0d));
    }

    private double valueOfGauge(String name, String service, String... tags) {
        return registry.get(name).tag("service", service).tags(tags).gauge().value();
    }
}