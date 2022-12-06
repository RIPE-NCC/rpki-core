package net.ripe.rpki.domain;

import lombok.Value;
import net.ripe.ipresource.ImmutableResourceSet;

import static java.util.Objects.requireNonNull;

@Value
public class ResourceClassListQuery {

    ImmutableResourceSet resources;

    public ResourceClassListQuery(ImmutableResourceSet resources) {
        this.resources = requireNonNull(resources, "resources is required");
    }

}
