package net.ripe.rpki.domain;

import lombok.Value;
import net.ripe.ipresource.IpResourceSet;

import static java.util.Objects.requireNonNull;

@Value
public class ResourceClassListResponse {
    IpResourceSet certifiableResources;

    public ResourceClassListResponse() {
        this(new IpResourceSet());
    }

    public ResourceClassListResponse(IpResourceSet certifiableResources) {
        this.certifiableResources = requireNonNull(certifiableResources, "certifiableResources is required");
    }

}
