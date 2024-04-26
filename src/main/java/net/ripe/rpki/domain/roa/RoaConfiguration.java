package net.ripe.rpki.domain.roa;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Specification for a ROA. This specification determines how ROAs must be
 * created and managed. The ROA specification can be edited by the user. The
 * system will then take care of managing the required ROAs.
 */
@Slf4j
@Entity
@Table(name = "roaconfiguration")
@SequenceGenerator(name = "seq_roaconfiguration", sequenceName = "seq_all", allocationSize = 1)
public class RoaConfiguration extends EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_roaconfiguration")
    @Getter
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "certificateauthority_id")
    private ManagedCertificateAuthority certificateAuthority;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "roaconfiguration_prefixes", joinColumns = @JoinColumn(name = "roaconfiguration_id"))
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

    public void setPrefixes(Collection<? extends RoaConfigurationPrefix> prefixes) {
        this.prefixes = convertToSet(convertToMap(prefixes));
    }

    public Set<RoaConfigurationPrefix> getPrefixes() {
        return Collections.unmodifiableSet(prefixes);
    }

    public ManagedCertificateAuthority getCertificateAuthority() {
        return certificateAuthority;
    }

    public RoaConfigurationData convertToData() {
        return new RoaConfigurationData(prefixes.stream()
                .map(RoaConfigurationPrefix::toData).toList());
    }

    public final void addPrefix(Collection<? extends RoaConfigurationPrefix> roaPrefixes) {
        Map<AnnouncedRoute, Integer> byPrefix = convertToMap(prefixes);
        byPrefix.putAll(convertToMap(roaPrefixes));
        prefixes = convertToSet(byPrefix);
    }

    public final void removePrefix(Collection<? extends RoaConfigurationPrefix> roaPrefixes) {
        prefixes.removeAll(roaPrefixes);
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

    private static Set<RoaConfigurationPrefix> convertToSet(Map<AnnouncedRoute, Integer> byPrefix) {
        return byPrefix.entrySet().stream().map(prefix -> new RoaConfigurationPrefix(prefix.getKey(), prefix.getValue())).collect(Collectors.toSet());
    }

    private static Map<AnnouncedRoute, Integer> convertToMap(Collection<? extends RoaConfigurationPrefix> prefixes) {
        return prefixes.stream().collect(Collectors.toMap(
                prefix -> new AnnouncedRoute(prefix.getAsn(), prefix.getPrefix()),
                RoaConfigurationPrefix::getMaximumLength,
                (a, b) -> b));
    }
}
