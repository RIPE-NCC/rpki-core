package net.ripe.rpki.core.services.background;

import net.ripe.rpki.server.api.services.system.ActiveNodeService;

public abstract class ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode extends BackgroundServiceWithAdminPrivilegesOnActiveNode {

    public ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode(ActiveNodeService activeNodeService) {
        super(activeNodeService, false);
    }

}
