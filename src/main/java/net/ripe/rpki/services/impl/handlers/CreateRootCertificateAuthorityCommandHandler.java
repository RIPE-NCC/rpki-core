package net.ripe.rpki.services.impl.handlers;

import lombok.NonNull;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairService;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.commands.CreateRootCertificateAuthorityCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.apache.commons.lang.Validate;

import javax.inject.Inject;

@Handler
public class CreateRootCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<CreateRootCertificateAuthorityCommand> {

    private final RepositoryConfiguration repositoryConfiguration;
    private final KeyPairService keyPairService;

    @Inject
    public CreateRootCertificateAuthorityCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                        RepositoryConfiguration repositoryConfiguration,
                                                        KeyPairService keyPairService) {
        super(certificateAuthorityRepository);
        this.repositoryConfiguration = repositoryConfiguration;
        this.keyPairService = keyPairService;
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
            command.getCertificateAuthorityVersionedId().getId(),
            repositoryConfiguration.getProductionCaPrincipal(),
            allResourcesCertificateAuthority
        );
        getCertificateAuthorityRepository().add(ca);
        ca.createNewKeyPair(keyPairService);
    }

    protected AllResourcesCertificateAuthority lookupAllResourcesCertificateAuthority() {
        return getCertificateAuthorityRepository().findByTypeAndName(AllResourcesCertificateAuthority.class, repositoryConfiguration.getAllResourcesCaPrincipal());
    }
}
