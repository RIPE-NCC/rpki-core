package net.ripe.rpki.domain.roa;

import net.ripe.ipresource.Asn;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCms;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCmsParser;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.PublishedObject;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import net.ripe.rpki.server.api.dto.RoaEntityData;
import org.apache.commons.lang.Validate;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.net.URI;

/**
 * Entity for managing generated and published ROAs. ROAs are generated and
 * published based on a CA's ROA specifications and the CA's incoming
 * certificates. Whenever this configuration changes ROAs may need to be
 * generated, replaced, or removed.
 */
@Entity
@Table(name = "roaentity")
@SequenceGenerator(name = "seq_roaentity", sequenceName = "seq_all", allocationSize=1)
public class RoaEntity extends EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_roaentity")
    private Long id;

    @OneToOne(optional = false)
    private OutgoingResourceCertificate certificate;

    @OneToOne(optional = false, cascade = {CascadeType.PERSIST})
    @JoinColumn(name = "published_object_id", nullable = false)
    private PublishedObject publishedObject;

    public RoaEntity() {
    }

    public RoaEntity(OutgoingResourceCertificate eeCertificate, RoaCms roaCms, String filename, URI directory) {
        super();
        Validate.notNull(eeCertificate);
        Validate.notNull(roaCms);
        this.certificate = eeCertificate;
        this.publishedObject = new PublishedObject(
                eeCertificate.getSigningKeyPair(), filename, roaCms.getEncoded(), true, directory, roaCms.getValidityPeriod());
    }

    @Override
    public Long getId() {
        return id;
    }

    public OutgoingResourceCertificate getCertificate() {
        return certificate;
    }

    public RoaCms getRoaCms() {
        RoaCmsParser parser = new RoaCmsParser();
        parser.parse("roa", publishedObject.getContent());
        return parser.getRoaCms();
    }

    public PublishedObject getPublishedObject() {
        return publishedObject;
    }

    public Asn getAsn() {
        return getRoaCms().getAsn();
    }

    public boolean isRevoked() {
        return getCertificate().isRevoked();
    }

    public void revoke() {
        getCertificate().revoke();
        publishedObject.withdraw();
    }

    public boolean isPublished() {
        return publishedObject.isPublished();
    }

    public void withdraw() {
        publishedObject.withdraw();
    }

    public RoaEntityData toData() {
        return new RoaEntityData(getRoaCms(), getId(), getCertificate().getId(), publishedObject.getFilename());
    }
}