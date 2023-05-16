package net.ripe.rpki.domain;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;

import java.util.Optional;

@AllArgsConstructor
@Value
public class ResourceClassListResponse {
    @NonNull Optional<ResourceExtension> resourceExtension;

    public ResourceClassListResponse(ImmutableResourceSet resources) {
        this(Optional.of(ResourceExtension.ofResources(resources)));
    }
}
