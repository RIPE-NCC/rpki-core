package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.provisioning.payload.error.NotPerformedError;
import net.ripe.rpki.commons.provisioning.payload.revocation.CertificateRevocationKeyElement;
import net.ripe.rpki.commons.provisioning.payload.revocation.request.CertificateRevocationRequestPayload;
import net.ripe.rpki.commons.provisioning.payload.revocation.response.CertificateRevocationResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.revocation.response.CertificateRevocationResponsePayloadBuilder;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.PublicKeyEntity;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.command.CommandService;

import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;


public class CertificateRevocationProcessor extends AbstractProvisioningProcessor {

    private final CommandService commandService;

    public CertificateRevocationProcessor(ResourceLookupService resourceLookupService,
                                          CommandService commandService) {
        super(resourceLookupService);
        this.commandService = commandService;
    }

    public CertificateRevocationResponsePayload process(NonHostedCertificateAuthority nonHostedCertificateAuthority,
                                                        CertificateRevocationRequestPayload requestPayload) {
        final CertificateRevocationKeyElement keyElement = requestPayload.getKeyElement();
        if (!DEFAULT_RESOURCE_CLASS.equals(keyElement.getClassName())) {
            throw new NotPerformedException(NotPerformedError.REQ_NO_SUCH_RESOURCE_CLASS);
        }

        PublicKeyEntity publicKeyEntity = getPublicKeyByHash(nonHostedCertificateAuthority, keyElement);
        if (publicKeyEntity == null) {
            throw new NotPerformedException(NotPerformedError.REV_NO_SUCH_KEY);
        }

        publicKeyEntity.setLatestRevocationRequest(keyElement);

        commandService.execute(new UpdateAllIncomingResourceCertificatesCommand(nonHostedCertificateAuthority.getVersionedId()));

        CertificateRevocationResponsePayloadBuilder responsePayloadBuilder = new CertificateRevocationResponsePayloadBuilder();
        responsePayloadBuilder.withClassName(keyElement.getClassName());
        responsePayloadBuilder.withPublicKeyHash(keyElement.getPublicKeyHash());
        return responsePayloadBuilder.build();
    }

    private PublicKeyEntity getPublicKeyByHash(NonHostedCertificateAuthority nonHostedCertificateAuthority, CertificateRevocationKeyElement keyElement) {
        return nonHostedCertificateAuthority
                .getPublicKeys().stream()
                .filter(key -> KeyPairUtil.getEncodedKeyIdentifier(key.getPublicKey()).equals(keyElement.getPublicKeyHash()))
                .findFirst()
                .orElse(null);
    }
}
