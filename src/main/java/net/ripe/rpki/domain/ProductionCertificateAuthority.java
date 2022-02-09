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

    public ProductionCertificateAuthority(long id, X500Principal name, AllResourcesCertificateAuthority parent, int randomSerialIncrement) {
        super(id, name, parent, randomSerialIncrement);
    }

    @Override
    public CertificateAuthorityType getType() {
        return CertificateAuthorityType.ROOT;
    }

    @Override
    public Optional<IpResourceSet> lookupCertifiableIpResources(ResourceLookupService resourceLookupService) {
        return resourceLookupService.lookupProductionCaResourcesSet();
    }

    /**
     * Let the CA create resource classes and request certificates as needed,
     * based on the IpResourceSet supplied and its internal state.
     * Will result in the creation of an UpstreamCARequest if new certificates needed to
     * be requested.
     */
    public void processCertifiableResources(IpResourceSet certifiableResources,
                                            KeyPairService keyPairService,
                                            CertificateRequestCreationService certificateRequestCreationService) {
        List<TaRequest> signingRequests = new ArrayList<>();
        if (!certifiableResources.isEmpty()) {
            if (getKeyPairs().isEmpty()) {
                createNewKeyPair(keyPairService);
            }
            signingRequests.addAll(certificateRequestCreationService.requestProductionCertificates(certifiableResources, this));
        }

        // TODO: Important when normal hosted CAs use this: Remove resource classes that disappeared & revoke the keys
        setUpStreamCARequestEntity(new UpStreamCARequestEntity(this,
            certificateRequestCreationService.createTrustAnchorRequest(signingRequests)));
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
