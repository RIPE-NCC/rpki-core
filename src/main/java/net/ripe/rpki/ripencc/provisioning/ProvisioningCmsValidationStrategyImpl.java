package net.ripe.rpki.ripencc.provisioning;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObjectValidator;
import net.ripe.rpki.commons.provisioning.protocol.ResponseExceptionType;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.validation.ValidationOptions;
import net.ripe.rpki.commons.validation.ValidationResult;
import org.springframework.stereotype.Component;

@Slf4j
@AllArgsConstructor
@Component
public class ProvisioningCmsValidationStrategyImpl implements ProvisioningCmsValidationStrategy {
    private final ProvisioningMetricsService provisioningMetrics;

    @Override
    public void validateProvisioningCmsAndIdentityCertificate(ProvisioningCmsObject unvalidatedProvisioningObject, ProvisioningIdentityCertificate provisioningIdentityCertificate) throws ProvisioningException {
        // Validate the EE certificate + identity certificate
        ProvisioningCmsObjectValidator provisioningCmsValidator = new ProvisioningCmsObjectValidator(
                ValidationOptions.strictValidation(),
                unvalidatedProvisioningObject,
                provisioningIdentityCertificate
        );
        final ValidationResult validationResult = ValidationResult.withLocation("<cms>");
        provisioningCmsValidator.validate(validationResult);
        provisioningMetrics.trackValidationResult(validationResult);

        if (validationResult.hasFailures()) {
            log.info("Rejected up-down payload because of validation failures: {}", validationResult.getFailuresForAllLocations());
            // Includes sending bad data when the certificates are expired.
            throw new ProvisioningException(ResponseExceptionType.BAD_DATA);
        }
    }
}
