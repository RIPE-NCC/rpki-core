package net.ripe.rpki.ripencc.provisioning;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.provisioning.payload.common.CertificateElement;
import net.ripe.rpki.commons.provisioning.payload.common.GenericClassElementBuilder;
import net.ripe.rpki.commons.provisioning.payload.list.response.ResourceClassListResponseClassElement;
import net.ripe.rpki.commons.provisioning.payload.list.response.ResourceClassListResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.list.response.ResourceClassListResponsePayloadBuilder;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import org.joda.time.DateTime;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;


public class ListResourceClassProcessor extends AbstractProvisioningProcessor {

    ListResourceClassProcessor(ResourceLookupService resourceLookupService) {
        super(resourceLookupService);
    }

    public ResourceClassListResponsePayload process(NonHostedCertificateAuthority nonHostedCertificateAuthority,
                                                    ProductionCertificateAuthority productionCA) {

        final ResourceClassListResponsePayloadBuilder responsePayloadBuilder = new ResourceClassListResponsePayloadBuilder();

        final IpResourceSet resources = getCertifiableResources(nonHostedCertificateAuthority, productionCA);
        if (!resources.isEmpty()) {
            productionCA.findCurrentIncomingResourceCertificate()
                .ifPresent(currentIncomingResourceCertificate -> {
                    final ResourceClassListResponseClassElement classElement = new GenericClassElementBuilder()
                        .withClassName(DEFAULT_RESOURCE_CLASS)
                        .withIpResourceSet(resources)
                        .withCertificateAuthorityUri(Collections.singletonList(currentIncomingResourceCertificate.getPublicationUri()))
                        .withIssuer(currentIncomingResourceCertificate.getCertificate())
                        .withValidityNotAfter(CertificateAuthority.calculateValidityNotAfter(new DateTime()))
                        .buildResourceClassListResponseClassElement();

                    final List<CertificateElement> certificateElements = nonHostedCertificateAuthority.getPublicKeys().stream()
                        .flatMap(publicKeyEntity -> publicKeyEntity.getOutgoingResourceCertificates().stream()
                            .filter(OutgoingResourceCertificate::isCurrent)
                            .map(certificate -> createClassElement(certificate, publicKeyEntity.getRequestedResourceSets())))
                        .collect(Collectors.toList());

                    classElement.setCertificateElements(certificateElements);
                    responsePayloadBuilder.addClassElement(classElement);
                });
        }

        return responsePayloadBuilder.build();
    }

}
