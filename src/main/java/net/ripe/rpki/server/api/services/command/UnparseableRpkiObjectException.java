package net.ripe.rpki.server.api.services.command;

import net.ripe.rpki.commons.validation.ValidationResult;

public class UnparseableRpkiObjectException extends CertificationException {
    public UnparseableRpkiObjectException(ValidationResult validationResult) {
        super(validationResult.getFailuresForAllLocations().toString());
    }
}
