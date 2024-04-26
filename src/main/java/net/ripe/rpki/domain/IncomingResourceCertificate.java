package net.ripe.rpki.domain;

import lombok.Getter;
import lombok.NonNull;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import org.apache.commons.lang3.Validate;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Objects;

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

    @NotNull
    @Column(name = "inherited_resources", nullable = false)
    @Getter
    private ImmutableResourceSet inheritedResources;

    protected IncomingResourceCertificate() {
        super();
    }

    public IncomingResourceCertificate(@NonNull CertificateIssuanceResponse issuanceResponse, @NonNull KeyPairEntity subjectKeyPair) {
        super(issuanceResponse.getCertificate());
        Validate.notNull(issuanceResponse, "issuance response is required");
        setPublicationUri(issuanceResponse.getPublicationUri());
        this.inheritedResources = issuanceResponse.getInheritedResources();
        this.subjectKeyPair = subjectKeyPair;
        revalidate();
    }

    public boolean update(CertificateIssuanceResponse issuanceResponse) {
        if (Arrays.equals(getDerEncoded(), issuanceResponse.getCertificate().getEncoded())
            && Objects.equals(getPublicationUri(), issuanceResponse.getPublicationUri())
            && Objects.equals(getInheritedResources(), issuanceResponse.getInheritedResources())) {
            return false;
        }

        updateCertificate(issuanceResponse.getCertificate());
        setPublicationUri(issuanceResponse.getPublicationUri());
        this.inheritedResources = issuanceResponse.getInheritedResources();
        revalidate();
        return true;
    }

    public ImmutableResourceSet getCertifiedResources() {
        return inheritedResources.union(super.getResources());
    }

    protected void revalidate() {
        Validate.notNull(subjectKeyPair, "subject keypair is required");
        Validate.notNull(inheritedResources, "inhereted resources are required");
        revalidateCertificate();
    }
}
