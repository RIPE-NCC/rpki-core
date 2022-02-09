package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.commons.provisioning.payload.error.NotPerformedError;

public class NotPerformedException extends RuntimeException {

    private final NotPerformedError notPerformedError;

    public NotPerformedException(NotPerformedError notPerformedError) {
        this.notPerformedError = notPerformedError;
    }

    public NotPerformedError getNotPerformedError() {
        return notPerformedError;
    }
}
