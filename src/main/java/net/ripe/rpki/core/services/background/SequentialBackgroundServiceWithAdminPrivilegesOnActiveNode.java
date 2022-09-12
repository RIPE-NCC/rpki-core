package net.ripe.rpki.core.services.background;

public abstract class SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode extends BackgroundServiceWithAdminPrivilegesOnActiveNode {

    public SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode(BackgroundTaskRunner backgroundTaskRunner) {
        super(backgroundTaskRunner, true);
    }

}
