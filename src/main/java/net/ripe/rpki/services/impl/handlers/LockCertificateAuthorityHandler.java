package net.ripe.rpki.services.impl.handlers;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.server.api.commands.CertificateAuthorityActivationCommand;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.ChildParentCertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.util.DBComponent;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Handler(order = 10)
public class LockCertificateAuthorityHandler implements CertificateAuthorityCommandHandler<CertificateAuthorityCommand> {

    private final DBComponent dbComponent;

    @Inject
    public LockCertificateAuthorityHandler(DBComponent dbComponent) {
        this.dbComponent = dbComponent;
    }

    @Override
    public Class<CertificateAuthorityCommand> commandType() {
        return CertificateAuthorityCommand.class;
    }

    @Override
    public void handle(CertificateAuthorityCommand command, CommandStatus commandStatus) {
        List<Long> lockedCaIds = new ArrayList<>(2);

        if (command instanceof CertificateAuthorityActivationCommand) {
            // Must lock the parent instead!
            long parentId = ((CertificateAuthorityActivationCommand) command).getParentId();
            dbComponent.lockCertificateAuthorityForUpdate(parentId);
            lockedCaIds.add(parentId);
        } else if (command instanceof ChildParentCertificateAuthorityCommand) {
            // Must lock child and then the parent
            long childId = command.getCertificateAuthorityVersionedId().getId();
            Long parentId = dbComponent.lockCertificateAuthorityForUpdate(childId);
            lockedCaIds.add(childId);
            if (parentId != null) {
                dbComponent.lockCertificateAuthorityForUpdate(parentId);
                lockedCaIds.add(parentId);
            }
        } else {
            // Other command types only affect a single CA that must be locked
            long id = command.getCertificateAuthorityVersionedId().getId();
            dbComponent.lockCertificateAuthorityForUpdate(id);
            lockedCaIds.add(id);
        }

        // Ensure the CAs version is incremented whenever it is involved in handling a command
        for (Long caId : lockedCaIds) {
            dbComponent.lockCertificateAuthorityForceIncrement(caId);
        }
    }
}
