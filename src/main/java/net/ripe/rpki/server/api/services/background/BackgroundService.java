package net.ripe.rpki.server.api.services.background;

import net.ripe.rpki.core.services.background.BackgroundServiceExecutionResult;

import java.util.Collections;
import java.util.Map;

public interface BackgroundService {
    String FORCE_UPDATE_PARAMETER = "forceUpdate";
    String BATCH_SIZE_PARAMETER = "batchSize";

    String getName();

    String getStatus();

    boolean isWaitingOrRunning();

    boolean isActive();

    BackgroundServiceExecutionResult execute(Map<String, String> parameters);

    default Map<String, String> supportedParameters() {
        return Collections.emptyMap();
    }
}
