package net.ripe.rpki.ripencc.provisioning;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.provisioning.payload.common.CertificateElement;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.RequestedResourceSets;
import net.ripe.rpki.domain.ResourceClassListQuery;
import net.ripe.rpki.domain.ResourceClassListResponse;
import net.ripe.rpki.server.api.ports.ResourceLookupService;

import java.util.Collections;

public abstract class AbstractProvisioningProcessor {

    private final ResourceLookupService resourceLookupService;

    protected AbstractProvisioningProcessor(ResourceLookupService resourceLookupService) {
        this.resourceLookupService = resourceLookupService;
    }

    protected IpResourceSet getCertifiableResources(NonHostedCertificateAuthority nonHostedCertificateAuthority, ProductionCertificateAuthority productionCA) {
        IpResourceSet memberResources = resourceLookupService.lookupMemberCaPotentialResources(nonHostedCertificateAuthority.getName());
        ResourceClassListResponse resources = productionCA.processResourceClassListQuery(new ResourceClassListQuery(memberResources));
        return resources.getCertifiableResources();
    }

    protected CertificateElement createClassElement(OutgoingResourceCertificate certificate, RequestedResourceSets requestedResourceSets) {
        CertificateElement element = new CertificateElement();
        element.setCertificate(certificate.getCertificate());
        element.setIssuerCertificatePublicationLocation(Collections.singletonList(certificate.getPublicationUri()));
        element.setAllocatedAsn(requestedResourceSets.getRequestedResourceSetAsn().orElse(null));
        element.setAllocatedIpv4(requestedResourceSets.getRequestedResourceSetIpv4().orElse(null));
        element.setAllocatedIpv6(requestedResourceSets.getRequestedResourceSetIpv6().orElse(null));
        return element;
    }
}
