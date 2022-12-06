package net.ripe.rpki.domain;

import lombok.Value;
import net.ripe.ipresource.ImmutableResourceSet;

import static java.util.Objects.requireNonNull;

@Value
public class ResourceClassListResponse {
    ImmutableResourceSet certifiableResources;

    public ResourceClassListResponse() {
        this(ImmutableResourceSet.empty());
    }

    public ResourceClassListResponse(ImmutableResourceSet certifiableResources) {
        this.certifiableResources = requireNonNull(certifiableResources, "certifiableResources is required");
    }

}
