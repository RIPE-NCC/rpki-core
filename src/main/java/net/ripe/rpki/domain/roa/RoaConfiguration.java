package net.ripe.rpki.domain.roa;

import com.google.common.base.Preconditions;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.util.Streams;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.*;

/**
 * Specification for a ROA. This specification determines how ROAs must be
 * created and managed. The ROA specification can be edited by the user. The
 * system will then take care of managing the required ROAs.
 */
@DynamicInsert
@DynamicUpdate
@Slf4j
@Entity
@Table(name = "roaconfiguration")
@SequenceGenerator(name = "seq_roaconfiguration", sequenceName = "seq_all", allocationSize = 1)
public class RoaConfiguration extends EntitySupport {

    public static final Comparator<RoaConfigurationPrefix> ROA_CONFIGURATION_PREFIX_COMPARATOR =
            comparing(RoaConfigurationPrefix::getAsn)
                    .thenComparing(RoaConfigurationPrefix::getPrefix)
                    .thenComparing(RoaConfigurationPrefix::getMaximumLength, reverseOrder())
                    .thenComparing(RoaConfigurationPrefix::getUpdatedAt, nullsLast(naturalOrder()));

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_roaconfiguration")
    @Getter
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "certificateauthority_id")
    @Getter
    private ManagedCertificateAuthority certificateAuthority;

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "roaConfiguration", cascade = CascadeType.MERGE)
    private Set<RoaConfigurationPrefix> prefixes = new HashSet<>();

    public RoaConfiguration() {
    }

    public RoaConfiguration(ManagedCertificateAuthority certificateAuthority) {
        this(certificateAuthority, Collections.emptySet());
    }

    public RoaConfiguration(ManagedCertificateAuthority certificateAuthority, Collection<? extends RoaConfigurationPrefix> prefixes) {
        this.certificateAuthority = Preconditions.checkNotNull(certificateAuthority, "certificateAuthority is required");
        this.prefixes.addAll(prefixes);
    }

    public void setPrefixes(Collection<RoaConfigurationPrefix> prefixes) {
        this.prefixes = canonicalRoaPrefixes(prefixes.stream());
    }

    public Set<RoaConfigurationPrefix> getPrefixes() {
        return Collections.unmodifiableSet(prefixes);
    }

    public RoaConfigurationData convertToData() {
        return new RoaConfigurationData(prefixes.stream()
                .map(RoaConfigurationPrefix::toData).toList());
    }

    public final PrefixDiff addPrefixes(Collection<RoaConfigurationPrefix> roaPrefixes) {
        return mergePrefixes(roaPrefixes, Collections.emptyList());
    }

    public final PrefixDiff removePrefixes(Collection<RoaConfigurationPrefix> roaPrefixes) {
        return mergePrefixes(Collections.emptyList(), roaPrefixes);
    }

    Map<Asn, RoaSpecification> toRoaSpecifications(IncomingResourceCertificate currentIncomingCertificate) {
        ImmutableResourceSet caResources = currentIncomingCertificate.getCertificate().resources();
        ValidityPeriod validityPeriod = currentIncomingCertificate.getCertificate().getValidityPeriod();

        Map<Asn, RoaSpecification> result = new TreeMap<>();
        for (RoaConfigurationPrefix prefix : prefixes) {
            if (caResources.contains(prefix.getPrefix())) {
                RoaSpecification specification = result.get(prefix.getAsn());
                if (specification == null) {
                    specification = new RoaSpecification(prefix.getAsn(), validityPeriod);
                    result.put(prefix.getAsn(), specification);
                }
                specification.putPrefix(prefix.getPrefix(), prefix.getMaximumLength());
            } else {
                log.warn("Prefix {} in the Roa configuration is not covered by the resources of CA: {}", prefix.getPrefix(), certificateAuthority.getName());
            }
        }
        return result;
    }

    private static Pair<Asn, IpRange> prefixKey(RoaConfigurationPrefix r) {
        return Pair.of(r.getAsn(), r.getPrefix());
    }

    private static Triple<Asn, IpRange, Integer> prefixMinimalIdentity(RoaConfigurationPrefix r) {
        return Triple.of(r.getAsn(), r.getPrefix(), r.getMaximumLength());
    }

    private static boolean differentPrefixes(RoaConfigurationPrefix r1, RoaConfigurationPrefix r2) {
        return r1 == null ? r2 != null : r2 == null || !prefixMinimalIdentity(r1).equals(prefixMinimalIdentity(r2));
    }

    /**
     * Sort the ROA prefixes and keep the first unique one by earliest updatedAt
     */
    private Set<RoaConfigurationPrefix> canonicalRoaPrefixes(Stream<RoaConfigurationPrefix> input) {
        var prefixList = input.sorted(ROA_CONFIGURATION_PREFIX_COMPARATOR)
                .filter(Streams.distinctByKey(RoaConfiguration::prefixKey))
                .toList();

        return new HashSet<>(prefixList);
    }

    /**
     * Apply added and deleted prefixes, and return the ones that were really
     * added or really deleted based on the current set of prefixes and certain
     * heuristics such as max_length and update_at fields.
     */
    public PrefixDiff mergePrefixes(
            Collection<RoaConfigurationPrefix> prefixesToAdd,
            Collection<RoaConfigurationPrefix> prefixesToRemove) {

        var toRemove = prefixesToRemove.stream()
                .map(RoaConfiguration::prefixMinimalIdentity)
                .collect(Collectors.toSet());

        var newPrefixes = Stream.concat(prefixes.stream(), prefixesToAdd.stream())
                .filter(r -> !toRemove.contains(prefixMinimalIdentity(r)))
                .collect(Collectors.toMap(
                        RoaConfiguration::prefixKey,
                        Function.identity(),
                        // Prefer prefix based on the maxLength + updatedAt heuristics
                        // defined by the comparator
                        (r1, r2) -> ROA_CONFIGURATION_PREFIX_COMPARATOR.compare(r1, r2) < 0 ? r1 : r2));

        var existing = prefixes.stream()
                .collect(Collectors.toMap(RoaConfiguration::prefixKey, Function.identity()));

        var removed = prefixes.stream()
                .filter(r -> differentPrefixes(r, newPrefixes.get(prefixKey(r))))
                .toList();

        var added = newPrefixes.values().stream()
                .filter(r -> differentPrefixes(r, existing.get(prefixKey(r))))
                .toList();

        added.forEach(r -> r.setRoaConfiguration(this));
        prefixes = new HashSet<>(newPrefixes.values());
        return new PrefixDiff(added, removed);
    }

    public record PrefixDiff(List<RoaConfigurationPrefix> added, List<RoaConfigurationPrefix> removed) {}
}
