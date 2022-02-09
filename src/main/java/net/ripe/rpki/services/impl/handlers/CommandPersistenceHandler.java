package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;

import javax.inject.Inject;

@Handler(order=200)
public class CommandPersistenceHandler extends AbstractCertificateAuthorityCommandHandler<CertificateAuthorityCommand> {

    private final CommandAuditService commandAuditService;

    @Inject
    public CommandPersistenceHandler(CertificateAuthorityRepository certificateAuthorityRepository, CommandAuditService commandAuditService) {
        super(certificateAuthorityRepository);
        this.commandAuditService = commandAuditService;
    }

    @Override
    public Class<CertificateAuthorityCommand> commandType() {
        return CertificateAuthorityCommand.class;
    }

    @Override
    public void handle(CertificateAuthorityCommand command, final CommandStatus commandStatus) {
        if (commandStatus.isHasEffect()) {
            CertificateAuthority ca = lookupCA(command.getCertificateAuthorityVersionedId().getId());
            commandAuditService.record(command, ca.getVersionedId());
        }
    }
}
