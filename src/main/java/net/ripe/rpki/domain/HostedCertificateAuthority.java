package net.ripe.rpki.domain;

import lombok.NonNull;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.KeyPairData;
import net.ripe.rpki.server.api.ports.ResourceInformationNotAvailableException;
import net.ripe.rpki.server.api.ports.ResourceLookupService;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import javax.security.auth.x500.X500Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Locally hosted certificate authority on behalf of a RIPE NCC member.
 *
 * <p>A hosted certificate authority receives it's certified resources from its parent.</p>
 */
@Entity
@DiscriminatorValue(value = "HOSTED")
public class HostedCertificateAuthority extends ManagedCertificateAuthority {

    protected HostedCertificateAuthority() { }

    public HostedCertificateAuthority(long id, @NonNull X500Principal name, @NonNull UUID uuid, @NonNull ParentCertificateAuthority parent) {
        super(id, name, uuid, parent);
    }

    @Override
    public CertificateAuthorityType getType() {
        return CertificateAuthorityType.HOSTED;
    }

    @Override
    public HostedCertificateAuthorityData toData() {
        final List<KeyPairData> keys = getKeyPairs().stream()
                .map(KeyPairEntity::toData).toList();

        return new HostedCertificateAuthorityData(
            getVersionedId(), getName(), getUuid(),
            getParent().getId(),
            getCertifiedResources(), keys);
    }

    @Override
    public Optional<ResourceExtension> lookupCertifiableIpResources(ResourceLookupService resourceLookupService) throws ResourceInformationNotAvailableException {
        return resourceLookupService.lookupMemberCaPotentialResources(getName());
    }

    @Override
    public CertificateRevocationResponse processCertificateRevocationRequest(CertificateRevocationRequest request,
                                                                             ResourceCertificateRepository resourceCertificateRepository) {
        throw new UnsupportedOperationException("Member CAs cannot handle this request");
    }

    @Override
    public ResourceClassListResponse processResourceClassListQuery(ResourceClassListQuery query) {
        // Hosted certificate authorities currently are not allowed to be a parent CA. Once they are they can
        // return all their certifiable resources here.
        return new ResourceClassListResponse(Optional.empty());
    }

}
