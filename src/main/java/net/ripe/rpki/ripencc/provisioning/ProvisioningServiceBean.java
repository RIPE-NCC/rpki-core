package net.ripe.rpki.ripencc.provisioning;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObjectParser;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObjectParserException;
import net.ripe.rpki.commons.provisioning.protocol.ResponseExceptionType;
import net.ripe.rpki.domain.ProvisioningAuditLogEntity;
import net.ripe.rpki.server.api.security.RunAsUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static net.ripe.rpki.server.api.security.RunAsUserHolder.*;

@Slf4j
@Service("provisioningService")
public class ProvisioningServiceBean implements ProvisioningService {
    private final ProvisioningRequestProcessor provisioningRequestProcessor;
    private final ProvisioningAuditLogService provisioningAuditLogService;

    private final ProvisioningMetricsService provisioningMetricsService;

    @Autowired
    public ProvisioningServiceBean(ProvisioningRequestProcessor provisioningRequestProcessor, ProvisioningAuditLogService provisioningAuditLogService, ProvisioningMetricsService provisioningMetricsService) {
        this.provisioningRequestProcessor = provisioningRequestProcessor;
        this.provisioningAuditLogService = provisioningAuditLogService;
        this.provisioningMetricsService = provisioningMetricsService;
    }

    @Override
    public byte[] processRequest(final byte[] request) throws ProvisioningException {
        return asAdmin((GetE<byte[], ProvisioningException>) () -> {
            final ProvisioningCmsObject requestObject = extractRequestObject(request);
            provisioningMetricsService.trackPayload(requestObject.getPayload());

            final String memberUUID = requestObject.getPayload().getSender();
            final ProvisioningAuditLogEntity requestLogEntry = new ProvisioningAuditLogEntity(requestObject, "non-hosted CA", memberUUID);
            provisioningAuditLogService.log(requestLogEntry, request);
            try {
                ProvisioningCmsObject responseObject = provisioningRequestProcessor.process(requestObject);
                ProvisioningAuditLogEntity responseLogEntry = new ProvisioningAuditLogEntity(responseObject, RunAsUser.ADMIN.getFriendlyName(), memberUUID);
                provisioningAuditLogService.log(responseLogEntry, request);

                provisioningMetricsService.trackPayload(responseObject.getPayload());

                return responseObject.getEncoded();
            } catch (ProvisioningException ex) {
                log.warn("Not able to process provisioning request, member UUID = {} with the following error: {}", memberUUID, ex.getMessage());
                throw ex;
            }
        });
    }

    /**
     * Get the CMS object - without validating the certificates.
     *
     * Many steps validation steps are deferred until the certificate is available, for which the sender in the XML
     * content needs to be analysed (@see ProvisioningCmsValidationStrategy).
     *
     * @param encodedCmsObject raw CMS object
     * @return parsed but unvalidated object
     * @throws ProvisioningException when object fails semantic validity checks
     */
    ProvisioningCmsObject extractRequestObject(byte[] encodedCmsObject) throws ProvisioningException {
        try {
            ProvisioningCmsObjectParser cmsParser = new ProvisioningCmsObjectParser();
            cmsParser.parseCms("cms", encodedCmsObject);
            provisioningMetricsService.trackValidationResult(cmsParser.getValidationResult());
            return cmsParser.getProvisioningCmsObject();
        } catch (ProvisioningCmsObjectParserException e) {
            log.warn("Could not parse CMS Object: " + e.getMessage());
            throw new ProvisioningException(ResponseExceptionType.BAD_DATA, e);
        }
    }

}
