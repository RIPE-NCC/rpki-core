package net.ripe.rpki.domain.hsm;

import net.ripe.rpki.ncc.core.domain.support.EntitySupport;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hsm_key_store")
@SequenceGenerator(name = "seq_hsm_key_store", sequenceName = "seq_all", allocationSize = 1)
public class HsmKeyStore extends EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_hsm_key_store")
    private Long id;

    @Column(name = "hmac", nullable = false)
    private byte[] hmac;

    @NotNull
    private String name;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "hsmKeyStore")
    private List<HsmKey> hsmKeys = new ArrayList<>();

    public HsmKeyStore() {
    }

    public HsmKeyStore(byte[] hmac, String name, List<HsmKey> hsmKeys) {
        this.hmac = hmac;
        this.name = name;
        this.hsmKeys = new ArrayList<>(hsmKeys);
    }

    @Override
    public Long getId() {
        return id;
    }

    public byte[] getHmac() {
        return hmac;
    }

    public List<HsmKey> getHsmKeys() {
        return hsmKeys;
    }

    public String getName() {
        return name;
    }

    public void addKey(HsmKey k) {
        hsmKeys.add(k);
    }

    public void setHmac(byte[] hmac) {
        this.hmac = hmac;
    }

    public void replaceKey(HsmKey hsmKey) {
        hsmKeys.stream().filter(k -> name.equals(k.getAlias())).findFirst().ifPresent(k -> hsmKeys.remove(k));
        hsmKeys.add(hsmKey);
    }
}
