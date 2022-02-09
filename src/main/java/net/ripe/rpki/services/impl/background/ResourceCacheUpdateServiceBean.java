package net.ripe.rpki.services.impl.background;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.core.services.background.ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service("resourceCacheUpdateService")
public class ResourceCacheUpdateServiceBean extends ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final ResourceCacheService resourceCacheService;

    @Autowired
    public ResourceCacheUpdateServiceBean(ActiveNodeService activeNodeService,
                                          ResourceCacheService resourceCacheService) {
        super(activeNodeService);
        this.resourceCacheService = resourceCacheService;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    protected void runService() {
        resourceCacheService.updateFullResourceCache();
    }
}
