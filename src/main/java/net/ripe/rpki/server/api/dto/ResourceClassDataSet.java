package net.ripe.rpki.server.api.dto;

import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The certified resources and keys for a CA
 */
public final class ResourceClassDataSet extends ValueObjectSupport {

    private final Map<String, ResourceClassData> resourceClassDataMap;

    public static ResourceClassDataSet empty() {
        return new ResourceClassDataSet();
    }

    public ResourceClassDataSet plus(String resourceClassName, ResourceClassData resourceClassData) {
        HashMap<String, ResourceClassData> result = new HashMap<>(resourceClassDataMap);
        result.put(resourceClassName, resourceClassData);
        return new ResourceClassDataSet(result);
    }

    private ResourceClassDataSet() {
        resourceClassDataMap = new HashMap<>();
    }

    private ResourceClassDataSet(Map<String, ResourceClassData> resourceClassDataMap) {
        this.resourceClassDataMap = resourceClassDataMap;
    }

    public Set<String> getClassNames() {
        return resourceClassDataMap.keySet();
    }

    public ResourceClassData getResourceClassData(String resourceClassName) {
        return resourceClassDataMap.getOrDefault(resourceClassName, ResourceClassData.empty(resourceClassName));
    }
    
}
