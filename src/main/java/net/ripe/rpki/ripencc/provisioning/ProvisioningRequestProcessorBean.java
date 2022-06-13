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
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CertificationResourceLimitExceededException;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.LockTimeoutException;
import java.util.Objects;
import java.util.UUID;


@Component
@Slf4j
class ProvisioningRequestProcessorBean implements ProvisioningRequestProcessor {

    private final CertificateAuthorityViewService certificateAuthorityViewService;
    private final ProvisioningCmsValidationStrategy provisioningValidator;
    private final ProvisioningCmsResponseGenerator provisioningCmsResponseGenerator;
    private final ListResourceClassProcessor listResourceClassProcessor;
    private final CertificateIssuanceProcessor certificateIssuanceProcessor;
    private final CertificateRevocationProcessor certificateRevocationProcessor;

    @Autowired
    public ProvisioningRequestProcessorBean(
        CertificateAuthorityViewService certificateAuthorityViewService,
        ProvisioningCmsValidationStrategy provisioningValidator,
        ProvisioningCmsResponseGenerator provisioningCmsResponseGenerator,
        ListResourceClassProcessor listResourceClassProcessor,
        CertificateIssuanceProcessor certificateIssuanceProcessor,
        CertificateRevocationProcessor certificateRevocationProcessor
    ) {
        this.certificateAuthorityViewService = certificateAuthorityViewService;
        this.provisioningCmsResponseGenerator = provisioningCmsResponseGenerator;
        this.certificateIssuanceProcessor = certificateIssuanceProcessor;
        this.listResourceClassProcessor = listResourceClassProcessor;
        this.certificateRevocationProcessor = certificateRevocationProcessor;
        this.provisioningValidator = provisioningValidator;
    }

    @Override
    public ProvisioningCmsObject process(ProvisioningCmsObject request) {
        UUID memberUuid = parseSenderAndRecipientUUID(request.getPayload().getSender());
        UUID productionCaUuid = parseSenderAndRecipientUUID(request.getPayload().getRecipient());

        try {
            NonHostedCertificateAuthorityData nonHostedMemberCa = getNonHostedCertificateAuthorityWithProvisioningCertificateSigning(memberUuid, request);

            HostedCertificateAuthorityData productionCA = getProductionCertificateAuthorityWhichIsParentOf(productionCaUuid, nonHostedMemberCa);

            AbstractProvisioningResponsePayload responsePayload = processRequestPayload(nonHostedMemberCa, productionCA, request.getPayload());

            responsePayload.setRecipient(request.getPayload().getSender());
            responsePayload.setSender(request.getPayload().getRecipient());

            return provisioningCmsResponseGenerator.createProvisioningCmsResponseObject(responsePayload);
        } catch (LockTimeoutException e) {
            // TODO (2021-12-29, TdK): Because we use a javax.persistence.lock.timeout of -1 the pessimistic write lock
            //  (under water: a `SELECT ... FOR UPDATE`) will serialise operations instead of rejecting them.
            //  Thus catching the exception is a noop. We should lower this timeout or use pg_try_advisory_xact_lock.

            // https://datatracker.ietf.org/doc/html/rfc6492#section-3
            // Lock the CA because multiple concurrent requests with a common sender
            // MUST be detected and rejected with an error response (i.e., an error code 1101 response).

            log.info("Concurrent operation for non-hosted CA for memberUuid: {}", memberUuid);

            // Only possible iff javax.persistence.lock.timeout != -1
            throw new NotPerformedException(NotPerformedError.ALREADY_PROCESSING_REQUEST);
        }
    }

    @VisibleForTesting
    protected AbstractProvisioningResponsePayload processRequestPayload(NonHostedCertificateAuthorityData nonHostedMemberCa,
                                                                        HostedCertificateAuthorityData productionCA,
                                                                        AbstractProvisioningPayload requestPayload) {
        try {
            if (requestPayload instanceof ResourceClassListQueryPayload) {
                return listResourceClassProcessor.process(nonHostedMemberCa, productionCA);
            } else if (requestPayload instanceof CertificateIssuanceRequestPayload) {
                return certificateIssuanceProcessor.process(nonHostedMemberCa, productionCA, (CertificateIssuanceRequestPayload) requestPayload);
            } else if (requestPayload instanceof CertificateRevocationRequestPayload) {
                return certificateRevocationProcessor.process(nonHostedMemberCa, (CertificateRevocationRequestPayload) requestPayload);
            } else {
                return buildError(NotPerformedError.UNRECOGNIZED_REQUEST_TYPE);
            }
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
     * @param nonHostedMemberCa child CA of the parent
     * @ensures \result is the parent of nonHostedMemberCa.
     */
    private HostedCertificateAuthorityData getProductionCertificateAuthorityWhichIsParentOf(UUID parentId, NonHostedCertificateAuthorityData nonHostedMemberCa) {
        CertificateAuthorityData parent = certificateAuthorityViewService.findCertificateAuthorityByTypeAndUuid(CertificateAuthorityType.ROOT, parentId);
        if (!(parent instanceof HostedCertificateAuthorityData)) {
            throw new ProvisioningException(ResponseExceptionType.UNKNOWN_RECIPIENT);
        }

        if (!Objects.equals(parent.getId(), nonHostedMemberCa.getParentId())) {
            throw new ProvisioningException(ResponseExceptionType.BAD_SENDER_AND_RECIPIENT);
        }

        return (HostedCertificateAuthorityData) parent;
    }

    /**
     * Get the non-hosted CA for the member UUID.
     *
     * @param unvalidatedProvisioningObject the parsed, but unvalidated CMS object containing the up-down request
     * @ensures unvalidatedProvisioningObject is validated (EE cert is current, etc) and \result's provisioning identity certificate signed over certificateSigningCMS
     */
    private NonHostedCertificateAuthorityData getNonHostedCertificateAuthorityWithProvisioningCertificateSigning(UUID memberUuid, ProvisioningCmsObject unvalidatedProvisioningObject) throws ProvisioningException {
        CertificateAuthorityData ca = certificateAuthorityViewService.findCertificateAuthorityByTypeAndUuid(CertificateAuthorityType.NONHOSTED, memberUuid);

        if (ca instanceof NonHostedCertificateAuthorityData) {
            NonHostedCertificateAuthorityData result = (NonHostedCertificateAuthorityData) ca;

            provisioningValidator.validateProvisioningCmsAndIdentityCertificate(unvalidatedProvisioningObject, result.getProvisioningIdentityCertificate());

            return result;
        }

        throw new ProvisioningException(ResponseExceptionType.UNKNOWN_SENDER);
    }
}
