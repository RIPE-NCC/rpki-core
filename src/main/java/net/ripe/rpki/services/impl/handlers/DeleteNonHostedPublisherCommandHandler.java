package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.server.api.commands.DeleteNonHostedPublisherCommand;
import net.ripe.rpki.server.api.ports.NonHostedPublisherRepositoryService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.apache.commons.lang.Validate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import javax.inject.Inject;
import javax.persistence.EntityNotFoundException;
import java.util.UUID;

@Handler
@ConditionalOnBean(NonHostedPublisherRepositoryService.class)
public class DeleteNonHostedPublisherCommandHandler extends AbstractCertificateAuthorityCommandHandler<DeleteNonHostedPublisherCommand> {

    @Inject
    public DeleteNonHostedPublisherCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository) {
        super(certificateAuthorityRepository);
    }

    @Override
    public Class<DeleteNonHostedPublisherCommand> commandType() {
        return DeleteNonHostedPublisherCommand.class;
    }

    @Override
    public void handle(DeleteNonHostedPublisherCommand command, CommandStatus commandStatus) {
        Validate.notNull(command);

        NonHostedCertificateAuthority ca = getCertificateAuthorityRepository().findNonHostedCa(command.getCertificateAuthorityId());
        if (ca == null) {
            throw new EntityNotFoundException("non-hosted CA " + command.getCertificateAuthorityId() + " not found");
        }

        UUID publisherHandle = command.getPublisherHandle();
        if (!ca.removeNonHostedPublisherRepository(publisherHandle)) {
            throw new EntityNotFoundException("publisher repository " + publisherHandle + " not found for non-hosted CA " + command.getCertificateAuthorityId());
        }
    }
}
