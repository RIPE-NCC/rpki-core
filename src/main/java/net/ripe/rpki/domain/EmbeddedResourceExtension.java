package net.ripe.rpki.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import java.util.EnumSet;

/**
 * JPA mappable version of a {@link net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension}
 */
@Embeddable
@EqualsAndHashCode
@Getter
public class EmbeddedResourceExtension {

    @Column(name = "asn_inherited", nullable = false)
    private boolean asnInherited;

    @Column(name = "ipv4_inherited", nullable = false)
    private boolean ipv4Inherited;

    @Column(name = "ipv6_inherited", nullable = false)
    private boolean ipv6Inherited;

    @NotNull
    @Column(nullable = false)
    private ImmutableResourceSet resources;

    protected EmbeddedResourceExtension() {
    }

    public EmbeddedResourceExtension(@NonNull ResourceExtension resourceExtension) {
        this.asnInherited = resourceExtension.isResourceTypesInherited(EnumSet.of(IpResourceType.ASN));
        this.ipv4Inherited = resourceExtension.isResourceTypesInherited(EnumSet.of(IpResourceType.IPv4));
        this.ipv6Inherited = resourceExtension.isResourceTypesInherited(EnumSet.of(IpResourceType.IPv6));
        this.resources = resourceExtension.getResources();
    }

    public @NonNull ResourceExtension getResourceExtension() {
        var inherited = EnumSet.noneOf(IpResourceType.class);
        if (asnInherited) inherited.add(IpResourceType.ASN);
        if (ipv4Inherited) inherited.add(IpResourceType.IPv4);
        if (ipv6Inherited) inherited.add(IpResourceType.IPv6);
        return ResourceExtension.of(inherited, resources);
    }


    @Override
    public String toString() {
        return String.valueOf(getResourceExtension());
    }

}
