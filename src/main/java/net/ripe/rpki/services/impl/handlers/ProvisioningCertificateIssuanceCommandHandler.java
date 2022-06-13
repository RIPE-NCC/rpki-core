package net.ripe.rpki.services.impl.handlers;

import lombok.NonNull;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.PublicKeyEntity;
import net.ripe.rpki.server.api.commands.ProvisioningCertificateIssuanceCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;

import javax.inject.Inject;
import java.security.PublicKey;

@Handler
public class ProvisioningCertificateIssuanceCommandHandler extends AbstractCertificateAuthorityCommandHandler<ProvisioningCertificateIssuanceCommand> {

    private final ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga;

    @Inject
    ProvisioningCertificateIssuanceCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                  ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga) {
        super(certificateAuthorityRepository);
        this.childParentCertificateUpdateSaga = childParentCertificateUpdateSaga;
    }

    @Override
    public Class<ProvisioningCertificateIssuanceCommand> commandType() {
        return ProvisioningCertificateIssuanceCommand.class;
    }

    @Override
    public void handle(@NonNull ProvisioningCertificateIssuanceCommand command, @NonNull CommandStatus commandStatus) {
        final NonHostedCertificateAuthority nonHostedCa = lookupNonHostedCA(command.getCertificateAuthorityVersionedId().getId());

        PublicKey publicKey = command.getPublicKey();

        PublicKeyEntity publicKeyEntity = nonHostedCa.findOrCreatePublicKeyEntityByPublicKey(publicKey);
        publicKeyEntity.setLatestIssuanceRequest(command.getRequestedResourceSets(), command.getSia());

        boolean hasEffect = childParentCertificateUpdateSaga.execute(nonHostedCa.getParent(), nonHostedCa, NonHostedCertificateAuthority.INCOMING_RESOURCE_CERTIFICATES_PER_PUBLIC_KEY_LIMIT);
        if (!hasEffect) {
            throw new CommandWithoutEffectException(command);
        }
    }

}
