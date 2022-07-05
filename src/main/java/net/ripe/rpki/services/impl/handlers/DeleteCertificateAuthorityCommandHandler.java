package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.server.api.commands.DeleteCertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.services.impl.DeleteCertificateAuthorityService;

import javax.inject.Inject;

@Handler(order = 210)
public class DeleteCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<DeleteCertificateAuthorityCommand> {

    private final DeleteCertificateAuthorityService deleteCertificateAuthorityService;

    @Inject
    public DeleteCertificateAuthorityCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                    DeleteCertificateAuthorityService deleteCertificateAuthorityService) {
        super(certificateAuthorityRepository);
        this.deleteCertificateAuthorityService = deleteCertificateAuthorityService;
    }

    @Override
    public Class<DeleteCertificateAuthorityCommand> commandType() {
        return DeleteCertificateAuthorityCommand.class;
    }

    @Override
    public void handle(DeleteCertificateAuthorityCommand command, CommandStatus commandStatus) {
        deleteCertificateAuthorityService.revokeCa(command.getCertificateAuthorityVersionedId().getId());
    }

}
