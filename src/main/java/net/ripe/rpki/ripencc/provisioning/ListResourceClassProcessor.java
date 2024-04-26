package net.ripe.rpki.ripencc.provisioning;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.provisioning.payload.common.CertificateElement;
import net.ripe.rpki.commons.provisioning.payload.common.GenericClassElementBuilder;
import net.ripe.rpki.commons.provisioning.payload.list.response.ResourceClassListResponseClassElement;
import net.ripe.rpki.commons.provisioning.payload.list.response.ResourceClassListResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.list.response.ResourceClassListResponsePayloadBuilder;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.ResourceCertificateData;
import net.ripe.rpki.server.api.ports.ResourceInformationNotAvailableException;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.read.ResourceCertificateViewService;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;

@Component
class ListResourceClassProcessor extends AbstractProvisioningProcessor {

    private final ResourceCertificateViewService resourceCertificateViewService;

    ListResourceClassProcessor(ResourceLookupService resourceLookupService, ResourceCertificateViewService resourceCertificateViewService) {
        super(resourceLookupService);
        this.resourceCertificateViewService = resourceCertificateViewService;
    }

    public ResourceClassListResponsePayload process(NonHostedCertificateAuthorityData nonHostedCertificateAuthority)
        throws ResourceInformationNotAvailableException
    {
        final ResourceClassListResponsePayloadBuilder responsePayloadBuilder = new ResourceClassListResponsePayloadBuilder();

        Optional<ResourceCertificateData> maybeIncomingResourceCertificate = resourceCertificateViewService
            .findCurrentIncomingResourceCertificate(nonHostedCertificateAuthority.getParentId());
        if (maybeIncomingResourceCertificate.isPresent()) {
            ResourceCertificateData currentIncomingResourceCertificate = maybeIncomingResourceCertificate.get();
            ImmutableResourceSet resources = getCertifiableResources(nonHostedCertificateAuthority, currentIncomingResourceCertificate);
            if (resources.isEmpty()) {
                return responsePayloadBuilder.build();
            }
            final ResourceClassListResponseClassElement classElement = new GenericClassElementBuilder()
                .withClassName(DEFAULT_RESOURCE_CLASS)
                .withIpResourceSet(new IpResourceSet(resources))
                .withCertificateAuthorityUri(Collections.singletonList(currentIncomingResourceCertificate.getPublicationUri()))
                .withIssuer(currentIncomingResourceCertificate.getCertificate())
                .withValidityNotAfter(CertificateAuthority.calculateValidityNotAfter(new DateTime()))
                .buildResourceClassListResponseClassElement();

            final List<CertificateElement> certificateElements = nonHostedCertificateAuthority.getPublicKeys().stream()
                    .filter(publicKeyData -> publicKeyData.getCurrentCertificate() != null)
                    .map(publicKeyData -> createClassElement(
                            publicKeyData.getCurrentCertificate().getCertificate(),
                            publicKeyData.getRequestedResourceSets(),
                            publicKeyData.getCurrentCertificate().getPublicationUri()
                    )).toList();

            classElement.setCertificateElements(certificateElements);
            responsePayloadBuilder.addClassElement(classElement);
        }

        return responsePayloadBuilder.build();
    }

}
