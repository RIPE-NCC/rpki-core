package net.ripe.rpki.core.services.background;

public abstract class ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode extends BackgroundServiceWithAdminPrivilegesOnActiveNode {

    public ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode(BackgroundTaskRunner backgroundTaskRunner) {
        super(backgroundTaskRunner, false);
    }

}
