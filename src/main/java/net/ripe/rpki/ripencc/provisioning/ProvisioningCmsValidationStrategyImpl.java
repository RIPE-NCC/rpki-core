package net.ripe.rpki.ripencc.provisioning;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObjectValidator;
import net.ripe.rpki.commons.provisioning.protocol.ResponseExceptionType;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.validation.ValidationOptions;
import net.ripe.rpki.commons.validation.ValidationResult;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static net.ripe.rpki.commons.validation.ValidationString.SIGNING_TIME_GREATER_OR_EQUAL;

@Slf4j
@AllArgsConstructor
@Component
class ProvisioningCmsValidationStrategyImpl implements ProvisioningCmsValidationStrategy {
    private final ProvisioningMetricsService provisioningMetrics;

    @Override
    public void validateProvisioningCmsAndIdentityCertificate(ProvisioningCmsObject unvalidatedProvisioningObject, Optional<DateTime> lastSigningTime, ProvisioningIdentityCertificate provisioningIdentityCertificate) throws ProvisioningException {
        // Validate the EE certificate + identity certificate
        ProvisioningCmsObjectValidator provisioningCmsValidator = new ProvisioningCmsObjectValidator(
                ValidationOptions.strictValidation(),
                lastSigningTime,
                unvalidatedProvisioningObject,
                provisioningIdentityCertificate
        );
        final ValidationResult validationResult = ValidationResult.withLocation("<cms>");
        provisioningCmsValidator.validate(validationResult);
        provisioningMetrics.trackValidationResult(validationResult);

        if (validationResult.hasFailures()) {
            log.info("Rejected up-down payload because of validation failures: {}", validationResult.getFailuresForAllLocations());

            if (validationResult.getFailuresForAllLocations().stream().anyMatch(check -> SIGNING_TIME_GREATER_OR_EQUAL.equals(check.getKey()))) {
                throw new ProvisioningException(ResponseExceptionType.POTENTIAL_REPLAY_ATTACK);
            }
            // Includes sending bad data when the certificates are expired.
            throw new ProvisioningException(ResponseExceptionType.BAD_DATA);
        }
    }
}
