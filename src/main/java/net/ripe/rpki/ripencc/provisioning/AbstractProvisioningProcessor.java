package net.ripe.rpki.ripencc.provisioning;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.provisioning.payload.common.CertificateElement;
import net.ripe.rpki.domain.RequestedResourceSets;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.ResourceCertificateData;
import net.ripe.rpki.server.api.ports.ResourceInformationNotAvailableException;
import net.ripe.rpki.server.api.ports.ResourceLookupService;

import java.net.URI;
import java.util.Collections;

abstract class AbstractProvisioningProcessor {

    private final ResourceLookupService resourceLookupService;

    protected AbstractProvisioningProcessor(ResourceLookupService resourceLookupService) {
        this.resourceLookupService = resourceLookupService;
    }

    protected ImmutableResourceSet getCertifiableResources(NonHostedCertificateAuthorityData nonHostedCertificateAuthority, ResourceCertificateData productionCertificate) {
        // We cannot use `nonHostedCertificateAuthority.getResources()` here since they only include _certified_
        // resources (which may be limited by the requested resource set) and we must include all _certifiable_
        // resources.
        try {
            ImmutableResourceSet memberResources = resourceLookupService.lookupMemberCaPotentialResources(nonHostedCertificateAuthority.getName())
                .map(ResourceExtension::getResources)
                .orElse(ImmutableResourceSet.empty());
            return memberResources.intersection(productionCertificate.getCertificate().resources());
        } catch (ResourceInformationNotAvailableException e) {
            return ImmutableResourceSet.empty();
        }
    }

    protected CertificateElement createClassElement(X509ResourceCertificate certificate, RequestedResourceSets requestedResourceSets, URI publicationUri) {
        CertificateElement element = new CertificateElement();
        element.setCertificate(certificate);
        element.setIssuerCertificatePublicationLocation(Collections.singletonList(publicationUri));
        element.setAllocatedAsn(requestedResourceSets.getRequestedResourceSetAsn().map(IpResourceSet::new).orElse(null));
        element.setAllocatedIpv4(requestedResourceSets.getRequestedResourceSetIpv4().map(IpResourceSet::new).orElse(null));
        element.setAllocatedIpv6(requestedResourceSets.getRequestedResourceSetIpv6().map(IpResourceSet::new).orElse(null));
        return element;
    }
}
