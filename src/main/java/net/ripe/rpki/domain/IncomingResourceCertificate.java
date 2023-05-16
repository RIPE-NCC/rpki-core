package net.ripe.rpki.domain;

import lombok.Getter;
import lombok.NonNull;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
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
        setPublicationUri(issuanceResponse.getPublicationUri());
        this.inheritedResources = issuanceResponse.getInheritedResources();
        this.subjectKeyPair = subjectKeyPair;
        assertValid();
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
        return true;
    }

    public ImmutableResourceSet getCertifiedResources() {
        return inheritedResources.union(super.getResources());
    }
}
