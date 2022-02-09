package net.ripe.rpki.domain.hsm;

import net.ripe.rpki.ncc.core.domain.support.EntitySupport;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hsm_key")
@SequenceGenerator(name = "seq_hsm_keys", sequenceName = "seq_all", allocationSize = 1)
public class HsmKey extends EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_hsm_keys")
    private Long id;

    @NotNull
    @Column(name = "key_blob", nullable = false)
    private byte[] keyBlob;

    @NotNull
    private String alias;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "hsmKey")
    private List<HsmCertificateChain> certificateChain = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private HsmKeyStore hsmKeyStore;

    public HsmKey() {
    }

    public HsmKey(byte[] keyBlob, String alias, List<HsmCertificateChain> certificateChain) {
        this.keyBlob = keyBlob;
        this.alias = alias;
        this.certificateChain = certificateChain;
    }

    @Override
    public Long getId() {
        return id;
    }

    public byte[] getKeyBlob() {
        return keyBlob;
    }

    public List<HsmCertificateChain> getCertificateChain() {
        if (certificateChain == null || certificateChain.isEmpty() || certificateChain.size() == 1) {
            return certificateChain;
        }
        final List<HsmCertificateChain> chain = new ArrayList<>(certificateChain.size());
        chain.addAll(certificateChain);
        chain.sort((o1, o2) -> Integer.compare(o1.getChainOrder(), o2.getChainOrder()));
        return chain;
    }

    public String getAlias() {
        return alias;
    }

    public HsmKeyStore getHsmKeyStore() {
        return hsmKeyStore;
    }

    public void setHsmKeyStore(HsmKeyStore hsmKeyStore) {
        this.hsmKeyStore = hsmKeyStore;
    }
}
