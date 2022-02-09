package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.commands.CreateRootCertificateAuthorityCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.apache.commons.lang.Validate;

import javax.inject.Inject;

@Handler
public class CreateRootCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<CreateRootCertificateAuthorityCommand> {

    private final RepositoryConfiguration repositoryConfiguration;
    private final CertificationConfiguration certificationConfiguration;

    @Inject
    public CreateRootCertificateAuthorityCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                        RepositoryConfiguration repositoryConfiguration,
                                                        CertificationConfiguration certificationConfiguration) {
        super(certificateAuthorityRepository);
        this.repositoryConfiguration = repositoryConfiguration;
        this.certificationConfiguration = certificationConfiguration;
    }

    @Override
    public Class<CreateRootCertificateAuthorityCommand> commandType() {
        return CreateRootCertificateAuthorityCommand.class;
    }

    @Override
    public void handle(CreateRootCertificateAuthorityCommand command, CommandStatus commandStatus) {
        Validate.notNull(command);

        AllResourcesCertificateAuthority allResourcesCertificateAuthority = lookupAllResourcesCertificateAuthority();
        Validate.notNull(allResourcesCertificateAuthority, "all resources CA not found");

        int randomSerialIncrement = certificationConfiguration.getMaxSerialIncrement();
        HostedCertificateAuthority ca = new ProductionCertificateAuthority(
            command.getCertificateAuthorityVersionedId().getId(),
            repositoryConfiguration.getProductionCaPrincipal(),
            allResourcesCertificateAuthority,
            randomSerialIncrement
        );
        getCertificateAuthorityRepository().add(ca);
    }

    protected AllResourcesCertificateAuthority lookupAllResourcesCertificateAuthority() {
        return getCertificateAuthorityRepository().findByTypeAndName(AllResourcesCertificateAuthority.class, repositoryConfiguration.getAllResourcesCaPrincipal());
    }
}
