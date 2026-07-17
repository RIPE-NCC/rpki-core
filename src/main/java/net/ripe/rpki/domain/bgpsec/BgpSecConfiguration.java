package net.ripe.rpki.domain.bgpsec;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import net.ripe.rpki.server.api.dto.BgpSecConfigurationData;

@Entity
@Table(name = "bgpsec_configuration")
@SequenceGenerator(name = "seq_bgpsec_configuration", sequenceName = "seq_all", allocationSize = 1)
@Slf4j
@NoArgsConstructor
public class BgpSecConfiguration extends EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_bgpsec_configuration")
    @Getter
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "certificateauthority_id")
    @Getter
    private ManagedCertificateAuthority certificateAuthority;

    @Column(name = "asn", nullable = false)
    @Getter
    private Asn asn;

    @Column(name = "router_id")
    @Getter
    private Long routerId;

    @Column(name = "key_identifier", nullable = false)
    @Getter
    private String keyIdentifier;

    @Column(name = "certificate_request", nullable = false)
    @Getter
    private String csr;

    public BgpSecConfiguration(ManagedCertificateAuthority certificateAuthority, Asn asn, Long routerId, String csr) {
        this.certificateAuthority = certificateAuthority;
        this.asn = asn;
        this.routerId = routerId;
        this.csr = csr;
        this.keyIdentifier = Csr.getKeyIdentifier(csr);
    }

    public BgpSecConfigurationData toData() {
        return new BgpSecConfigurationData(id, asn, new RouterId(routerId), csr, keyIdentifier);
    }

    public BgpSecConfigurationData withId() {
        return new BgpSecConfigurationData(id,asn, new RouterId(routerId),csr,  keyIdentifier);
    }
}
