package net.ripe.rpki.services.impl.handlers;

import lombok.NonNull;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.server.api.commands.CreateAllResourcesCertificateAuthorityCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.services.command.CommandStatus;

import javax.inject.Inject;

@Handler
public class CreateAllResourcesCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<CreateAllResourcesCertificateAuthorityCommand> {

    private final RepositoryConfiguration repositoryConfiguration;

    @Inject
    public CreateAllResourcesCertificateAuthorityCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                                RepositoryConfiguration repositoryConfiguration) {
        super(certificateAuthorityRepository);
        this.repositoryConfiguration = repositoryConfiguration;
    }

    @Override
    public Class<CreateAllResourcesCertificateAuthorityCommand> commandType() {
        return CreateAllResourcesCertificateAuthorityCommand.class;
    }

    @Override
    public void handle(@NonNull CreateAllResourcesCertificateAuthorityCommand command, CommandStatus commandStatus) {
        final HostedCertificateAuthority allResourcesCertificateAuthority = new AllResourcesCertificateAuthority(
                command.getCertificateAuthorityVersionedId().getId(),
                repositoryConfiguration.getAllResourcesCaPrincipal()
        );
        getCertificateAuthorityRepository().add(allResourcesCertificateAuthority);
    }
}
