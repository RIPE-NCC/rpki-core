package net.ripe.rpki.services.impl.handlers;

import lombok.NonNull;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.commands.CreateRootCertificateAuthorityCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.apache.commons.lang.Validate;

import jakarta.inject.Inject;

@Handler
public class CreateRootCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<CreateRootCertificateAuthorityCommand> {

    private final RepositoryConfiguration repositoryConfiguration;

    @Inject
    public CreateRootCertificateAuthorityCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                        RepositoryConfiguration repositoryConfiguration) {
        super(certificateAuthorityRepository);
        this.repositoryConfiguration = repositoryConfiguration;
    }

    @Override
    public Class<CreateRootCertificateAuthorityCommand> commandType() {
        return CreateRootCertificateAuthorityCommand.class;
    }

    @Override
    public void handle(@NonNull CreateRootCertificateAuthorityCommand command, CommandStatus commandStatus) {
        AllResourcesCertificateAuthority allResourcesCertificateAuthority = lookupAllResourcesCertificateAuthority();
        Validate.notNull(allResourcesCertificateAuthority, "all resources CA not found");

        ManagedCertificateAuthority ca = new ProductionCertificateAuthority(
            command.getCertificateAuthorityId(),
            command.getName(),
            command.getUuid(),
            allResourcesCertificateAuthority
        );
        getCertificateAuthorityRepository().add(ca);
    }

    protected AllResourcesCertificateAuthority lookupAllResourcesCertificateAuthority() {
        return getCertificateAuthorityRepository().findByTypeAndName(AllResourcesCertificateAuthority.class, repositoryConfiguration.getAllResourcesCaPrincipal());
    }
}
