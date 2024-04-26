package net.ripe.rpki.domain.aspa;

import com.google.common.collect.ImmutableSortedSet;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import java.util.*;

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
     * Set of provider ASNs.
     */
    @NotEmpty
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "aspaconfiguration_providers")
    private Set<@NonNull Asn> providers = new TreeSet<>();

    public AspaConfiguration(@NonNull ManagedCertificateAuthority certificateAuthority, @NonNull Asn customerAsn, @NonNull SortedSet<Asn> providers) {
        this.certificateAuthority = certificateAuthority;
        this.customerAsn = customerAsn;
        this.providers.addAll(providers);
    }

    public static SortedMap<Asn, SortedSet<Asn>> entitiesToMaps(SortedMap<Asn, AspaConfiguration> entities) {
        return streamToSortedMap(
            entities.values().stream(),
            AspaConfiguration::getCustomerAsn,
            AspaConfiguration::getProviders
        );
    }

    public SortedSet<Asn> getProviders() {
        return ImmutableSortedSet.copyOf(providers);
    }

    public void setProviders(SortedSet<Asn> providers) {
        this.providers = new TreeSet<>(providers);
    }

    public AspaConfigurationData toData() {
        return new AspaConfigurationData(
            getCustomerAsn(),
            List.copyOf(getProviders())
        );
    }
}
