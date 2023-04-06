package net.ripe.rpki.services.impl.handlers;

import lombok.NonNull;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.server.api.commands.ActivateHostedCertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;

import javax.inject.Inject;

@Handler
public class ActivateHostedCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<ActivateHostedCertificateAuthorityCommand> {

    private final ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga;

    @Inject
    ActivateHostedCertificateAuthorityCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                     ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga) {
        super(certificateAuthorityRepository);
        this.childParentCertificateUpdateSaga = childParentCertificateUpdateSaga;
    }

    @Override
    public Class<ActivateHostedCertificateAuthorityCommand> commandType() {
        return ActivateHostedCertificateAuthorityCommand.class;
    }

    @Override
    public void handle(@NonNull ActivateHostedCertificateAuthorityCommand command, CommandStatus commandStatus) {
        ManagedCertificateAuthority productionCa = lookupManagedCa(command.getParentId());
        HostedCertificateAuthority memberCa = createMemberCA(command, productionCa);
        childParentCertificateUpdateSaga.execute(memberCa, Integer.MAX_VALUE);
    }

    private HostedCertificateAuthority createMemberCA(ActivateHostedCertificateAuthorityCommand command, ManagedCertificateAuthority parentCa) {
        HostedCertificateAuthority ca = new HostedCertificateAuthority(command.getCertificateAuthorityId(),
                command.getName(), command.getUuid(), parentCa);
        getCertificateAuthorityRepository().add(ca);
        return ca;
    }

}
