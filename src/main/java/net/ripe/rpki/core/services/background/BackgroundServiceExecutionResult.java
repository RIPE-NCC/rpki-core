package net.ripe.rpki.core.services.background;

import lombok.Value;

@Value
public class BackgroundServiceExecutionResult {
    public enum Status {
        SUCCESS,
        FAILURE,
        SKIPPED
    }

    // Execution time of the service "runService" method.
    private long pureDuration;

    // Execution time including waiting for locks.
    private long fullDuration;

    private Status status;
}
