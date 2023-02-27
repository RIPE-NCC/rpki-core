package net.ripe.rpki.ripencc.ui.daemon.health;


import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Controller
@RequestMapping(path = "/monitoring/healthcheck")
public class HealthService {
    private final List<Health.Check> healthchecks;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, HealthServiceMetrics> healthCheckMetrics = new ConcurrentHashMap<>();

    @Inject
    public HealthService(@NonNull List<Health.Check> healthchecks, MeterRegistry meterRegistry) {
        this.healthchecks = healthchecks;
        this.meterRegistry = meterRegistry;
    }

    private void trackStatus(String checkName, Health.Status checkStatus) {
        healthCheckMetrics
                .computeIfAbsent(checkName, c -> new HealthServiceMetrics(c, meterRegistry))
                .update(checkStatus);
    }

    @GetMapping
    public ResponseEntity<Map<String, Health.Status>> getHealthChecks() {
        final Map<String, Health.Status> statuses = Health.statuses(healthchecks);

        statuses.forEach(this::trackStatus);

        HttpStatus httpStatus = Health.httpCode(statuses);
        if (httpStatus != HttpStatus.OK) {
            log.warn("Health check servlet is called, result is {}", Health.toJson(statuses));
        }

        return ResponseEntity.status(httpStatus)
                        .body(statuses);
    }

    private static class HealthServiceMetrics {
        private final AtomicInteger status;
        private final AtomicLong updated;

        public HealthServiceMetrics(String checkName, MeterRegistry registry) {
            status = new AtomicInteger(Health.Code.OK.ordinal());
            updated = new AtomicLong(System.currentTimeMillis());

            Gauge.builder("rpkicore.healthcheck.status", status::get)
                    .description("Ordinal status of health checks that have been evaluated. 0 is OK, 1 is WARN, 2 is ERROR")
                    .tag("check", checkName)
                    .register(registry);


            TimeGauge.builder("rpkicore.healthcheck.updated", updated::get, TimeUnit.MILLISECONDS)
                    .tag("check", checkName)
                    .register(registry);
        }

        public void update(Health.Status checkStatus) {
            status.set(checkStatus.status.ordinal());
            updated.set(System.currentTimeMillis());
        }
    }
}
