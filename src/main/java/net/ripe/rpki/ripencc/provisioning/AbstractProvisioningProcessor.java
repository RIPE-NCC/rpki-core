package net.ripe.rpki.ripencc.provisioning;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.provisioning.payload.common.CertificateElement;
import net.ripe.rpki.domain.RequestedResourceSets;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.ports.ResourceLookupService;

import java.net.URI;
import java.util.Collections;

abstract class AbstractProvisioningProcessor {

    private final ResourceLookupService resourceLookupService;

    protected AbstractProvisioningProcessor(ResourceLookupService resourceLookupService) {
        this.resourceLookupService = resourceLookupService;
    }

    protected IpResourceSet getCertifiableResources(NonHostedCertificateAuthorityData nonHostedCertificateAuthority, HostedCertificateAuthorityData productionCA) {
        // We cannot use `nonHostedCertificateAuthority.getResources()` here since they only include _certified_
        // resources (which may be limited by the requested resource set) and we must include all _certifiable_
        // resources.
        IpResourceSet memberResources = resourceLookupService.lookupMemberCaPotentialResources(nonHostedCertificateAuthority.getName());
        memberResources.retainAll(productionCA.getResources());
        return memberResources;
    }

    protected CertificateElement createClassElement(X509ResourceCertificate certificate, RequestedResourceSets requestedResourceSets, URI publicationUri) {
        CertificateElement element = new CertificateElement();
        element.setCertificate(certificate);
        element.setIssuerCertificatePublicationLocation(Collections.singletonList(publicationUri));
        element.setAllocatedAsn(requestedResourceSets.getRequestedResourceSetAsn().orElse(null));
        element.setAllocatedIpv4(requestedResourceSets.getRequestedResourceSetIpv4().orElse(null));
        element.setAllocatedIpv6(requestedResourceSets.getRequestedResourceSetIpv6().orElse(null));
        return element;
    }
}
