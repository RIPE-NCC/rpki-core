package net.ripe.rpki.domain;

import lombok.Getter;
import net.ripe.rpki.application.impl.ResourceCertificateInformationAccessStrategyBean;
import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.provisioning.payload.PayloadMessageType;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import net.ripe.rpki.server.api.dto.NonHostedPublicKeyData;

import javax.persistence.*;
import javax.security.auth.x500.X500Principal;
import javax.validation.constraints.NotNull;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * KeyPairs for remote (non-hosted) child CAs.
 * <p/>
 * We need to keep track so that we can sign / revoke certificates for these
 * key pairs.
 */
@Entity
@Table(name = "non_hosted_ca_public_key")
@SequenceGenerator(name = "pk_entity_seq_all", sequenceName = "seq_all", allocationSize = 1)
public class PublicKeyEntity extends EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pk_entity_seq_all")
    @Getter
    private Long id;

    @NotNull
    @Column(name = "encoded")
    private byte[] encoded;

    @OneToMany(cascade={})
    @JoinColumn(name="subject_public_key_id")
    private Collection<OutgoingResourceCertificate> outgoingResourceCertificates = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Getter
    private PayloadMessageType latestProvisioningRequestType;

    @Embedded
    private RequestedResourceSets requestedResourceSets;

    @ElementCollection
    @OrderColumn(name = "index")
    @CollectionTable(name="non_hosted_ca_public_key_requested_sia")
    private List<EmbeddedInformationAccessDescriptor> requestedSia = new ArrayList<>();

    protected PublicKeyEntity() {
    }

    public PublicKeyEntity(PublicKey publicKey) {
        this.encoded = publicKey.getEncoded();
    }

    public String getEncodedKeyIdentifier() {
        return KeyPairUtil.getEncodedKeyIdentifier(getPublicKey());
    }

    public PublicKey getPublicKey() {
        return KeyPairFactory.decodePublicKey(encoded);
    }

    public Collection<OutgoingResourceCertificate> getOutgoingResourceCertificates() {
        return Collections.unmodifiableCollection(outgoingResourceCertificates);
    }

    public void addOutgoingResourceCertificate(OutgoingResourceCertificate certificate) {
        if (!outgoingResourceCertificates.contains(certificate)) {
            outgoingResourceCertificates.add(certificate);
        }
    }

    public Optional<OutgoingResourceCertificate> findCurrentOutgoingResourceCertificate() {
        return outgoingResourceCertificates.stream().filter(OutgoingResourceCertificate::isCurrent).findFirst();
    }

    public boolean isRevoked() {
        return getLatestProvisioningRequestType() == PayloadMessageType.revoke;
    }

    public RequestedResourceSets getRequestedResourceSets() {
        // Hibernate will make an embedded object null if all its fields are null, so fix this here.
        return requestedResourceSets == null ? new RequestedResourceSets() : requestedResourceSets;
    }

    public List<X509CertificateInformationAccessDescriptor> getRequestedSia() {
        return requestedSia.stream().map(EmbeddedInformationAccessDescriptor::toDescriptor).collect(Collectors.toList());
    }

    public void setLatestIssuanceRequest(RequestedResourceSets requestedResourceSets, List<X509CertificateInformationAccessDescriptor> sia) {
        this.latestProvisioningRequestType = PayloadMessageType.issue;
        this.requestedResourceSets = requestedResourceSets;
        this.requestedSia = sia.stream().map(EmbeddedInformationAccessDescriptor::of).collect(Collectors.toList());
    }

    public void setLatestRevocationRequest() {
        this.latestProvisioningRequestType = PayloadMessageType.revoke;
        this.requestedResourceSets = new RequestedResourceSets();
        this.requestedSia = new ArrayList<>();
    }

    protected X500Principal getSubjectForCertificateRequest() {
        final Optional<OutgoingResourceCertificate> currentCertificate = findCurrentOutgoingResourceCertificate();
        if (currentCertificate.isPresent()) {
            return currentCertificate.get().getSubject();
        } else {
            ResourceCertificateInformationAccessStrategy ias = new ResourceCertificateInformationAccessStrategyBean();
            return ias.caCertificateSubject(getPublicKey());
        }
    }

    public NonHostedPublicKeyData toData() {
        return new NonHostedPublicKeyData(
            getPublicKey(),
            getLatestProvisioningRequestType(),
            getRequestedResourceSets(),
            findCurrentOutgoingResourceCertificate().map(OutgoingResourceCertificate::toData).orElse(null)
        );
    }
}
