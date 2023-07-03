package net.ripe.rpki.domain;

import lombok.Getter;
import lombok.Setter;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
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
import java.util.UUID;


@Entity
@DiscriminatorValue(value = "ALL_RESOURCES")
public class AllResourcesCertificateAuthority extends ManagedCertificateAuthority {

    @Getter
    @Setter
    @OneToOne(mappedBy = "certificateAuthority", cascade = {javax.persistence.CascadeType.ALL}, orphanRemoval = true)
    private UpStreamCARequestEntity upStreamCARequestEntity;

    protected AllResourcesCertificateAuthority() {
    }

    public AllResourcesCertificateAuthority(long id, X500Principal name, UUID uuid) {
        super(id, name, uuid, null);
    }

    @Override
    public CertificateAuthorityType getType() {
        return CertificateAuthorityType.ALL_RESOURCES;
    }

    @Override
    public Optional<ResourceExtension> lookupCertifiableIpResources(ResourceLookupService resourceLookupService) {
        return Optional.of(ResourceExtension.ofResources(Resources.ALL_RESOURCES));
    }

    public void processCertifiableResources(KeyPairService keyPairService, CertificateRequestCreationService certificateRequestCreationService) {
        if (getKeyPairs().isEmpty()) {
            createNewKeyPair(keyPairService);
        }
        final List<TaRequest> signingRequests = new ArrayList<>(
                certificateRequestCreationService.requestAllResourcesCertificate(this));

        final TrustAnchorRequest trustAnchorRequest = certificateRequestCreationService.createTrustAnchorRequest(signingRequests);
        setUpStreamCARequestEntity(new UpStreamCARequestEntity(this, trustAnchorRequest));
    }

}
