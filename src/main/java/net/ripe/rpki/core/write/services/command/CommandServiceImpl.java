package net.ripe.rpki.core.write.services.command;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.core.events.CertificateAuthorityEventVisitor;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.ripencc.support.event.EventDelegateTracker;
import net.ripe.rpki.ripencc.support.event.EventSubscription;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import net.ripe.rpki.services.impl.handlers.MessageDispatcher;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.math.BigInteger;

@Service
@Slf4j
public class CommandServiceImpl implements CommandService {

    @Autowired
    private MessageDispatcher commandDispatcher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    @Named("roaEntityServiceBean")
    private CertificateAuthorityEventVisitor roaEntityService;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public VersionedId getNextId() {
        Query q = entityManager.createNativeQuery("SELECT nextval('seq_all')");
        q.setFlushMode(FlushModeType.COMMIT); // no need to do dirty checking
        BigInteger next = (BigInteger) q.getSingleResult();
        return new VersionedId(next.longValue());
    }

    @Override
    public CommandStatus execute(final CertificateAuthorityCommand command) {
        EventDelegateTracker.get().reset();
        EventSubscription roaEntityServiceSubscription = HostedCertificateAuthority.subscribe(roaEntityService);
        try {
            return executeCommand(command);
        } finally {
            roaEntityServiceSubscription.cancel();
            EventDelegateTracker.get().reset();
        }
    }

    private CommandStatus executeCommand(CertificateAuthorityCommand command) {
        final CommandStatus commandStatus = new CommandStatus();
        transactionTemplate.executeWithoutResult((status) -> {
            MDC.put("command", command.getCommandGroup() + ":" + command.getCommandType() + ":" + command.getCertificateAuthorityId());
            try {
                commandStatus.setTransactionStatus(status);
                commandDispatcher.dispatch(command, commandStatus);
                log.debug("Command completed.");
            } catch (CommandWithoutEffectException e) {
                log.debug("Command without effect: " + command);
                commandStatus.setHasEffect(false);
            } catch (Exception e) {
                log.warn("Error processing command: " + command, e);
                status.setRollbackOnly();
                throw e;
            } finally {
                MDC.remove("command");
            }
        });
        return commandStatus;
    }

    // test-only
    void setCommandDispatcher(MessageDispatcher commandDispatcher) {
        this.commandDispatcher = commandDispatcher;
    }

    // test-only
    void setTransactionTemplate(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }
}
