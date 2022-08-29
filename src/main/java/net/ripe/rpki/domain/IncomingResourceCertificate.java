package net.ripe.rpki.domain;

import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import org.apache.commons.lang.Validate;

import javax.persistence.CascadeType;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;
import java.net.URI;

/**
 * An incoming resource certificate is used by a {@link ManagedCertificateAuthority} to track its current set of
 * certifiable resources. It is a copy of the parent's {@link OutgoingResourceCertificate}.
 */
@Entity
@DiscriminatorValue(value = "INCOMING")
public class IncomingResourceCertificate extends ResourceCertificate {

    @NotNull
    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "subject_keypair_id")
    private KeyPairEntity subjectKeyPair;

    protected IncomingResourceCertificate() {
        super();
    }

    public IncomingResourceCertificate(X509ResourceCertificate certificate, URI publicationURI, KeyPairEntity subjectKeyPair) {
        super(certificate);
        setPublicationUri(publicationURI);
        Validate.notNull(subjectKeyPair, "subjectKeyPair is required");
        this.subjectKeyPair = subjectKeyPair;
        assertValid();
    }

    public void update(X509ResourceCertificate certificate, URI publicationURI) {
        updateCertificate(certificate);
        setPublicationUri(publicationURI);
    }

}
