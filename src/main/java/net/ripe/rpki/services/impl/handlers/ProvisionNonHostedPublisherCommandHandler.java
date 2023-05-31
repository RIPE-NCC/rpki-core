package net.ripe.rpki.services.impl.handlers;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.server.api.commands.ProvisionNonHostedPublisherCommand;
import net.ripe.rpki.server.api.ports.NonHostedPublisherRepositoryService;
import net.ripe.rpki.server.api.services.command.CertificationResourceLimitExceededException;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import javax.inject.Inject;
import java.util.UUID;

import static net.ripe.rpki.domain.NonHostedCertificateAuthority.PUBLISHER_REPOSITORIES_LIMIT;

@Handler
@ConditionalOnBean(NonHostedPublisherRepositoryService.class)
@Slf4j
public class ProvisionNonHostedPublisherCommandHandler extends AbstractCertificateAuthorityCommandHandler<ProvisionNonHostedPublisherCommand> {

    @Inject
    public ProvisionNonHostedPublisherCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository) {
        super(certificateAuthorityRepository);
    }

    @Override
    public Class<ProvisionNonHostedPublisherCommand> commandType() {
        return ProvisionNonHostedPublisherCommand.class;
    }

    @Override
    public void handle(@NonNull ProvisionNonHostedPublisherCommand command, @NonNull CommandStatus commandStatus) {
        NonHostedCertificateAuthority ca = lookupNonHostedCa(command.getCertificateAuthorityId());

        UUID publisherHandle = command.getPublisherHandle();
        if (ca.getPublisherRepositories().containsKey(publisherHandle)) {
            throw new CommandWithoutEffectException(command);
        }

        if (ca.getPublisherRepositories().size() >= PUBLISHER_REPOSITORIES_LIMIT) {
            throw new CertificationResourceLimitExceededException("maximum number of publisher repositories limit exceeded");
        }

        ca.addNonHostedPublisherRepository(publisherHandle, command.getPublisherRequest(), command.getRepositoryResponse());
    }
}
