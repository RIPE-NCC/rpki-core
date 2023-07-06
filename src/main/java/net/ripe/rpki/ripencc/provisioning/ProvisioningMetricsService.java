package net.ripe.rpki.ripencc.provisioning;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.provisioning.payload.AbstractProvisioningPayload;
import net.ripe.rpki.commons.provisioning.payload.AbstractProvisioningResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.PayloadMessageType;
import net.ripe.rpki.commons.provisioning.payload.error.NotPerformedError;
import net.ripe.rpki.commons.provisioning.payload.error.RequestNotPerformedResponsePayload;
import net.ripe.rpki.commons.validation.ValidationCheck;
import net.ripe.rpki.commons.validation.ValidationResult;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.CheckForNull;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ProvisioningMetricsService {
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<Pair<String, String>, Counter> validationStatusCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PayloadMessageType, Counter> payloadTypeCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<NotPerformedError, Counter> errorPayloadCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> provisioningExceptionCounters = new ConcurrentHashMap<>();

    private final Counter totalValidationCount;
    private final Set<String> ignoredSenders;

    @Autowired
    public ProvisioningMetricsService(MeterRegistry registry, @Value("${rfc6492.sender.ignores}") Set<String> ignoredSenders) {
        this.meterRegistry = registry;
        this.ignoredSenders = Set.copyOf(ignoredSenders);

        totalValidationCount = Counter.builder("rpkicore.rfc6492.validation.total")
                .description("total number of rpki-commons validation results tracked")
                .register(meterRegistry);
    }

    /**
     * Track the status checks in a validation result of a received RFC6492 message.
     *
     * @param validationResult message to track
     */
    public void trackValidationResult(@CheckForNull ValidationResult validationResult) {
        if (validationResult == null) {
            return;
        }

        totalValidationCount.increment();

        validationResult.getWarnings().forEach(this::updateCheck);
        validationResult.getFailuresForAllLocations().forEach(this::updateCheck);
    }

    private void updateCheck(ValidationCheck check) {
        if (check == null || check.getStatus() == null || check.getKey() == null) {
            log.error("Invalid validation result (missing parameter)");
            return;
        }

        validationStatusCounters.computeIfAbsent(
                Pair.of(check.getStatus().getMessageKey(), check.getKey()),
                statusAndCheck -> Counter.builder("rpkicore.rfc6492.validation")
                        .description("rpki-commons validation warning/error count for incoming RFC 6492 up-down messages")
                        .tag("status", statusAndCheck.getLeft())
                        .tag("check", statusAndCheck.getRight())
                        .register(meterRegistry)
        ).increment();
    }

    /**
     * Track the return of a payload response, and track the request not performed error's type iff applicable.
     *
     * @param payload payload to track
     */
    public void trackPayload(@CheckForNull AbstractProvisioningPayload payload) {
        if (payload == null || payload.getType() == null) {
            return;
        }
        payloadTypeCounters.computeIfAbsent(payload.getType(),
                pt -> Counter.builder("rpkicore.rfc6492.payload.type")
                        .description("Number of requests by payload type")
                        .tag("type", pt.name())
                        .tag("direction", payload instanceof AbstractProvisioningResponsePayload ? "response" : "request")
                        .register(meterRegistry)
        ).increment();

        if (payload instanceof RequestNotPerformedResponsePayload) {
            errorPayloadCounters.computeIfAbsent(((RequestNotPerformedResponsePayload) payload).getStatus(),
                    status -> Counter.builder("rpkicore.rfc6492.payload.error.response.code")
                            .description("Number of error responses by error")
                            .tag("error", status.name())
                            .register(meterRegistry)
            ).increment();
        }
    }

    /**
     * Track a (non-CMS) exception's cause
     *
     * @param provisioningException exception to log the cause for
     */
    public void trackProvisioningExceptionCause(@CheckForNull ProvisioningException provisioningException) {
        if (provisioningException == null) {
            return;
        }
        if (provisioningException.getSender().map(ignoredSenders::contains).orElse(false)) {
            return;
        }

        provisioningExceptionCounters.computeIfAbsent(provisioningException.getName(),
                exceptionName -> Counter.builder("rpkicore.rfc6492.response.exception")
                        .description("Number of exceptions returned from up-down endpoint by underlying exception")
                        .tag("type", exceptionName)
                        .register(meterRegistry)
        ).increment();
    }
}
