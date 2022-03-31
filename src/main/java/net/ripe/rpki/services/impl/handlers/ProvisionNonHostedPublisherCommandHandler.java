package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.server.api.commands.ProvisionNonHostedPublisherCommand;
import net.ripe.rpki.server.api.ports.NonHostedPublisherRepositoryService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.apache.commons.lang.Validate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import javax.inject.Inject;
import javax.persistence.EntityNotFoundException;
import java.util.UUID;

@Handler
@ConditionalOnBean(NonHostedPublisherRepositoryService.class)
public class ProvisionNonHostedPublisherCommandHandler extends AbstractCertificateAuthorityCommandHandler<ProvisionNonHostedPublisherCommand> {

    private final NonHostedPublisherRepositoryService nonHostedPublisherRepositoryService;

    @Inject
    public ProvisionNonHostedPublisherCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository, NonHostedPublisherRepositoryService nonHostedPublisherRepositoryService) {
        super(certificateAuthorityRepository);
        this.nonHostedPublisherRepositoryService = nonHostedPublisherRepositoryService;
    }

    @Override
    public Class<ProvisionNonHostedPublisherCommand> commandType() {
        return ProvisionNonHostedPublisherCommand.class;
    }

    @Override
    public void handle(ProvisionNonHostedPublisherCommand command, CommandStatus commandStatus) {
        Validate.notNull(command);

        NonHostedCertificateAuthority ca = getCertificateAuthorityRepository().findNonHostedCa(command.getCertificateAuthorityId());
        if (ca == null) {
            throw new EntityNotFoundException("non-hosted CA " + command.getCertificateAuthorityId() + " not found");
        }

        UUID publisherHandle = command.getPublisherHandle();
        PublisherRequest publisherRequest = command.getPublisherRequest();
        RepositoryResponse repositoryResponse = nonHostedPublisherRepositoryService.provisionPublisher(publisherHandle, publisherRequest);

        ca.addNonHostedPublisherRepository(publisherHandle, publisherRequest, repositoryResponse);
    }
}
