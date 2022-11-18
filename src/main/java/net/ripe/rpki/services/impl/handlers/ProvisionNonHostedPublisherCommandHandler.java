package net.ripe.rpki.services.impl.handlers;

import lombok.NonNull;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.server.api.commands.ProvisionNonHostedPublisherCommand;
import net.ripe.rpki.server.api.ports.NonHostedPublisherRepositoryService;
import net.ripe.rpki.server.api.services.command.CertificationResourceLimitExceededException;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import javax.inject.Inject;
import javax.persistence.EntityNotFoundException;
import java.util.UUID;

import static net.ripe.rpki.domain.NonHostedCertificateAuthority.PUBLISHER_REPOSITORIES_LIMIT;

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
    public void handle(@NonNull ProvisionNonHostedPublisherCommand command, @NonNull CommandStatus commandStatus) {
        NonHostedCertificateAuthority ca = getCertificateAuthorityRepository().findNonHostedCa(command.getCertificateAuthorityId());
        if (ca == null) {
            throw new EntityNotFoundException("non-hosted CA " + command.getCertificateAuthorityId() + " not found");
        }

        if (ca.getPublisherRepositories().size() >= PUBLISHER_REPOSITORIES_LIMIT) {
            throw new CertificationResourceLimitExceededException("maximum number of publisher repositories limit exceeded");
        }

        UUID publisherHandle = command.getPublisherHandle();
        PublisherRequest publisherRequest = command.getPublisherRequest();
        RepositoryResponse repositoryResponse = nonHostedPublisherRepositoryService.provisionPublisher(publisherHandle, publisherRequest);

        ca.addNonHostedPublisherRepository(publisherHandle, publisherRequest, repositoryResponse);
    }
}
