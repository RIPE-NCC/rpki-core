package net.ripe.rpki.services.impl.handlers;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.server.api.commands.CertificateAuthorityActivationCommand;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.ChildParentCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.ChildSharedParentCertificateAuthorityCommand;
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
        List<Long> lockedForUpdateCaIds = new ArrayList<>(2);

        if (command instanceof CertificateAuthorityActivationCommand) {
            // Must lock the parent instead!
            long parentId = ((CertificateAuthorityActivationCommand) command).getParentId();
            dbComponent.lockCertificateAuthorityForUpdate(parentId);
            lockedForUpdateCaIds.add(parentId);
        } else if (command instanceof ChildSharedParentCertificateAuthorityCommand) {
            // Must lock child exclusively and parent for sharing
            long childId = command.getCertificateAuthorityId();
            Long parentId = dbComponent.lockCertificateAuthorityForUpdate(childId);
            lockedForUpdateCaIds.add(childId);
            if (parentId != null) {
                dbComponent.lockCertificateAuthorityForSharing(parentId);
            }
        } else if (command instanceof ChildParentCertificateAuthorityCommand) {
            // Must lock child and then the parent
            long childId = command.getCertificateAuthorityId();
            Long parentId = dbComponent.lockCertificateAuthorityForUpdate(childId);
            lockedForUpdateCaIds.add(childId);
            if (parentId != null) {
                dbComponent.lockCertificateAuthorityForUpdate(parentId);
                lockedForUpdateCaIds.add(parentId);
            }
        } else {
            // Other command types only affect a single CA that must be locked
            long id = command.getCertificateAuthorityId();
            dbComponent.lockCertificateAuthorityForUpdate(id);
            lockedForUpdateCaIds.add(id);
        }

        // Ensure the CAs version is incremented whenever it is updated when handling a command
        for (Long caId : lockedForUpdateCaIds) {
            dbComponent.lockCertificateAuthorityForceIncrement(caId);
        }
    }
}
