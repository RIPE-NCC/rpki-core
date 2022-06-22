package net.ripe.rpki.domain.aspa;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
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
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

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
    private HostedCertificateAuthority certificateAuthority;

    @Column(name = "customer_asn", nullable = false)
    private BigInteger customerAsn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "aspaconfiguration_provider_asns", joinColumns = @JoinColumn(name = "aspaconfiguration_id"))
    private Set<AspaProviderAsn> providerASSet = new HashSet<>();

    public AspaConfiguration(HostedCertificateAuthority ca) {
        this(ca, null, Collections.emptySet());
    }

    public AspaConfiguration(HostedCertificateAuthority ca, BigInteger customerAsn, Set<AspaProviderAsn> providerASSet) {
        this.certificateAuthority = Preconditions.checkNotNull(ca, "certificateAuthority is required");
        this.customerAsn = customerAsn;
        this.providerASSet.addAll(providerASSet);
    }

    public Asn getCustomerAsn() {
        return new Asn(customerAsn.longValue());
    }

    public Set<AspaProviderAsn> getProviderASSet() {
        return Collections.unmodifiableSet(providerASSet);
    }

    public AspaConfigurationData toData() {
        return new AspaConfigurationData(
            new Asn(customerAsn), providerASSet.stream()
            .map(AspaProviderAsn::toData)
            .collect(Collectors.toSet()));
    }
}
