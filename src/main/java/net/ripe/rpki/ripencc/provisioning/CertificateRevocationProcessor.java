package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.provisioning.payload.error.NotPerformedError;
import net.ripe.rpki.commons.provisioning.payload.revocation.CertificateRevocationKeyElement;
import net.ripe.rpki.commons.provisioning.payload.revocation.request.CertificateRevocationRequestPayload;
import net.ripe.rpki.commons.provisioning.payload.revocation.response.CertificateRevocationResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.revocation.response.CertificateRevocationResponsePayloadBuilder;
import net.ripe.rpki.server.api.commands.ProvisioningCertificateRevocationCommand;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.NonHostedPublicKeyData;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.command.CommandService;
import org.springframework.stereotype.Component;

import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;


@Component
class CertificateRevocationProcessor extends AbstractProvisioningProcessor {

    private final CommandService commandService;

    public CertificateRevocationProcessor(ResourceLookupService resourceLookupService,
                                          CommandService commandService) {
        super(resourceLookupService);
        this.commandService = commandService;
    }

    public CertificateRevocationResponsePayload process(NonHostedCertificateAuthorityData nonHostedCertificateAuthority,
                                                        CertificateRevocationRequestPayload requestPayload) {
        final CertificateRevocationKeyElement keyElement = requestPayload.getKeyElement();
        if (!DEFAULT_RESOURCE_CLASS.equals(keyElement.getClassName())) {
            throw new NotPerformedException(NotPerformedError.REQ_NO_SUCH_RESOURCE_CLASS);
        }

        NonHostedPublicKeyData publicKeyEntity = getPublicKeyByHash(nonHostedCertificateAuthority, keyElement);
        if (publicKeyEntity == null) {
            throw new NotPerformedException(NotPerformedError.REV_NO_SUCH_KEY);
        }

        commandService.execute(new ProvisioningCertificateRevocationCommand(nonHostedCertificateAuthority.getVersionedId(), publicKeyEntity.getPublicKey()));

        CertificateRevocationResponsePayloadBuilder responsePayloadBuilder = new CertificateRevocationResponsePayloadBuilder();
        responsePayloadBuilder.withClassName(keyElement.getClassName());
        responsePayloadBuilder.withPublicKeyHash(keyElement.getPublicKeyHash());
        return responsePayloadBuilder.build();
    }

    private NonHostedPublicKeyData getPublicKeyByHash(NonHostedCertificateAuthorityData nonHostedCertificateAuthority, CertificateRevocationKeyElement keyElement) {
        return nonHostedCertificateAuthority
                .getPublicKeys().stream()
                .filter(key -> KeyPairUtil.getEncodedKeyIdentifier(key.getPublicKey()).equals(keyElement.getPublicKeyHash()))
                .findFirst()
                .orElse(null);
    }
}
