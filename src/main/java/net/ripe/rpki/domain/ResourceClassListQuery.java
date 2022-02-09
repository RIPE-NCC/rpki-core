package net.ripe.rpki.domain;

import lombok.Value;
import net.ripe.ipresource.IpResourceSet;

import static java.util.Objects.requireNonNull;

@Value
public class ResourceClassListQuery {

    IpResourceSet resources;

    public ResourceClassListQuery(IpResourceSet resources) {
        this.resources = requireNonNull(resources, "resources is required");
    }

}
