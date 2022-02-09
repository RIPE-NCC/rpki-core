package net.ripe.rpki.server.api.services.background;

import net.ripe.rpki.core.services.background.BackgroundServiceTimings;

public interface BackgroundService {

    String getName();

    String getStatus();

    boolean isRunning();

    boolean isActive();

    boolean isBlocked();

    BackgroundServiceTimings execute();

}
