package net.ripe.rpki.domain;

import lombok.Getter;
import lombok.NonNull;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.server.api.dto.OutgoingResourceCertificateStatus;
import org.apache.commons.lang3.Validate;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.validation.constraints.NotNull;
import java.net.URI;

import static java.util.Objects.requireNonNull;

@Entity
@DiscriminatorValue(value = "OUTGOING")
public class OutgoingResourceCertificate extends ResourceCertificate {

    @NotNull
    @ManyToOne
    @JoinColumn(name = "signing_keypair_id")
    private KeyPairEntity signingKeyPair;

    @Getter
    @NotNull
    @Enumerated(EnumType.STRING)
    private OutgoingResourceCertificateStatus status;

    /**
     * True if this resource certificate is published embedded inside another object (such as a ROA). False if this
     * resource certificate should be published on its own.
     */
    @Getter
    @NotNull
    private boolean embedded;

    @Column(name = "revocationtime")
    @Getter
    private DateTime revocationTime;

    @Getter
    @OneToOne(optional=true, cascade={CascadeType.PERSIST}, fetch = FetchType.LAZY)
    @JoinColumn(name="published_object_id", nullable=true)
    private PublishedObject publishedObject;

    @Getter
    @ManyToOne(targetEntity = CertificateAuthority.class, optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "requesting_ca_id", nullable = true)
    private ChildCertificateAuthority requestingCertificateAuthority;

    protected OutgoingResourceCertificate() {
    }

    OutgoingResourceCertificate(X509ResourceCertificate certificate, KeyPairEntity signingKeyPair, boolean embedded,
                                String filename, URI parentPublicationDirectory) {
        super(certificate);
        Validate.isTrue(embedded || filename != null, "embedded or filename must be set");
        Validate.isTrue(embedded || parentPublicationDirectory != null, "embedded or parentPublicationDirectory must be set");
        Validate.notNull(signingKeyPair);
        this.signingKeyPair = signingKeyPair;
        this.embedded = embedded;
        this.status = OutgoingResourceCertificateStatus.CURRENT;
        if (!embedded) {
            publishedObject = new PublishedObject(signingKeyPair, filename, getDerEncoded(), true, parentPublicationDirectory, getValidityPeriod());
            setPublicationUri(publishedObject.getUri());
        }
        revalidateCertificate();
    }

    public KeyPairEntity getSigningKeyPair() {
        return signingKeyPair;
    }

    public void setRequestingCertificateAuthority(@NonNull ChildCertificateAuthority requestingCertificateAuthority) {
        Validate.isTrue(isCurrent(), "only CURRENT certificate can have requesting child certificate authority");
        this.requestingCertificateAuthority = requireNonNull(requestingCertificateAuthority);
    }

    public boolean isCurrent() {
        return status == OutgoingResourceCertificateStatus.CURRENT;
    }

    public boolean isExpired() {
        return status == OutgoingResourceCertificateStatus.EXPIRED;
    }

    /**
     * @return true if this resource certificate should be published, false
     *         others.
     */
    public boolean isPublishable() {
        return publishedObject != null && publishedObject.isPublished();
    }

    public boolean isRevoked() {
        return status == OutgoingResourceCertificateStatus.REVOKED;
    }

    public boolean isValid() {
        return !isRevoked() && !isExpired();
    }

    public void revoke() {
        if (isValid()) {
            withdraw();
            requestingCertificateAuthority = null;
            status = OutgoingResourceCertificateStatus.REVOKED;
            revocationTime = new DateTime(DateTimeZone.UTC);
        }
    }

    public void expire(DateTime now) {
        Validate.isTrue(now.isAfter(getValidityPeriod().getNotValidAfter()), "certificate should not be expired yet!");
        if (!isExpired()) {
            withdraw();
            requestingCertificateAuthority = null;
            status = OutgoingResourceCertificateStatus.EXPIRED;
        }
    }

    private void withdraw() {
        if (publishedObject != null) {
            publishedObject.withdraw();
        }
    }
}
