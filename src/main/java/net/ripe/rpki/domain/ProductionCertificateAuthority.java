package net.ripe.rpki.domain;

import lombok.Getter;
import lombok.Setter;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.security.auth.x500.X500Principal;
import java.util.Optional;


@Entity
@DiscriminatorValue(value = "ROOT")
public class ProductionCertificateAuthority extends ManagedCertificateAuthority {

    @Setter
    @Getter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "down_stream_provisioning_communicator_id", nullable = true)
    @Cascade(value = {CascadeType.ALL})
    private DownStreamProvisioningCommunicator myDownStreamProvisioningCommunicator;

    protected ProductionCertificateAuthority() {
    }

    public ProductionCertificateAuthority(long id, X500Principal name, AllResourcesCertificateAuthority parent) {
        super(id, name, parent);
    }

    @Override
    public CertificateAuthorityType getType() {
        return CertificateAuthorityType.ROOT;
    }

    @Override
    public Optional<ImmutableResourceSet> lookupCertifiableIpResources(ResourceLookupService resourceLookupService) {
        return resourceLookupService.lookupProductionCaResourcesSet();
    }

    public ProvisioningIdentityCertificate getProvisioningIdentityCertificate() {
        return myDownStreamProvisioningCommunicator.getProvisioningIdentityCertificate();
    }
}
