package net.ripe.rpki.domain.hsm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "hsm_certificate_chain")
@SequenceGenerator(name = "seq_certificate_chain", sequenceName = "seq_all", allocationSize = 1)
public class HsmCertificateChain implements net.ripe.rpki.ncc.core.domain.support.Entity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_certificate_chain")
    private Long id;

    @NotNull
    @Column(name = "content", nullable = false)
    private byte[] content;

    @NotNull
    @Column(name = "chain_order", nullable = false)
    private int chainOrder;

    @ManyToOne
    @JoinColumn(name = "key_id", nullable = false)
    private HsmKey hsmKey;

    public HsmCertificateChain() {
    }

    public HsmCertificateChain(byte[] content, int chainOrder) {
        this.content = content;
        this.chainOrder = chainOrder;
    }

    @Override
    public Long getId() {
        return id;
    }

    public byte[] getContent() {
        return content;
    }

    public int getChainOrder() {
        return chainOrder;
    }

    public HsmKey getHsmKey() {
        return hsmKey;
    }

    public void setHsmKey(HsmKey hsmKey) {
        this.hsmKey = hsmKey;
    }
}
