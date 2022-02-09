package net.ripe.rpki.domain.roa;

import com.google.common.base.Preconditions;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.CollectionTable;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
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
@Entity
@Table(name = "roaconfiguration")
@SequenceGenerator(name = "seq_roaconfiguration", sequenceName = "seq_all", allocationSize = 1)
public class RoaConfiguration extends EntitySupport {

    private static final Logger LOG = LoggerFactory.getLogger(RoaConfiguration.class);

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_roaconfiguration")
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "certificateauthority_id")
    private HostedCertificateAuthority certificateAuthority;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "roaconfiguration_prefixes", joinColumns = @JoinColumn(name = "roaconfiguration_id"))
    private Set<RoaConfigurationPrefix> prefixes = new HashSet<>();

    public RoaConfiguration() {
    }

    public RoaConfiguration(HostedCertificateAuthority certificateAuthority) {
        this(certificateAuthority, Collections.emptySet());
    }

    public RoaConfiguration(HostedCertificateAuthority certificateAuthority, Collection<? extends RoaConfigurationPrefix> prefixes) {
        this.certificateAuthority = Preconditions.checkNotNull(certificateAuthority, "certificateAuthority is required");
        this.prefixes.addAll(prefixes);
    }

    @Override
    public Long getId() {
        return id;
    }

    public void setPrefixes(Collection<? extends RoaConfigurationPrefix> prefixes) {
        this.prefixes = convertToSet(convertToMap(prefixes));
    }

    public Set<RoaConfigurationPrefix> getPrefixes() {
        return Collections.unmodifiableSet(prefixes);
    }

    public HostedCertificateAuthority getCertificateAuthority() {
        return certificateAuthority;
    }

    public RoaConfigurationData convertToData() {
        return new RoaConfigurationData(prefixes.stream()
                .map(RoaConfigurationPrefix::toData)
                .collect(Collectors.toList()));
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
        IpResourceSet caResources = currentIncomingCertificate.getCertificate().getResources();
        ValidityPeriod validityPeriod = currentIncomingCertificate.getCertificate().getValidityPeriod();

        Map<Asn, RoaSpecification> result = new TreeMap<>();
        for (RoaConfigurationPrefix prefix : prefixes) {
            if (caResources.contains(prefix.getPrefix())) {
                RoaSpecification specification = result.get(prefix.getAsn());
                if (specification == null) {
                    specification = new RoaSpecification(certificateAuthority, prefix.getAsn(), validityPeriod);
                    result.put(prefix.getAsn(), specification);
                }
                specification.putPrefix(prefix.getPrefix(), prefix.getMaximumLength());
            } else {
                LOG.warn("Prefix " + prefix.getPrefix() + " in the Roa configuration is not covered by the resources of CA: " + certificateAuthority.getName().toString());
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
