package net.ripe.rpki.services.impl.handlers;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.server.api.commands.CertificateAuthorityActivationCommand;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;


@Slf4j
@Handler(order = 10)
public class LockCertificateAuthorityHandler implements CertificateAuthorityCommandHandler<CertificateAuthorityCommand> {

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
        log.debug("Attempting to lock CA (id = {})", id);
        CertificateAuthority certificateAuthority = entityManager.find(CertificateAuthority.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (certificateAuthority != null) {
            entityManager.lock(certificateAuthority, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        }
        log.debug("Locked certificate authority with (id = {})", id);
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
