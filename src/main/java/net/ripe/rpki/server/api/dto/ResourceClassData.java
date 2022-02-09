package net.ripe.rpki.server.api.dto;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;

import java.util.Collections;
import java.util.List;

public class ResourceClassData extends ValueObjectSupport {

    private String name;
    private IpResourceSet currentResources;
    private final List<KeyPairData> keys;

    public static ResourceClassData empty(String name) {
        return new ResourceClassData(name, new IpResourceSet(), Collections.emptyList());
    }

    public ResourceClassData(String name, IpResourceSet currentResources) {
        this(name, currentResources, Collections.emptyList());
    }

    public ResourceClassData(String name, IpResourceSet currentResources, List<KeyPairData> keys) {
        this.name = name;
        this.currentResources = currentResources;
        this.keys = keys;
    }

    public String getName() {
        return name;
    }

    public IpResourceSet getCurrentResources() {
        return currentResources;
    }

    public List<KeyPairData> getKeys() {
        return keys;
    }
}
