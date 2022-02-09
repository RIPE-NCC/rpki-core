package net.ripe.rpki.ripencc.provisioning;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.provisioning.payload.common.CertificateElement;
import net.ripe.rpki.commons.provisioning.payload.common.GenericClassElementBuilder;
import net.ripe.rpki.commons.provisioning.payload.error.NotPerformedError;
import net.ripe.rpki.commons.provisioning.payload.issue.request.CertificateIssuanceRequestElement;
import net.ripe.rpki.commons.provisioning.payload.issue.request.CertificateIssuanceRequestPayload;
import net.ripe.rpki.commons.provisioning.payload.issue.response.CertificateIssuanceResponseClassElement;
import net.ripe.rpki.commons.provisioning.payload.issue.response.CertificateIssuanceResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.issue.response.CertificateIssuanceResponsePayloadBuilder;
import net.ripe.rpki.commons.provisioning.x509.pkcs10.RpkiCaCertificateRequestParser;
import net.ripe.rpki.commons.provisioning.x509.pkcs10.RpkiCaCertificateRequestParserException;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.PublicKeyEntity;
import net.ripe.rpki.domain.RequestedResourceSets;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.command.CommandService;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.joda.time.DateTime;

import java.net.URI;
import java.util.Collections;
import java.util.Optional;

import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;


public class CertificateIssuanceProcessor extends AbstractProvisioningProcessor {

    private final CommandService commandService;

    public CertificateIssuanceProcessor(ResourceLookupService resourceLookupService, CommandService commandService) {
        super(resourceLookupService);
        this.commandService = commandService;
    }

    public CertificateIssuanceResponsePayload process(NonHostedCertificateAuthority nonHostedCertificateAuthority,
                                                      ProductionCertificateAuthority productionCA,
                                                      CertificateIssuanceRequestPayload requestPayload) {

        CertificateIssuanceRequestElement request = requestPayload.getRequestElement();
        if (!DEFAULT_RESOURCE_CLASS.equals(request.getClassName())) {
            throw new NotPerformedException(NotPerformedError.REQ_NO_SUCH_RESOURCE_CLASS);
        }

        RequestedResourceSets requestedResourceSets = new RequestedResourceSets(
            Optional.ofNullable(request.getAllocatedAsn()),
            Optional.ofNullable(request.getAllocatedIpv4()),
            Optional.ofNullable(request.getAllocatedIpv6())
        );
        IpResourceSet certifiableResources = getCertifiableResources(nonHostedCertificateAuthority, productionCA);
        IpResourceSet certificateResources = requestedResourceSets.calculateEffectiveResources(certifiableResources);
        if (certificateResources.isEmpty()) {
            // Note that we also return this error in case the non-hosted CA has certifiable resources, but they
            // do not match the requested resources. It is not clear from the RFC if this is the right error code
            // in this case.
            throw new NotPerformedException(NotPerformedError.REQ_NO_RESOURCES_ALLOTED_IN_RESOURCE_CLASS);
        }

        Optional<OutgoingResourceCertificate> outgoingResourceCertificate = issueCertificate(
            request, nonHostedCertificateAuthority
        );

        return outgoingResourceCertificate
            .map(orc -> createResponse(productionCA, request, requestedResourceSets, certifiableResources, orc))
            .orElseThrow(() -> new NotPerformedException(NotPerformedError.REQ_NO_RESOURCES_ALLOTED_IN_RESOURCE_CLASS));
    }

    private CertificateIssuanceResponsePayload createResponse(
        ProductionCertificateAuthority productionCA,
        CertificateIssuanceRequestElement request,
        RequestedResourceSets requestedResourceSets,
        IpResourceSet certifiableResources,
        OutgoingResourceCertificate outgoingResourceCertificate
    ) {
        CertificateElement certificateElement = createClassElement(outgoingResourceCertificate, requestedResourceSets);

        CertificateIssuanceResponseClassElement classElement = buildClassElement(request,
            productionCA.getCurrentIncomingCertificate(), certifiableResources, certificateElement);

        CertificateIssuanceResponsePayloadBuilder responsePayloadBuilder = new CertificateIssuanceResponsePayloadBuilder();
        responsePayloadBuilder.withClassElement(classElement);

        return responsePayloadBuilder.build();
    }


    private CertificateIssuanceResponseClassElement buildClassElement(CertificateIssuanceRequestElement request,
                                                                      IncomingResourceCertificate currentIncomingResourceCertificate,
                                                                      IpResourceSet ipResources,
                                                                      CertificateElement certificateElement) {
        return new GenericClassElementBuilder()
                .withClassName(request.getClassName())
                .withIssuer(currentIncomingResourceCertificate.getCertificate())
                .withCertificateAuthorityUri(Collections.singletonList(currentIncomingResourceCertificate.getPublicationUri()))
                .withValidityNotAfter(CertificateAuthority.calculateValidityNotAfter(new DateTime()))
                .withIpResourceSet(ipResources)
                .withCertificateElements(Collections.singletonList(certificateElement))
                .buildCertificateIssuanceResponseClassElement();
    }

    private Optional<OutgoingResourceCertificate> issueCertificate(CertificateIssuanceRequestElement request,
                                                                   NonHostedCertificateAuthority nonHostedCa) {
        RpkiCaCertificateRequestParser requestParser = parseCertificateRequest(request);

        PublicKeyEntity publicKeyEntity = nonHostedCa.findOrCreatePublicKeyEntityByPublicKey(requestParser.getPublicKey());
        X509CertificateInformationAccessDescriptor[] sia = getSubjectInformationAccessDescriptors(requestParser);
        publicKeyEntity.setLatestIssuanceRequest(request, sia);

        commandService.execute(new UpdateAllIncomingResourceCertificatesCommand(nonHostedCa.getVersionedId()));

        return publicKeyEntity.findCurrentOutgoingResourceCertificate();
    }

    private X509CertificateInformationAccessDescriptor[] getSubjectInformationAccessDescriptors(RpkiCaCertificateRequestParser requestParser) {
        URI notificationUri = requestParser.getNotificationUri();
        if (notificationUri == null) {
            return new X509CertificateInformationAccessDescriptor[]{
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY,
                    requestParser.getCaRepositoryUri()),
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST,
                    requestParser.getManifestUri())
            };
        } else {
            return new X509CertificateInformationAccessDescriptor[]{
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY,
                    requestParser.getCaRepositoryUri()),
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST,
                    requestParser.getManifestUri()),
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_NOTIFY,
                    notificationUri),
            };
        }
    }

    private RpkiCaCertificateRequestParser parseCertificateRequest(CertificateIssuanceRequestElement requestElement) {
        try {
            PKCS10CertificationRequest pkc10Request = requestElement.getCertificateRequest();
            return new RpkiCaCertificateRequestParser(pkc10Request);
        } catch (RpkiCaCertificateRequestParserException e) {
            throw new NotPerformedException(NotPerformedError.REQ_BADLY_FORMED_CERTIFICATE_REQUEST);
        }
    }
}
