package net.ripe.rpki.domain.aspa;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import net.ripe.rpki.server.api.dto.AspaAfiLimit;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;
import net.ripe.rpki.server.api.dto.AspaProviderData;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToOne;
import javax.persistence.OrderBy;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.validation.constraints.NotEmpty;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static net.ripe.rpki.util.Streams.streamToSortedMap;

@Entity
@Table(name = "aspaconfiguration")
@SequenceGenerator(name = "seq_aspaconfiguration", sequenceName = "seq_all", allocationSize = 1)
@Slf4j
@NoArgsConstructor
public class AspaConfiguration extends EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_aspaconfiguration")
    @Getter
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "certificateauthority_id")
    @Getter
    private ManagedCertificateAuthority certificateAuthority;

    @Column(name = "customer_asn", nullable = false)
    @Getter
    private Asn customerAsn;

    /**
     * Set of provider ASNs with the AFI limit (null allows both IPv4 and IPv6, otherwise only the specified address
     * family is allowed).
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name = "provider_asn")
    @Enumerated(value = EnumType.STRING)
    @Column(name = "afi_limit")
    @CollectionTable(name = "aspaconfiguration_providers", joinColumns = @JoinColumn(name = "aspaconfiguration_id"))
    @OrderBy("provider_asn")
    @NotEmpty
    private SortedMap<@NonNull Asn, @NonNull AspaAfiLimit> providers = new TreeMap<>();

    public AspaConfiguration(@NonNull ManagedCertificateAuthority certificateAuthority, @NonNull Asn customerAsn, @NonNull Map<Asn, AspaAfiLimit> providers) {
        this.certificateAuthority = certificateAuthority;
        this.customerAsn = customerAsn;
        this.providers.putAll(providers);
    }

    public static SortedMap<Asn, SortedMap<Asn, AspaAfiLimit>> entitiesToMaps(SortedMap<Asn, AspaConfiguration> entities) {
        return streamToSortedMap(
            entities.values().stream(),
            AspaConfiguration::getCustomerAsn,
            AspaConfiguration::getProviders
        );
    }

    public SortedMap<Asn, AspaAfiLimit> getProviders() {
        return Collections.unmodifiableSortedMap(providers);
    }

    public void setProviders(Map<Asn, AspaAfiLimit> providers) {
        this.providers = new TreeMap<>(providers);
    }

    public AspaConfigurationData toData() {
        return new AspaConfigurationData(
            getCustomerAsn(),
            providers.entrySet().stream()
                .map(entry -> new AspaProviderData(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList())
        );
    }
}
