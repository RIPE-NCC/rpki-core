package net.ripe.rpki.domain.aspa;

import lombok.Getter;
import net.ripe.rpki.commons.crypto.cms.aspa.AspaCms;
import net.ripe.rpki.commons.crypto.cms.aspa.AspaCmsParser;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.PublishedObject;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
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
import javax.persistence.Transient;
import java.net.URI;

@Entity
@Table(name = "aspaentity")
@SequenceGenerator(name = "seq_aspaentity", sequenceName = "seq_all", allocationSize=1)
public class AspaEntity extends EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_aspaentity")
    @Getter
    private Long id;

    @OneToOne(optional = false)
    @Getter
    private OutgoingResourceCertificate certificate;

    @OneToOne(optional = false, cascade = {CascadeType.PERSIST})
    @JoinColumn(name = "published_object_id", nullable = false)
    @Getter
    private PublishedObject publishedObject;

    public AspaEntity() {
    }

    public AspaEntity(OutgoingResourceCertificate eeCertificate, AspaCms aspaCms, String filename, URI directory) {
        super();
        Validate.notNull(eeCertificate);
        Validate.notNull(aspaCms);
        this.certificate = eeCertificate;
        this.publishedObject = new PublishedObject(
                eeCertificate.getSigningKeyPair(), filename, aspaCms.getEncoded(), true, directory, aspaCms.getValidityPeriod());
    }

    @Transient
    private AspaCms cms;

    public synchronized AspaCms getAspaCms() {
        if (cms == null) {
            final AspaCmsParser parser = new AspaCmsParser();
            parser.parse("asa", publishedObject.getContent());
            cms = parser.getAspa();
        }
        return cms;
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
}
