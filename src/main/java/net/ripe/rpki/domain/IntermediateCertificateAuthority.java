package net.ripe.rpki.domain;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.ports.ResourceLookupService;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.security.auth.x500.X500Principal;
import java.util.Optional;
import java.util.UUID;

@Entity
@DiscriminatorValue(value = "INTERMEDIATE")
public class IntermediateCertificateAuthority extends ManagedCertificateAuthority {
    protected IntermediateCertificateAuthority() {
    }

    public IntermediateCertificateAuthority(long id, X500Principal name, UUID uuid, ManagedCertificateAuthority parent) {
        super(id, name, uuid, parent);
    }

    @Override
    public CertificateAuthorityType getType() {
        return CertificateAuthorityType.INTERMEDIATE;
    }

    @Override
    public Optional<ImmutableResourceSet> lookupCertifiableIpResources(ResourceLookupService resourceLookupService) {
        return resourceLookupService.lookupIntermediateCaResources(getName());
    }
}
