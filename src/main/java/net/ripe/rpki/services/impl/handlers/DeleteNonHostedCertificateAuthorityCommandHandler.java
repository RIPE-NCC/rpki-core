package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.domain.*;
import net.ripe.rpki.server.api.commands.DeleteNonHostedCertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.services.impl.DeleteCertificateAuthorityService;

import javax.inject.Inject;

@Handler(order = 210)
public class DeleteNonHostedCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<DeleteNonHostedCertificateAuthorityCommand> {

    private final DeleteCertificateAuthorityService deleteCertificateAuthorityService;

    @Inject
    public DeleteNonHostedCertificateAuthorityCommandHandler(
        CertificateAuthorityRepository certificateAuthorityRepository,
        DeleteCertificateAuthorityService deleteCertificateAuthorityService
    ) {
        super(certificateAuthorityRepository);
        this.deleteCertificateAuthorityService = deleteCertificateAuthorityService;
    }

    @Override
    public Class<DeleteNonHostedCertificateAuthorityCommand> commandType() {
        return DeleteNonHostedCertificateAuthorityCommand.class;
    }

    @Override
    public void handle(DeleteNonHostedCertificateAuthorityCommand command, CommandStatus commandStatus) {
        deleteCertificateAuthorityService.deleteNonHosted(command.getCertificateAuthorityVersionedId().getId());
    }

}
