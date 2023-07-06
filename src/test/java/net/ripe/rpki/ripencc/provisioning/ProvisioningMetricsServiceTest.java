package net.ripe.rpki.ripencc.provisioning;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.provisioning.payload.error.NotPerformedError;
import net.ripe.rpki.commons.provisioning.payload.error.RequestNotPerformedResponsePayloadBuilder;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayloadBuilder;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.commons.validation.ValidationStatus;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;

@ExtendWith(MockitoExtension.class)
public class ProvisioningMetricsServiceTest {
    private static final UUID KNOWN_UNKNOWN_SENDER = UUID.randomUUID();
    private ProvisioningMetricsService subject;

    private MeterRegistry registry;

    @BeforeEach
    public void setUp() {
        registry = new SimpleMeterRegistry();
        subject = new ProvisioningMetricsService(registry, Set.of(KNOWN_UNKNOWN_SENDER.toString()));
    }

    @Test
    public void testTrackValidationResult_null_tolerant() {
        subject.trackValidationResult(null);
    }

    @Test
    public void testTrackValidationResult_track_warning_and_error() {
        final ValidationResult validationResult = ValidationResult.withLocation("https://example.org");
        validationResult.warn("warn.key");

        subject.trackValidationResult(validationResult);
        // one warning, one validation result object processed
        then(registry.find("rpkicore.rfc6492.validation").meters())
                .anySatisfy(meter -> then(meter.getId().getTag("status")).isEqualTo(ValidationStatus.WARNING.getMessageKey()))
                .anySatisfy(meter -> then(meter.getId().getTag("check")).isEqualTo("warn.key"))
                .hasSize(1);
        then(registry.find("rpkicore.rfc6492.validation.total"))
                .isNotNull()
                .satisfies(search -> then(search.counter().count()).isEqualTo(1));

        validationResult.rejectIfTrue(true, "failure.key");

        subject.trackValidationResult(validationResult);
        // two warnings, two validation result objects processed
        then(registry.find("rpkicore.rfc6492.validation").meters())
                .anySatisfy(meter -> then(meter.getId().getTag("status")).isEqualTo(ValidationStatus.ERROR.getMessageKey()))
                .anySatisfy(meter -> then(meter.getId().getTag("check")).isEqualTo("failure.key"))
                .hasSize(2);
        then(registry.find("rpkicore.rfc6492.validation.total"))
                .isNotNull()
                .satisfies(search -> then(search.counter().count()).isEqualTo(2));
    }

    @Test
    public void testTrackPayload_null_tolerant() {
        subject.trackPayload(null);
    }

    @Test
    public void testTrackPayload_request_payload() {
        subject.trackPayload(new ResourceClassListQueryPayloadBuilder().build());
        then(registry.getMeters()).hasSize(2);
    }

    @Test
    public void testTrackPayload_not_performed_response_payload() {
        final RequestNotPerformedResponsePayloadBuilder builder = new RequestNotPerformedResponsePayloadBuilder();
        builder.withError(NotPerformedError.VERSION_NUMBER_ERROR);
        subject.trackPayload(builder.build());

        // payload type + error response code metric
        then(registry.getMeters()).hasSize(3);
    }

    @Test
    public void testTrackProvisioningExceptionCause_null_tolerant() {
        subject.trackProvisioningExceptionCause(null);
    }

    @Test
    public void testTrackProvisioningExceptionCause_payload() {
        subject.trackProvisioningExceptionCause(new ProvisioningException.BadData());

        then(registry.find("rpkicore.rfc6492.response.exception").meters()).haveExactly(1, withType("BAD_DATA"));
    }

    @Test
    public void testTrackUnknownSenderProvisioningError() {
        subject.trackProvisioningExceptionCause(new ProvisioningException.UnknownSender(UUID.randomUUID()));

        then(registry.find("rpkicore.rfc6492.response.exception").meters()).haveExactly(1, withType("UNKNOWN_SENDER"));
    }

    @Test
    public void testTrackUnknownSenderProvisioningErrorForIgnoredSenderUUID() {
        subject.trackProvisioningExceptionCause(new ProvisioningException.UnknownSender(KNOWN_UNKNOWN_SENDER));
        then(registry.find("rpkicore.rfc6492.response.exception").meters()).isEmpty();
    }

    @Test
    public void testTrackBadSenderAndRecipientErrorForIgnoredSender() {
        subject.trackProvisioningExceptionCause(new ProvisioningException.BadSenderAndRecipient(KNOWN_UNKNOWN_SENDER.toString()));
        then(registry.find("rpkicore.rfc6492.response.exception").meters()).isEmpty();
    }

    private Condition<Meter> withType(String type) {
        return new Condition<>(
                x -> Optional.ofNullable(x.getId().getTag("type")).orElse("").equals(type),
                "tag 'type' with value '%s'", type
        );
    }
}
