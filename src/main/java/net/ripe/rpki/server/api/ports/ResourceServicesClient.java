package net.ripe.rpki.server.api.ports;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.EqualsSupport;
import net.ripe.rpki.server.api.support.objects.CaName;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public interface ResourceServicesClient {

    boolean isAvailable();

    IpResourceSet findProductionCaDelegations();

    MemberResources fetchAllMemberResources();

    @AllArgsConstructor
    class Delegation extends EqualsSupport {
        public final String range;
    }

    class MemberResourceResponse {
        @Getter
        private MemberResourceContent response;
    }

    class MemberResourceContent {
        @Getter
        private MemberResources content;
    }

    class MemberResources extends EqualsSupport {
        private List<AsnResource> asns;
        private final List<Ipv4Allocation> ipv4Allocations;
        private final List<Ipv6Allocation> ipv6Allocations;
        private final List<Ipv4Assignment> ipv4Assignments;
        private final List<Ipv6Assignment> ipv6Assignments;
        private final List<Ipv4ErxResource> ipv4ErxResources;

        public MemberResources(List<AsnResource> asns,
                               List<Ipv4Allocation> ipv4Allocations,
                               List<Ipv4Assignment> ipv4Assignments,
                               List<Ipv6Allocation> ipv6Allocations,
                               List<Ipv6Assignment> ipv6Assignments,
                               List<Ipv4ErxResource> ipv4ErxResources) {
            this.asns = asns;
            this.ipv4Allocations = ipv4Allocations;
            this.ipv4Assignments = ipv4Assignments;
            this.ipv6Allocations = ipv6Allocations;
            this.ipv6Assignments = ipv6Assignments;
            this.ipv4ErxResources = ipv4ErxResources;
        }

        public void ignoreAsns() {
            asns = null;
        }

        /**
         * Get the certifiable resources.
         *
         * Independent allocations in RSNG that are contiguous are combined here; this is a MUST in
         * https://tools.ietf.org/html/rfc3779#section-2.2.3.6 .
         *
         * For example, an allocation of
         * <pre>
         * 05-08-2014 	185.66.66.0/23 	NCC#2014080397 	PA
         * 05-08-2014 	185.66.64.0/23 	NCC#2014080397 	PA
         * ...
         * 05-08-2014 	2a05:640::/29 	NCC#2014080402
         * </pre>
         * Ends up in the resource cache as
         * <pre>
         *    name   |           resources
         * ----------+-------------------------------
         *  CN=28402 | 185.66.64.0/22, 2a05:640::/29
         * </pre>
         */
        public Map<CaName, IpResourceSet> getCertifiableResources() {
            final Map<CaName, IpResourceSet> map = Maps.newLinkedHashMap();
            Stream.of(asns, ipv4Allocations, ipv4Assignments, ipv6Allocations, ipv6Assignments, ipv4ErxResources)
                .filter(Objects::nonNull)
                .forEach(bunch ->
                    bunch.forEach(dto -> {
                        dto.getCertifiableResource().ifPresent(r ->
                            map.computeIfAbsent(dto.getCaName(), k -> new IpResourceSet()).add(r));
                }));

            return ImmutableMap.copyOf(map);
        }

        public Map<String, Integer> getMemberResourcesCounts() {
                Map<String, Integer> result = Maps.newTreeMap();
                result.put("Asn", asns.size());
                result.put("Ipv4Allocations", ipv4Allocations.size());
                result.put("Ipv4Assignments", ipv4Assignments.size());
                result.put("Ipv6Allocations", ipv6Allocations.size());
                result.put("Ipv6Assignments", ipv6Assignments.size());
                result.put("Ipv4ErxResources", ipv4ErxResources.size());
                return result;
        }
    }

    abstract class Resource extends EqualsSupport {
        protected final Long membershipId;
        protected final String resource;
        protected final String resourceStatus;
        protected final String caName;

        public Resource(Long membershipId, String resource, String resourceStatus, String caName) {
            this.membershipId = membershipId;
            this.caName = caName;
            this.resource = Preconditions.checkNotNull(resource);
            this.resourceStatus = Preconditions.checkNotNull(resourceStatus);
        }

        Optional<? extends IpResource> getCertifiableResource() {
            return caName != null
                ? Optional.of(IpRange.parse(resource))
                : Optional.empty();
        }

        public CaName getCaName() {
            return caName == null ? null : CaName.parse(caName.toUpperCase());
        }
    }

    class Ipv4ErxResource extends Resource {
        public Ipv4ErxResource(String resource, String resourceStatus, Long membershipId, String caName) {
            super(membershipId, resource, resourceStatus, caName);
        }
    }

    class AsnResource extends Resource {
        public AsnResource(long membershipId, String resource, String resourceStatus, String caName) {
            super(membershipId, resource, resourceStatus, caName);
        }

        @Override
        Optional<Asn> getCertifiableResource() {
            return caName != null
                ? Optional.of(Asn.parse(resource))
                : Optional.empty();
        }
    }

    class Ipv4Allocation extends Resource {
        public Ipv4Allocation(long membershipId, String resource, String resourceStatus, String caName) {
            super(membershipId, resource, resourceStatus, caName);
        }
    }

    class Ipv4Assignment extends Resource {
        public Ipv4Assignment(long membershipId, String resource, String resourceStatus, String caName) {
            super(membershipId, resource, resourceStatus, caName);
        }
    }

    class Ipv6Allocation extends Resource {
        public Ipv6Allocation(long membershipId, String resource, String resourceStatus, String caName) {
            super(membershipId, resource, resourceStatus, caName);
        }
    }

    class Ipv6Assignment extends Resource {
        public Ipv6Assignment(long membershipId, String resource, String resourceStatus, String caName) {
            super(membershipId, resource, resourceStatus, caName);
        }
    }

}
