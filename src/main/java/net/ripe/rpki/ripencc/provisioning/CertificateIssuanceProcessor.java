package net.ripe.rpki.ripencc.provisioning;

import com.google.common.collect.Iterators;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.ImmutableResourceSet;
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
import net.ripe.rpki.domain.RequestedResourceSets;
import net.ripe.rpki.server.api.commands.ProvisioningCertificateIssuanceCommand;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.ResourceCertificateData;
import net.ripe.rpki.server.api.ports.ResourceInformationNotAvailableException;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.ResourceCertificateViewService;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;

import java.math.BigInteger;
import java.net.URI;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;

@Slf4j
@Component
class CertificateIssuanceProcessor extends AbstractProvisioningProcessor {

    // Limits based on https://trac.ietf.org/trac/sidrops/wiki/FencingLimits
    public static final int SIA_URI_LENGTH_LIMIT = 2048;
    public static final int RESOURCE_SET_ENTRIES_LIMIT = 100_000;

    // See https://www.rfc-editor.org/rfc/rfc6485#section-3. These constants should be part of rpki-commons,
    // but are currently not exposed.
    public static final int REQUIRED_RPKI_PUBLIC_KEY_LENGTH = 2048;
    public static final BigInteger REQUIRED_RPKI_PUBLIC_KEY_EXPONENT = RSAKeyGenParameterSpec.F4;
    public static final String REQUIRED_RPKI_PUBLIC_KEY_ALGORITHM = "RSA";

    private final CommandService commandService;
    private final ResourceCertificateViewService resourceCertificateViewService;

    @Inject
    public CertificateIssuanceProcessor(
        ResourceLookupService resourceLookupService,
        CommandService commandService,
        ResourceCertificateViewService resourceCertificateViewService
    ) {
        super(resourceLookupService);
        this.commandService = commandService;
        this.resourceCertificateViewService = resourceCertificateViewService;
    }

    public CertificateIssuanceResponsePayload process(NonHostedCertificateAuthorityData nonHostedCertificateAuthority,
                                                      CertificateIssuanceRequestPayload requestPayload)
        throws ResourceInformationNotAvailableException
    {
        CertificateIssuanceRequestElement request = requestPayload.getRequestElement();
        if (!DEFAULT_RESOURCE_CLASS.equals(request.getClassName())) {
            throw new NotPerformedException(NotPerformedError.REQ_NO_SUCH_RESOURCE_CLASS);
        }

        ResourceCertificateData issuingCertificate = resourceCertificateViewService
            .findCurrentIncomingResourceCertificate(nonHostedCertificateAuthority.getParentId())
            .orElseThrow(() -> new NotPerformedException(NotPerformedError.REQ_NO_RESOURCES_ALLOTED_IN_RESOURCE_CLASS));

        RequestedResourceSets requestedResourceSets = new RequestedResourceSets(
            validateRequestedResources(request.getAllocatedAsn()),
            validateRequestedResources(request.getAllocatedIpv4()),
            validateRequestedResources(request.getAllocatedIpv6())
        );
        ImmutableResourceSet certifiableResources = getCertifiableResources(nonHostedCertificateAuthority, issuingCertificate);

        ImmutableResourceSet certificateResources = requestedResourceSets.calculateEffectiveResources(certifiableResources);
        if (certificateResources.isEmpty()) {
            // Note that we also return this error in case the non-hosted CA has certifiable resources, but they
            // do not match the requested resources. It is not clear from the RFC if this is the right error code
            // in this case.
            throw new NotPerformedException(NotPerformedError.REQ_NO_RESOURCES_ALLOTED_IN_RESOURCE_CLASS);
        }

        ResourceCertificateData outgoingResourceCertificate = issueCertificate(
            request, requestedResourceSets, nonHostedCertificateAuthority
        ).orElseThrow(() -> new NotPerformedException(NotPerformedError.REQ_NO_RESOURCES_ALLOTED_IN_RESOURCE_CLASS));

        return createResponse(request, requestedResourceSets, certifiableResources, outgoingResourceCertificate, issuingCertificate);
    }

    private Optional<ImmutableResourceSet> validateRequestedResources(IpResourceSet resourceSet) {
        if (resourceSet == null) {
            return Optional.empty();
        }

        int entries = Iterators.size(resourceSet.iterator());
        if (entries > RESOURCE_SET_ENTRIES_LIMIT) {
            throw badRequest("requested resource set exceeds entry limit (" + entries + " > " + RESOURCE_SET_ENTRIES_LIMIT + ")");
        }

        return Optional.of(ImmutableResourceSet.of(resourceSet));
    }

    private CertificateIssuanceResponsePayload createResponse(
        CertificateIssuanceRequestElement request,
        RequestedResourceSets requestedResourceSets,
        ImmutableResourceSet certifiableResources,
        ResourceCertificateData issuedCertificate, ResourceCertificateData issuingCertificate
    ) {
        CertificateElement certificateElement = createClassElement(issuedCertificate.getCertificate(), requestedResourceSets, issuedCertificate.getPublicationUri());

        CertificateIssuanceResponseClassElement classElement = buildClassElement(request,
            issuingCertificate, certifiableResources, certificateElement);

        CertificateIssuanceResponsePayloadBuilder responsePayloadBuilder = new CertificateIssuanceResponsePayloadBuilder();
        responsePayloadBuilder.withClassElement(classElement);

        return responsePayloadBuilder.build();
    }


    private CertificateIssuanceResponseClassElement buildClassElement(CertificateIssuanceRequestElement request,
                                                                      ResourceCertificateData currentIncomingResourceCertificate,
                                                                      ImmutableResourceSet ipResources,
                                                                      CertificateElement certificateElement) {
        return new GenericClassElementBuilder()
                .withClassName(request.getClassName())
                .withIssuer(currentIncomingResourceCertificate.getCertificate())
                .withCertificateAuthorityUri(Collections.singletonList(currentIncomingResourceCertificate.getPublicationUri()))
                .withValidityNotAfter(CertificateAuthority.calculateValidityNotAfter(new DateTime(DateTimeZone.UTC)))
                .withIpResourceSet(new IpResourceSet(ipResources))
                .withCertificateElements(Collections.singletonList(certificateElement))
                .buildCertificateIssuanceResponseClassElement();
    }

    private Optional<ResourceCertificateData> issueCertificate(CertificateIssuanceRequestElement request,
                                                               RequestedResourceSets requestedResourceSets,
                                                               NonHostedCertificateAuthorityData nonHostedCa) {
        RpkiCaCertificateRequestParser requestParser = parseCertificateRequest(request);

        PublicKey publicKey = validatePublicKey(requestParser.getPublicKey());
        List<X509CertificateInformationAccessDescriptor> sia = getSubjectInformationAccessDescriptors(requestParser);

        commandService.execute(new ProvisioningCertificateIssuanceCommand(
            nonHostedCa.getVersionedId(),
            publicKey,
            requestedResourceSets,
            sia
        ));

        return resourceCertificateViewService.findCurrentOutgoingResourceCertificate(nonHostedCa.getId(), publicKey);
    }

    private PublicKey validatePublicKey(PublicKey publicKey) {
        if (!REQUIRED_RPKI_PUBLIC_KEY_ALGORITHM.equals(publicKey.getAlgorithm()) || !(publicKey instanceof RSAPublicKey)) {
            throw badRequest("public key algorithm is not " + REQUIRED_RPKI_PUBLIC_KEY_ALGORITHM);
        }
        RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
        if (rsaPublicKey.getModulus().bitLength() != REQUIRED_RPKI_PUBLIC_KEY_LENGTH) {
            throw badRequest("public key size is not " + REQUIRED_RPKI_PUBLIC_KEY_LENGTH);
        }
        if (!REQUIRED_RPKI_PUBLIC_KEY_EXPONENT.equals(rsaPublicKey.getPublicExponent())) {
            throw badRequest("public key exponent is not " + REQUIRED_RPKI_PUBLIC_KEY_EXPONENT);
        }
        return publicKey;
    }

    private List<X509CertificateInformationAccessDescriptor> getSubjectInformationAccessDescriptors(RpkiCaCertificateRequestParser requestParser) {
        List<X509CertificateInformationAccessDescriptor> sia = new ArrayList<>();

        sia.add(new X509CertificateInformationAccessDescriptor(
            X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY,
            validateURI(requestParser.getCaRepositoryUri(), "rsync")
        ));
        sia.add(new X509CertificateInformationAccessDescriptor(
            X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST,
            validateURI(requestParser.getManifestUri(), "rsync")
        ));

        URI notificationUri = requestParser.getNotificationUri();
        if (notificationUri != null) {
            sia.add(new X509CertificateInformationAccessDescriptor(
                X509CertificateInformationAccessDescriptor.ID_AD_RPKI_NOTIFY,
                validateURI(notificationUri, "https")
            ));
        }

        return sia;
    }

    private URI validateURI(URI uri, String expectedScheme) {
        if (!uri.equals(uri.normalize())) {
            // URI contains relative path segments
            throw badRequest("SIA URI is not normalized");
        }
        String asciiString = uri.toASCIIString();
        if (asciiString.contains("/../")) {
            throw badRequest("SIA URI contains relative segments");
        }
        if (!uri.isAbsolute() || uri.isOpaque()) {
            throw badRequest("SIA URI is not absolute or is opaque");
        }
        if (!expectedScheme.equals(uri.getScheme())) {
            // Only allow lowercase scheme
            throw badRequest("SIA URI scheme is not correct");
        }
        int length = asciiString.length();
        if (length > SIA_URI_LENGTH_LIMIT) {
            throw badRequest("maximum SIA URI length exceeded (" + length + " > " + SIA_URI_LENGTH_LIMIT + ")");
        }
        return uri.normalize();
    }

    private RpkiCaCertificateRequestParser parseCertificateRequest(CertificateIssuanceRequestElement requestElement) {
        try {
            PKCS10CertificationRequest pkc10Request = requestElement.getCertificateRequest();
            return new RpkiCaCertificateRequestParser(pkc10Request);
        } catch (RpkiCaCertificateRequestParserException e) {
            log.error("Failed to parse certificate request", e);
            throw new NotPerformedException(NotPerformedError.REQ_BADLY_FORMED_CERTIFICATE_REQUEST);
        }
    }

    private NotPerformedException badRequest(String s) {
        return new NotPerformedException(NotPerformedError.REQ_BADLY_FORMED_CERTIFICATE_REQUEST, s);
    }
}
