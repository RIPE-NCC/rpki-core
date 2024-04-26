package net.ripe.rpki.services.impl.handlers;

import lombok.NonNull;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.PublicKeyEntity;
import net.ripe.rpki.server.api.commands.ProvisioningCertificateRevocationCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;

import jakarta.inject.Inject;

@Handler
public class ProvisioningCertificateRevocationCommandHandler extends AbstractCertificateAuthorityCommandHandler<ProvisioningCertificateRevocationCommand> {

    private final ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga;

    @Inject
    ProvisioningCertificateRevocationCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                    ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga) {
        super(certificateAuthorityRepository);
        this.childParentCertificateUpdateSaga = childParentCertificateUpdateSaga;
    }

    @Override
    public Class<ProvisioningCertificateRevocationCommand> commandType() {
        return ProvisioningCertificateRevocationCommand.class;
    }

    @Override
    public void handle(@NonNull ProvisioningCertificateRevocationCommand command, @NonNull CommandStatus commandStatus) {
        final NonHostedCertificateAuthority nonHostedCa = lookupNonHostedCa(command.getCertificateAuthorityId());

        PublicKeyEntity publicKeyEntity = nonHostedCa.findPublicKeyEntityByPublicKey(command.getPublicKey())
            .orElseThrow(() -> new CommandWithoutEffectException(command));

        publicKeyEntity.setLatestRevocationRequest();

        boolean hasEffect = childParentCertificateUpdateSaga.execute(nonHostedCa, NonHostedCertificateAuthority.INCOMING_RESOURCE_CERTIFICATES_PER_PUBLIC_KEY_LIMIT);
        if (!hasEffect) {
            throw new CommandWithoutEffectException(command);
        }
    }

}
