package net.ripe.rpki.ripencc.provisioning;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.provisioning.payload.error.NotPerformedError;
import net.ripe.rpki.commons.provisioning.payload.error.RequestNotPerformedResponsePayloadBuilder;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayloadBuilder;
import net.ripe.rpki.commons.provisioning.protocol.ResponseExceptionType;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.commons.validation.ValidationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.BDDAssertions.then;

@ExtendWith(MockitoExtension.class)
public class ProvisioningMetricsTest {
    private ProvisioningMetricsService subject;

    private MeterRegistry registry;

    @BeforeEach
    public void setUp() {
        registry = new SimpleMeterRegistry();
        subject = new ProvisioningMetricsService(registry);
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
        subject.trackProvisioningExceptionCause(new ProvisioningException(ResponseExceptionType.BAD_DATA));

        then(registry.find("rpkicore.rfc6492.response.exception").meters()).hasSize(1);
    }
}
