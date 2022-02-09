package net.ripe.rpki.ripencc.provisioning;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
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
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.CertificationResourceLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.LockModeType;
import javax.persistence.LockTimeoutException;
import java.util.UUID;


@Component
@Transactional
@Slf4j
public class ProvisioningRequestProcessorBean implements ProvisioningRequestProcessor {

    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final KeyPairFactory keyPairFactory;
    private final CertificateIssuanceProcessor certificateIssuanceProcessor;
    private final ListResourceClassProcessor listResourceClassProcessor;
    private final CertificateRevocationProcessor certificateRevocationProcessor;

    private final ProvisioningCmsValidationStrategy provisioningValidator;

    @Autowired
    public ProvisioningRequestProcessorBean(
            final CertificateAuthorityRepository certificateAuthorityRepository,
            final KeyPairFactory keyPairFactory,
            final ResourceLookupService resourceLookupService,
            CommandService commandService,
            ProvisioningCmsValidationStrategy provisioningValidator
    ) {
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.keyPairFactory = keyPairFactory;
        this.certificateIssuanceProcessor = new CertificateIssuanceProcessor(resourceLookupService, commandService);
        this.listResourceClassProcessor = new ListResourceClassProcessor(resourceLookupService);
        this.certificateRevocationProcessor = new CertificateRevocationProcessor(resourceLookupService, commandService);
        this.provisioningValidator = provisioningValidator;
    }

    @Override
    public ProvisioningCmsObject process(ProvisioningCmsObject request) {
        UUID memberCAId = parseSenderAndRecipientUUID(request.getPayload().getSender());
        UUID productionCAId = parseSenderAndRecipientUUID(request.getPayload().getRecipient());

        NonHostedCertificateAuthority nonHostedMemberCa = getNonHostedCertificateAuthorityWithProvisioningCertificateSigning(memberCAId, request);

        ProductionCertificateAuthority productionCA = getProductionCertificateAuthorityWhichIsParentOf(productionCAId, nonHostedMemberCa);

        AbstractProvisioningResponsePayload responsePayload = processRequestPayload(nonHostedMemberCa, productionCA, request.getPayload());

        responsePayload.setRecipient(request.getPayload().getSender());
        responsePayload.setSender(request.getPayload().getRecipient());

        return productionCA.getMyDownStreamProvisioningCommunicator().createProvisioningCmsResponseObject(keyPairFactory, responsePayload);
    }

    @VisibleForTesting
    protected AbstractProvisioningResponsePayload processRequestPayload(NonHostedCertificateAuthority nonHostedMemberCa,
                                                                      ProductionCertificateAuthority productionCA,
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
            return buildError(e.getNotPerformedError());
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

    private static UUID parseSenderAndRecipientUUID(String uuid) {
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
    private ProductionCertificateAuthority getProductionCertificateAuthorityWhichIsParentOf(UUID parentId, NonHostedCertificateAuthority nonHostedMemberCa) {
        ProductionCertificateAuthority result = certificateAuthorityRepository.findByTypeAndUuid(
            ProductionCertificateAuthority.class, parentId, LockModeType.NONE);
        if (result == null) {
            throw new ProvisioningException(ResponseExceptionType.UNKNOWN_RECIPIENT);
        }

        if (nonHostedMemberCa.getParent() != result) {
            throw new ProvisioningException(ResponseExceptionType.BAD_SENDER_AND_RECIPIENT);
        }

        return result;
    }

    /**
     * Get the non-hosted CA for the member UUID.
     *
     * @param unvalidatedProvisioningObject the parsed, but unvalidated CMS object containingr the up-down request
     * @ensures unvalidatedProvisioningObject is validated (EE cert is current, etc) and \result's provisioning identity certificate signed over certificateSigningCMS
     */
    private NonHostedCertificateAuthority getNonHostedCertificateAuthorityWithProvisioningCertificateSigning(UUID memberUuid, ProvisioningCmsObject unvalidatedProvisioningObject) throws ProvisioningException {
        try {
            // TODO (2021-12-29, TdK): Because we use a javax.persistence.lock.timeout of -1 the pessimistic write lock
            //  (under water: a `SELECT ... FOR UPDATE`) will serialise operations instead of rejecting them.
            //  Thus catching the exception is a noop. We should lower this timeout or use pg_try_advisory_xact_lock.

            // https://datatracker.ietf.org/doc/html/rfc6492#section-3
            // Lock the CA because multiple concurrent requests with a common sender
            // MUST be detected and rejected with an error response (i.e., an error code 1101 response).
            NonHostedCertificateAuthority result = certificateAuthorityRepository.findByTypeAndUuid(NonHostedCertificateAuthority.class, memberUuid, LockModeType.PESSIMISTIC_WRITE);

            if (result == null) {
                throw new ProvisioningException(ResponseExceptionType.UNKNOWN_SENDER);
            }

            provisioningValidator.validateProvisioningCmsAndIdentityCertificate(unvalidatedProvisioningObject, result.getProvisioningIdentityCertificate());

            return result;
        } catch (LockTimeoutException e) {
            log.info("Concurrent operation for non-hosted CA for memberUuid: {}", memberUuid);
            // Only possible iff javax.persistence.lock.timeout != -1
            throw new NotPerformedException(NotPerformedError.ALREADY_PROCESSING_REQUEST);
        }
    }
}
