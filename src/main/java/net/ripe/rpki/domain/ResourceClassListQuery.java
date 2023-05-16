package net.ripe.rpki.domain;

import lombok.NonNull;
import lombok.Value;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;

import java.util.Optional;

@Value
public class ResourceClassListQuery {

    @NonNull Optional<ResourceExtension> resourceExtension;

}
