package net.ripe.rpki.domain;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.ta.domain.request.TaRequest;
import net.ripe.rpki.domain.rta.UpStreamCARequestEntity;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.security.auth.x500.X500Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Entity
@DiscriminatorValue(value = "ROOT")
public class ProductionCertificateAuthority extends HostedCertificateAuthority {

    @OneToOne
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
    public Optional<IpResourceSet> lookupCertifiableIpResources(ResourceLookupService resourceLookupService) {
        return resourceLookupService.lookupProductionCaResourcesSet();
    }

    public void setMyDownStreamProvisioningCommunicator(DownStreamProvisioningCommunicator myIdentityMaterial) {
        this.myDownStreamProvisioningCommunicator = myIdentityMaterial;
    }

    public DownStreamProvisioningCommunicator getMyDownStreamProvisioningCommunicator() {
        return myDownStreamProvisioningCommunicator;
    }

    public ProvisioningIdentityCertificate getProvisioningIdentityCertificate() {
        return myDownStreamProvisioningCommunicator.getProvisioningIdentityCertificate();
    }
}
