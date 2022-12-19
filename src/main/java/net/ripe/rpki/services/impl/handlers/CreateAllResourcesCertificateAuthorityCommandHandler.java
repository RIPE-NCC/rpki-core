package net.ripe.rpki.services.impl.handlers;

import lombok.NonNull;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.server.api.commands.CreateAllResourcesCertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;

import javax.inject.Inject;

@Handler
public class CreateAllResourcesCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<CreateAllResourcesCertificateAuthorityCommand> {

    @Inject
    public CreateAllResourcesCertificateAuthorityCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository) {
        super(certificateAuthorityRepository);
    }

    @Override
    public Class<CreateAllResourcesCertificateAuthorityCommand> commandType() {
        return CreateAllResourcesCertificateAuthorityCommand.class;
    }

    @Override
    public void handle(@NonNull CreateAllResourcesCertificateAuthorityCommand command, CommandStatus commandStatus) {
        final ManagedCertificateAuthority allResourcesCertificateAuthority = new AllResourcesCertificateAuthority(
                command.getCertificateAuthorityId(),
                command.getName(),
                command.getUuid()
        );
        getCertificateAuthorityRepository().add(allResourcesCertificateAuthority);
    }
}
