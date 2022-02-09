package net.ripe.rpki.core.services.background;

import lombok.Value;

@Value
public class BackgroundServiceTimings {
    // Execution time of the service "runService" method.
    private long pureDuration;

    // Execution time including waiting for locks.
    private long fullDuration;
}
