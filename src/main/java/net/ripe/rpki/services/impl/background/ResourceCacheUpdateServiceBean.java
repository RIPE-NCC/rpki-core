package net.ripe.rpki.services.impl.background;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Service(BackgroundServices.RESOURCE_CACHE_UPDATE_SERVICE)
public class ResourceCacheUpdateServiceBean extends ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final ResourceCacheService resourceCacheService;

    @Autowired
    public ResourceCacheUpdateServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                          ResourceCacheService resourceCacheService) {
        super(backgroundTaskRunner);
        this.resourceCacheService = resourceCacheService;
    }

    @Override
    public String getName() {
        return "Resource cache update service";
    }

    @Override
    protected void runService(Map<String, String> parameters) {
        resourceCacheService.updateFullResourceCache(parseForceUpdateParameter(parameters));
    }

    @Override
    public Map<String, String> supportedParameters() {
        return Collections.singletonMap(FORCE_UPDATE_PARAMETER, "");
    }
}
