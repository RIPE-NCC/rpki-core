package net.ripe.rpki.services.impl.handlers;

import jakarta.inject.Inject;
import lombok.NonNull;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.server.api.commands.BgpSecCertificateIssuanceCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;

@Handler
public class BgpSecCertificateIssuanceCommandHandler extends AbstractCertificateAuthorityCommandHandler<BgpSecCertificateIssuanceCommand> {

    public static final int ISSUED_CERTIFICATES_PER_SIGNED_KEY_LIMIT = 100;

    private final ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga;

    @Inject
    BgpSecCertificateIssuanceCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                            ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga) {
        super(certificateAuthorityRepository);
        this.childParentCertificateUpdateSaga = childParentCertificateUpdateSaga;
    }

    @Override
    public Class<BgpSecCertificateIssuanceCommand> commandType() {
        return BgpSecCertificateIssuanceCommand.class;
    }

    @Override
    public void handle(@NonNull BgpSecCertificateIssuanceCommand command, @NonNull CommandStatus commandStatus) {
        final CertificateAuthority ca = lookupCa(command.getCertificateAuthorityId());
        boolean hasEffect = childParentCertificateUpdateSaga.execute(ca, ISSUED_CERTIFICATES_PER_SIGNED_KEY_LIMIT);
        if (!hasEffect) {
            throw new CommandWithoutEffectException(command);
        }
    }

}
