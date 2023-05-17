package net.ripe.rpki.ripencc.provisioning;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.payload.AbstractProvisioningPayload;
import net.ripe.rpki.commons.provisioning.payload.AbstractProvisioningResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.error.NotPerformedError;
import net.ripe.rpki.commons.provisioning.payload.error.RequestNotPerformedResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.error.RequestNotPerformedResponsePayloadBuilder;
import net.ripe.rpki.commons.provisioning.payload.issue.request.CertificateIssuanceRequestPayload;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayload;
import net.ripe.rpki.commons.provisioning.payload.revocation.request.CertificateRevocationRequestPayload;
import net.ripe.rpki.commons.provisioning.protocol.ResponseExceptionType;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.ports.ResourceInformationNotAvailableException;
import net.ripe.rpki.server.api.services.command.CertificationResourceLimitExceededException;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import javax.persistence.LockTimeoutException;
import javax.persistence.OptimisticLockException;
import javax.persistence.PessimisticLockException;
import java.util.Optional;
import java.util.UUID;


@Component
@Slf4j
class ProvisioningRequestProcessorBean implements ProvisioningRequestProcessor {

    private final CertificateAuthorityViewService certificateAuthorityViewService;
    private final ProvisioningCmsValidationStrategy provisioningValidator;
    private final ProvisioningCmsSigningTimeStore provisioningCmsSigningTimeStore;
    private final ProvisioningCmsResponseGenerator provisioningCmsResponseGenerator;
    private final ListResourceClassProcessor listResourceClassProcessor;
    private final CertificateIssuanceProcessor certificateIssuanceProcessor;
    private final CertificateRevocationProcessor certificateRevocationProcessor;

    @Autowired
    public ProvisioningRequestProcessorBean(
        CertificateAuthorityViewService certificateAuthorityViewService,
        ProvisioningCmsValidationStrategy provisioningValidator,
        ProvisioningCmsSigningTimeStore provisioningCmsSigningTimeStore,
        ProvisioningCmsResponseGenerator provisioningCmsResponseGenerator,
        ListResourceClassProcessor listResourceClassProcessor,
        CertificateIssuanceProcessor certificateIssuanceProcessor,
        CertificateRevocationProcessor certificateRevocationProcessor
    ) {
        this.certificateAuthorityViewService = certificateAuthorityViewService;
        this.provisioningCmsSigningTimeStore = provisioningCmsSigningTimeStore;
        this.provisioningCmsResponseGenerator = provisioningCmsResponseGenerator;
        this.certificateIssuanceProcessor = certificateIssuanceProcessor;
        this.listResourceClassProcessor = listResourceClassProcessor;
        this.certificateRevocationProcessor = certificateRevocationProcessor;
        this.provisioningValidator = provisioningValidator;
    }

    @Override
    public ProvisioningCmsObject process(ProvisioningCmsObject request) {
        UUID memberUuid = parseSenderAndRecipientUUID(request.getPayload().getSender());
        UUID recipientUuid = parseSenderAndRecipientUUID(request.getPayload().getRecipient());

        // Gets the NonHostedCertificateAuthority and validates the (request) CMS object, including the check
        // that the object is from the correct CA.
        NonHostedCertificateAuthorityData nonHostedMemberCa = getNonHostedCertificateAuthorityWithProvisioningCertificateSigning(memberUuid, request);

        // Update last seen signingTime for the member CA
        provisioningCmsSigningTimeStore.updateLastSeenProvisioningCmsSeenAt(nonHostedMemberCa, request.getSigningTime());

        validateRecipientIsProductionCA(recipientUuid);
        validateParentOfNonHostedMemberCA(nonHostedMemberCa);

        AbstractProvisioningResponsePayload responsePayload = processRequestPayload(nonHostedMemberCa, request.getPayload());

        responsePayload.setRecipient(request.getPayload().getSender());
        responsePayload.setSender(request.getPayload().getRecipient());

        return provisioningCmsResponseGenerator.createProvisioningCmsResponseObject(responsePayload);
    }

    @VisibleForTesting
    protected AbstractProvisioningResponsePayload processRequestPayload(NonHostedCertificateAuthorityData nonHostedMemberCa,
                                                                        AbstractProvisioningPayload requestPayload) {
        try {
            if (requestPayload instanceof ResourceClassListQueryPayload) {
                return listResourceClassProcessor.process(nonHostedMemberCa);
            } else if (requestPayload instanceof CertificateIssuanceRequestPayload) {
                return certificateIssuanceProcessor.process(nonHostedMemberCa, (CertificateIssuanceRequestPayload) requestPayload);
            } else if (requestPayload instanceof CertificateRevocationRequestPayload) {
                return certificateRevocationProcessor.process(nonHostedMemberCa, (CertificateRevocationRequestPayload) requestPayload);
            } else {
                return buildError(NotPerformedError.UNRECOGNIZED_REQUEST_TYPE);
            }
        } catch (OptimisticLockException | PessimisticLockException | TransientDataAccessException | LockTimeoutException e) {
            // https://datatracker.ietf.org/doc/html/rfc6492#section-3
            // Lock the CA because multiple concurrent requests with a common sender
            // MUST be detected and rejected with an error response (i.e., an error code 1101 response).

            log.info("Concurrent operation for non-hosted CA for memberUuid: {}", nonHostedMemberCa.getUuid());

            // Only possible iff javax.persistence.lock.timeout != -1
            return buildError(NotPerformedError.ALREADY_PROCESSING_REQUEST);
        } catch (ResourceInformationNotAvailableException e) {
            return buildErrorWithDescription(NotPerformedError.INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (NotPerformedException e) {
            return buildErrorWithDescription(e.getNotPerformedError(), e.getMessage());
        } catch (CertificationResourceLimitExceededException e) {
            // Unfortunately RFC6492 does not define a more appropriate error code for this case.
            return buildErrorWithDescription(NotPerformedError.INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (Exception e) {
            log.error("Not able to process provisioning request", e);
            return buildErrorWithDescription(NotPerformedError.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private RequestNotPerformedResponsePayload buildError(NotPerformedError errorType) {
        return buildErrorWithDescription(errorType, null);
    }

    private RequestNotPerformedResponsePayload buildErrorWithDescription(NotPerformedError errorType, String description) {
        RequestNotPerformedResponsePayloadBuilder errorResponseBuilder = new RequestNotPerformedResponsePayloadBuilder();
        errorResponseBuilder.withError(errorType);
        errorResponseBuilder.withDescription(description);
        return errorResponseBuilder.build();
    }

    public static UUID parseSenderAndRecipientUUID(String uuid) {
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            throw new ProvisioningException(ResponseExceptionType.BAD_SENDER_AND_RECIPIENT);
        }
    }

    /**
     * Get the production CA by UUID.
     *
     * @ensures \result is the production CA.
     */
    private void validateRecipientIsProductionCA(UUID recipientUUID) {
        CertificateAuthorityData recipient = certificateAuthorityViewService.findCertificateAuthorityByTypeAndUuid(ProductionCertificateAuthority.class, recipientUUID);
        if (recipient == null || recipient.getType() != CertificateAuthorityType.ROOT) {
            throw new ProvisioningException(ResponseExceptionType.UNKNOWN_RECIPIENT);
        }
    }

    private void validateParentOfNonHostedMemberCA(NonHostedCertificateAuthorityData nonHostedMemberCa) {
        CertificateAuthorityData parent = certificateAuthorityViewService.findCertificateAuthority(nonHostedMemberCa.getParentId());
        if (parent == null || parent.getType() != CertificateAuthorityType.ROOT && parent.getType() != CertificateAuthorityType.INTERMEDIATE) {
            throw new IllegalStateException(String.format("parent CA of '%s' does not exist or has wrong type", nonHostedMemberCa.getName()));
        }
    }

    /**
     * Get the non-hosted CA for the member UUID.
     *
     * @param unvalidatedProvisioningObject the parsed, but unvalidated CMS object containing the up-down request
     * @ensures unvalidatedProvisioningObject is validated (EE cert is current, etc) and \result's provisioning identity certificate signed over certificateSigningCMS
     */
    private NonHostedCertificateAuthorityData getNonHostedCertificateAuthorityWithProvisioningCertificateSigning(UUID memberUuid, ProvisioningCmsObject unvalidatedProvisioningObject) throws ProvisioningException {
        CertificateAuthorityData ca = certificateAuthorityViewService.findCertificateAuthorityByTypeAndUuid(NonHostedCertificateAuthority.class, memberUuid);

        if (ca instanceof NonHostedCertificateAuthorityData) {
            NonHostedCertificateAuthorityData result = (NonHostedCertificateAuthorityData) ca;

            Optional<DateTime> lastSigningTimeForCA = provisioningCmsSigningTimeStore.getLastSeenProvisioningCmsSignedAt(result);
            provisioningValidator.validateProvisioningCmsAndIdentityCertificate(unvalidatedProvisioningObject, lastSigningTimeForCA, result.getProvisioningIdentityCertificate());

            return result;
        }

        throw new ProvisioningException(ResponseExceptionType.UNKNOWN_SENDER);
    }
}
