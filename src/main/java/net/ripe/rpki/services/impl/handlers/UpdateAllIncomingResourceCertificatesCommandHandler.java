package net.ripe.rpki.services.impl.handlers;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;

import jakarta.inject.Inject;

@Handler
@Slf4j
public class UpdateAllIncomingResourceCertificatesCommandHandler extends AbstractCertificateAuthorityCommandHandler<UpdateAllIncomingResourceCertificatesCommand> {

    private final ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga;

    @Inject
    UpdateAllIncomingResourceCertificatesCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                        ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga) {
        super(certificateAuthorityRepository);
        this.childParentCertificateUpdateSaga = childParentCertificateUpdateSaga;
    }

    @Override
    public Class<UpdateAllIncomingResourceCertificatesCommand> commandType() {
        return UpdateAllIncomingResourceCertificatesCommand.class;
    }

    @Override
    public void handle(@NonNull UpdateAllIncomingResourceCertificatesCommand command, @NonNull CommandStatus commandStatus) {
        final boolean hasEffect;
        final CertificateAuthority certificateAuthority = lookupCa(command.getCertificateAuthorityId());
        if (certificateAuthority.getParent() == null) {
            log.error("cannot update incoming resource certificate for CAs without parent {}", certificateAuthority);
            hasEffect = false;
        } else {
            hasEffect = childParentCertificateUpdateSaga.execute(certificateAuthority, command.getIssuedCertificatesPerSignedKeyLimit());
        }

        if (!hasEffect) {
            throw new CommandWithoutEffectException(command);
        }
    }
}
