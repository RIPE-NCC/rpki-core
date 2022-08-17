package net.ripe.rpki.domain;

import lombok.NonNull;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.CustomerCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.KeyPairData;
import net.ripe.rpki.server.api.ports.ResourceLookupService;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.security.auth.x500.X500Principal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Locally hosted certificate authority.
 *
 * A hosted certificate authority receives it's certified resources from its parent.
 */
@Entity
@DiscriminatorValue(value = "HOSTED")
public class CustomerCertificateAuthority extends HostedCertificateAuthority {

    protected CustomerCertificateAuthority() { }

    public CustomerCertificateAuthority(long id, X500Principal name, @NonNull ParentCertificateAuthority parent) {
        super(id, name, parent);
    }

    @Override
    public CertificateAuthorityType getType() {
        return CertificateAuthorityType.HOSTED;
    }

    @Override
    public CustomerCertificateAuthorityData toData() {
        final List<KeyPairData> keys = getKeyPairs().stream()
            .map(KeyPairEntity::toData)
            .collect(Collectors.toList());

        return new CustomerCertificateAuthorityData(
            getVersionedId(), getName(), getUuid(),
            getParent().getId(),
            getCertifiedResources(), keys);
    }

    @Override
    public Optional<IpResourceSet> lookupCertifiableIpResources(ResourceLookupService resourceLookupService) {
        return Optional.of(resourceLookupService.lookupMemberCaPotentialResources(getName()));
    }

    @Override
    public CertificateRevocationResponse processCertificateRevocationRequest(CertificateRevocationRequest request,
                                                                             ResourceCertificateRepository resourceCertificateRepository) {
        throw new UnsupportedOperationException("Member CAs cannot handle this request");
    }

    @Override
    public ResourceClassListResponse processResourceClassListQuery(ResourceClassListQuery query) {
        // Customer certificate authorities currently are not allowed to be a parent CA. Once they are they can
        // return all their certifiable resources here.
        return new ResourceClassListResponse();
    }

}
