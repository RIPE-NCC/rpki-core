package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.server.api.commands.CreateAllResourcesCertificateAuthorityCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.apache.commons.lang.Validate;

import javax.inject.Inject;

@Handler
public class CreateAllResourcesCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<CreateAllResourcesCertificateAuthorityCommand> {

    private final RepositoryConfiguration repositoryConfiguration;
    private final CertificationConfiguration certificationConfiguration;

    @Inject
	public CreateAllResourcesCertificateAuthorityCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
																RepositoryConfiguration repositoryConfiguration,
																CertificationConfiguration certificationConfiguration) {
		super(certificateAuthorityRepository);
        this.repositoryConfiguration = repositoryConfiguration;
        this.certificationConfiguration = certificationConfiguration;
	}

	@Override
	public Class<CreateAllResourcesCertificateAuthorityCommand> commandType() {
		return CreateAllResourcesCertificateAuthorityCommand.class;
	}

	@Override
	public void handle(CreateAllResourcesCertificateAuthorityCommand command, CommandStatus commandStatus) {
		Validate.notNull(command);
		final int randomSerialIncrement = certificationConfiguration.getMaxSerialIncrement();
		final HostedCertificateAuthority allResourcesCertificateAuthority = new AllResourcesCertificateAuthority(
		        command.getCertificateAuthorityVersionedId().getId(),
                repositoryConfiguration.getAllResourcesCaPrincipal(),
                randomSerialIncrement);
		getCertificateAuthorityRepository().add(allResourcesCertificateAuthority);
	}
}
