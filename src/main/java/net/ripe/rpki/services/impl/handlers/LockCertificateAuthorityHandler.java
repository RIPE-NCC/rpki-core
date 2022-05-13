package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.server.api.commands.CertificateAuthorityActivationCommand;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.util.DBComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;


@Handler(order = 10)
public class LockCertificateAuthorityHandler implements CertificateAuthorityCommandHandler<CertificateAuthorityCommand> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LockCertificateAuthorityHandler.class);

    private final EntityManager entityManager;

    @Inject
    public LockCertificateAuthorityHandler(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Class<CertificateAuthorityCommand> commandType() {
        return CertificateAuthorityCommand.class;
    }

    @Override
    public void handle(CertificateAuthorityCommand command, CommandStatus commandStatus) {
        final Long id = getCaId(command);
        entityManager.find(CertificateAuthority.class, id, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Locked certificate authority with (id = " + id + ")");
        }
    }

    private Long getCaId(CertificateAuthorityCommand command) {
        if (command instanceof CertificateAuthorityActivationCommand) {
            // Must lock the parent instead!
            return ((CertificateAuthorityActivationCommand) command).getParentId();
        } else {
            return command.getCertificateAuthorityVersionedId().getId();
        }
    }
}
