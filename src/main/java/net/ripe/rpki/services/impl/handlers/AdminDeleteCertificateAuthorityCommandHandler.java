package net.ripe.rpki.services.impl.handlers;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.application.impl.CaDeletionServiceBean;
import net.ripe.rpki.server.api.commands.AdminDeleteCertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;

@Slf4j
@Handler
public class AdminDeleteCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<AdminDeleteCertificateAuthorityCommand> {

    private final CaDeletionServiceBean caDeletionServiceBean;

    @Inject
    public AdminDeleteCertificateAuthorityCommandHandler(CaDeletionServiceBean caDeletionServiceBean) {
        super(caDeletionServiceBean.getCertificateAuthorityRepository());
        this.caDeletionServiceBean = caDeletionServiceBean;
    }

    @Override
    public Class<AdminDeleteCertificateAuthorityCommand> commandType() {
        return AdminDeleteCertificateAuthorityCommand.class;
    }

    @Override
    public void handle(AdminDeleteCertificateAuthorityCommand command, CommandStatus commandStatus) {
        caDeletionServiceBean.deleteCa(command.getCertificateAuthorityId());
    }
}
