package net.ripe.rpki.server.api.services.background;

import net.ripe.rpki.core.services.background.BackgroundServiceExecutionResult;

import java.util.Map;

public interface BackgroundService {

    String getName();

    String getStatus();

    boolean isWaitingOrRunning();

    boolean isActive();

    BackgroundServiceExecutionResult execute(Map<String, String> parameters);

}
