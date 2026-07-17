package net.ripe.rpki.domain.bgpsec;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.domain.PublishedObject;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;

import java.net.URI;

@jakarta.persistence.Entity
@Table(name = "bgpsec_entity")
@SequenceGenerator(name = "seq_bgpsecentity", sequenceName = "seq_all", allocationSize = 1)
@NoArgsConstructor
public class BgpSecEntity extends EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_bgpsecentity")
    @Getter
    private Long id;

    @Column(name = "asn", nullable = false)
    @Getter
    private Asn asn;

    @Column(name = "key_identifier", nullable = false)
    @Getter
    private String keyIdentifier;

    @Column(name = "router_id")
    @Getter
    private Long routerId;

    @OneToOne(optional = false)
    @JoinColumn(name = "certificate_id")
    @Getter
    private BgpSecCertificate certificate;

    @OneToOne(optional = false, cascade = {CascadeType.PERSIST}, fetch = FetchType.LAZY)
    @JoinColumn(name = "published_object_id", nullable = false)
    @Getter
    private PublishedObject publishedObject;

    public BgpSecEntity(Asn asn, String keyIdentifier, Long routerId, BgpSecCertificate certificate, String filename, URI directory) {
        this.asn = asn;
        this.keyIdentifier = keyIdentifier;
        this.routerId = routerId;
        this.certificate = certificate;
        this.publishedObject = new PublishedObject(certificate.getSigningKeyPair(),
                filename, certificate.getCertificate().getEncoded(), true, directory,
                certificate.getValidityPeriod(), certificate.getCreatedAt().toDateTime());
    }

    public void revokeAndRemove(BgpSecEntityRepository repository) {
        getCertificate().revoke();
        publishedObject.withdraw();
        repository.remove(this);
    }

    public boolean isPublished() {
        return publishedObject.isPublished();
    }

    public void withdraw() {
        publishedObject.withdraw();
    }

}