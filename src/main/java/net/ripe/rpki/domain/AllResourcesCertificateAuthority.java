package net.ripe.rpki.domain;

import lombok.Getter;
import lombok.Setter;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.ta.domain.request.TaRequest;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.domain.rta.UpStreamCARequestEntity;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.ports.ResourceLookupService;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.OneToOne;
import javax.security.auth.x500.X500Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Entity
@DiscriminatorValue(value = "ALL_RESOURCES")
public class AllResourcesCertificateAuthority extends ManagedCertificateAuthority {

    @Getter
    @Setter
    @OneToOne(mappedBy = "certificateAuthority", cascade = {javax.persistence.CascadeType.ALL}, orphanRemoval = true)
    private UpStreamCARequestEntity upStreamCARequestEntity;

    protected AllResourcesCertificateAuthority() {
    }

    public AllResourcesCertificateAuthority(long id, X500Principal name) {
        super(id, name, null);
    }

    @Override
    public CertificateAuthorityType getType() {
        return CertificateAuthorityType.ALL_RESOURCES;
    }

    @Override
    public Optional<ImmutableResourceSet> lookupCertifiableIpResources(ResourceLookupService resourceLookupService) {
        return Optional.of(Resources.ALL_RESOURCES);
    }

    public void processCertifiableResources(KeyPairService keyPairService, CertificateRequestCreationService certificateRequestCreationService) {
        if (getKeyPairs().isEmpty()) {
            createNewKeyPair(keyPairService);
        }
        final List<TaRequest> signingRequests = new ArrayList<>(
                certificateRequestCreationService.requestProductionCertificates(Resources.ALL_RESOURCES, this));

        final TrustAnchorRequest trustAnchorRequest = certificateRequestCreationService.createTrustAnchorRequest(signingRequests);
        setUpStreamCARequestEntity(new UpStreamCARequestEntity(this, trustAnchorRequest));
    }

    @Override
    public ResourceClassListResponse processResourceClassListQuery(ResourceClassListQuery query) {
        return new ResourceClassListResponse(query.getResources());
    }

    /**
     * Finds the revoked key, marks it as revoked, and withdraws any published objects for it
     */
    public void processRevokedKey(String encodedSKI, PublishedObjectRepository publishedObjectRepository) {
        final Optional<KeyPairEntity> kp = findKeyPairByEncodedPublicKey(encodedSKI);
        if (kp.isPresent()) {
            kp.get().revoke(publishedObjectRepository);
        } else {
            throw new CertificateAuthorityException("Unknown encoded key: " + encodedSKI);
        }
    }
}
