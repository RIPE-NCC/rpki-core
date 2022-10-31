package net.ripe.rpki.server.api.dto;

import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;
import org.apache.commons.lang.Validate;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Immutable class to represent a set of certified/certifiable resources by its
 * resource class.
 */
public class ResourceClassMap extends ValueObjectSupport implements Iterable<Map.Entry<String, IpResourceSet>> {

    private final SortedMap<String, IpResourceSet> resourceClasses;

    public static ResourceClassMap empty() {
        return new ResourceClassMap(Collections.emptyMap());
    }

    public static ResourceClassMap singleton(String resourceClass, IpResourceSet resources) {
        return empty().plus(resourceClass, resources);
    }

    private ResourceClassMap(Map<String, IpResourceSet> resourceClasses) {
        this.resourceClasses = new TreeMap<>(resourceClasses);
        for (Map.Entry<String, IpResourceSet> entry: this.resourceClasses.entrySet()) {
            entry.setValue(new IpResourceSet(entry.getValue()));
        }
    }

    private ResourceClassMap copy() {
        return new ResourceClassMap(resourceClasses);
    }

    public ResourceClassMap plus(String resourceClass, IpResource resource) {
        return plus(resourceClass, new IpResourceSet(resource));
    }

    public ResourceClassMap plus(String resourceClass, IpResourceSet resources) {
        Validate.notEmpty(resourceClass, "resourceClass must be present");
        Validate.notNull(resources, "resources are required");

        ResourceClassMap result = copy();
        IpResourceSet current = result.resourceClasses.get(resourceClass);
        if (current == null) {
            current = new IpResourceSet();
            result.resourceClasses.put(resourceClass, current);
        }
        current.addAll(resources);
        return result;
    }

    public ResourceClassMap minus(ResourceClassMap that) {
        ResourceClassMap result = copy();
        for (Iterator<Map.Entry<String, IpResourceSet>> it = result.resourceClasses.entrySet().iterator(); it.hasNext(); ) {
            Entry<String, IpResourceSet> entry = it.next();
            IpResourceSet other = that.getResources(entry.getKey());
            entry.getValue().removeAll(other);
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
        return result;
    }

    public IpResourceSet getResources(String resourceClass) {
        return resourceClasses.containsKey(resourceClass) ? resourceClasses.get(resourceClass) : new IpResourceSet();
    }

    public String findResourceClassContainingResourcesOrNull(IpResource resource) {
        return findResourceClassContainingResourcesOrNull(new IpResourceSet(resource));
    }

    public String findResourceClassContainingResourcesOrNull(IpResourceSet resource) {
        if (resource.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, IpResourceSet> entry: resourceClasses.entrySet()) {
            if (entry.getValue().contains(resource)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public boolean isEmpty() {
        for (IpResourceSet resources: resourceClasses.values()) {
            if (!resources.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean contains(ResourceClassMap that) {
        for (Entry<String, IpResourceSet> entry: that.resourceClasses.entrySet()) {
            if (!getResources(entry.getKey()).contains(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    public Set<String> getClasses() {
        return resourceClasses.keySet();
    }

    public SortedMap<String, IpResourceSet> getResourceClasses() {
        return Collections.unmodifiableSortedMap(resourceClasses);
    }

    public IpResourceSet toIpResourceSet() {
        IpResourceSet result = new IpResourceSet();
        for (IpResourceSet ipResources : resourceClasses.values()) {
            result.addAll(ipResources);
        }
        return result;
    }

    @Override
    public Iterator<Entry<String, IpResourceSet>> iterator() {
        return Collections.unmodifiableSortedMap(resourceClasses).entrySet().iterator();
    }

}
