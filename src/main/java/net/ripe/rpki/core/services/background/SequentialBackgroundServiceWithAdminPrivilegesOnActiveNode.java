package net.ripe.rpki.core.services.background;

import net.ripe.rpki.server.api.services.system.ActiveNodeService;

public abstract class SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode extends BackgroundServiceWithAdminPrivilegesOnActiveNode {

    public SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode(ActiveNodeService activeNodeService) {
        super(activeNodeService, true);
    }

}
