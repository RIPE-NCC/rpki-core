package net.ripe.rpki.server.api.services.background;

import net.ripe.rpki.core.services.background.BackgroundServiceExecutionResult;

public interface BackgroundService {

    String getName();

    String getStatus();

    boolean isRunning();

    boolean isActive();

    boolean isBlocked();

    BackgroundServiceExecutionResult execute();

}
