package net.ripe.rpki.domain;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import org.apache.commons.lang.Validate;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.security.auth.x500.X500Principal;
import java.util.Optional;

/**
 * Locally hosted certificate authority.
 *
 * A hosted certificate authority receives it's certified resources from its parent.
 */
@Entity
@DiscriminatorValue(value = "HOSTED")
public class CustomerCertificateAuthority extends HostedCertificateAuthority {

    protected CustomerCertificateAuthority() { }

    public CustomerCertificateAuthority(long id, X500Principal name, ParentCertificateAuthority parent, int randomSerialIncrement) {
        super(id, name, parent, randomSerialIncrement);
        Validate.notNull(parent);
    }

    @Override
    public CertificateAuthorityType getType() {
        return CertificateAuthorityType.HOSTED;
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
