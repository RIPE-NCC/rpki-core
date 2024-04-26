package net.ripe.rpki.domain;

import com.google.common.collect.Iterables;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResourceType;
import org.apache.commons.lang3.Validate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.PrePersist;
import java.io.Serializable;
import java.util.Optional;

/**
 * Represents the set of requested resources in an {@see https://datatracker.ietf.org/doc/html/rfc6492#section-3.4.1 certificate issuance request}
 *
 * For each requested resource set <code>null</code> indicates all certifiable resources of this type should be included, while the empty
 * set indicates that no resources of this type should be included.
 *
 * Otherwise, the union of the certifiable resources and the requested resources will be included.
 *
 */
@Embeddable
@EqualsAndHashCode
@ToString
public class RequestedResourceSets implements Serializable {

    @Column(name = "req_resource_set_asn", columnDefinition = "TEXT")
    private ImmutableResourceSet requestedResourceSetAsn;

    @Column(name = "req_resource_set_ipv4", columnDefinition = "TEXT")
    private ImmutableResourceSet requestedResourceSetIpv4;

    @Column(name = "req_resource_set_ipv6", columnDefinition = "TEXT")
    private ImmutableResourceSet requestedResourceSetIpv6;

    public RequestedResourceSets() {
        this(Optional.empty(), Optional.empty(), Optional.empty());
    }

    public RequestedResourceSets(
        Optional<ImmutableResourceSet> requestedResourceSetAsn,
        Optional<ImmutableResourceSet> requestedResourceSetIpv4,
        Optional<ImmutableResourceSet> requestedResourceSetIpv6
    ) {
        this.requestedResourceSetAsn = requestedResourceSetAsn.orElse(null);
        this.requestedResourceSetIpv4 = requestedResourceSetIpv4.orElse(null);
        this.requestedResourceSetIpv6 = requestedResourceSetIpv6.orElse(null);
        invariant();
    }

    public Optional<ImmutableResourceSet> getRequestedResourceSetAsn() {
        return Optional.ofNullable(requestedResourceSetAsn);
    }

    public Optional<ImmutableResourceSet> getRequestedResourceSetIpv4() {
        return Optional.ofNullable(requestedResourceSetIpv4);
    }

    public Optional<ImmutableResourceSet> getRequestedResourceSetIpv6() {
        return Optional.ofNullable(requestedResourceSetIpv6);
    }

    public ImmutableResourceSet calculateEffectiveResources(ImmutableResourceSet certifiableResources) {
        return getRequestedResourceSetAsn().orElse(ImmutableResourceSet.of(Resources.ALL_AS_RESOURCES))
            .union(getRequestedResourceSetIpv4().orElse(ImmutableResourceSet.of(Resources.ALL_IPV4_RESOURCES)))
            .union(getRequestedResourceSetIpv6().orElse(ImmutableResourceSet.of(Resources.ALL_IPV6_RESOURCES)))
            .intersection(certifiableResources);
    }

    @PrePersist
    private void invariant() {
        Validate.isTrue(onlyContainsResourcesOfType(requestedResourceSetAsn, IpResourceType.ASN), "req_resource_set_asn must only contain AS resources");
        Validate.isTrue(onlyContainsResourcesOfType(requestedResourceSetIpv4, IpResourceType.IPv4), "req_resource_set_ipv4 must only contain IPv4 resources");
        Validate.isTrue(onlyContainsResourcesOfType(requestedResourceSetIpv6, IpResourceType.IPv6), "req_resource_set_ipv6 must only contain IPv6 resources");
    }

    private boolean onlyContainsResourcesOfType(ImmutableResourceSet resourceSet, IpResourceType type) {
        return resourceSet == null || Iterables.all(resourceSet, resource -> resource.getType() == type);
    }

}
