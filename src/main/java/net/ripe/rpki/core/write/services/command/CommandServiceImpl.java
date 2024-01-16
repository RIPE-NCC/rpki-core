package net.ripe.rpki.core.write.services.command;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.core.events.CertificateAuthorityEventVisitor;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.rest.exception.PreconditionRequiredException;
import net.ripe.rpki.ripencc.support.event.EventDelegateTracker;
import net.ripe.rpki.ripencc.support.event.EventSubscription;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.CommandContext;
import net.ripe.rpki.server.api.services.command.*;
import net.ripe.rpki.services.impl.handlers.CommandHandlerMetrics;
import net.ripe.rpki.services.impl.handlers.LockCertificateAuthorityHandler;
import net.ripe.rpki.services.impl.handlers.MessageDispatcher;
import org.slf4j.MDC;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.persistence.OptimisticLockException;
import javax.persistence.PessimisticLockException;
import javax.persistence.Query;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;

@Setter
@Service
@Slf4j
public class CommandServiceImpl implements CommandService {

    public static final int MAX_RETRIES = 5;

    private MessageDispatcher commandDispatcher;
    private final TransactionTemplate transactionTemplate;
    private final List<CertificateAuthorityEventVisitor> eventVisitors;
    private final CommandAuditService commandAuditService;
    private final CommandHandlerMetrics commandHandlerMetrics;
    private final LockCertificateAuthorityHandler lockCertificateAuthorityHandler;
    private final EntityManager entityManager;

    private final MeterRegistry meterRegistry;
    private final Map<Class<?>, Timer> commandExecutionTimers = new ConcurrentHashMap<>();
    private final Counter commandRetryCounter;

    @Inject
    public CommandServiceImpl(
        MessageDispatcher commandDispatcher,
        TransactionTemplate transactionTemplate,
        List<CertificateAuthorityEventVisitor> eventVisitors,
        CommandAuditService commandAuditService,
        CommandHandlerMetrics commandHandlerMetrics,
        LockCertificateAuthorityHandler lockCertificateAuthorityHandler,
        EntityManager entityManager,
        MeterRegistry meterRegistry
    ) {
        this.commandDispatcher = commandDispatcher;
        this.transactionTemplate = transactionTemplate;
        this.eventVisitors = eventVisitors;
        this.commandAuditService = commandAuditService;
        this.commandHandlerMetrics = commandHandlerMetrics;
        this.lockCertificateAuthorityHandler = lockCertificateAuthorityHandler;
        this.entityManager = entityManager;

        this.meterRegistry = meterRegistry;

        this.commandRetryCounter = Counter.builder("rpkicore.command.transaction.retries")
                .description("Number of retries for commands because of transaction failures.")
                .baseUnit("total")
                .register(meterRegistry);
    }

    @Override
    public VersionedId getNextId() {
        Query q = entityManager.createNativeQuery("SELECT nextval('seq_all')");
        q.setFlushMode(FlushModeType.COMMIT); // no need to do dirty checking
        BigInteger next = (BigInteger) q.getSingleResult();
        return new VersionedId(next.longValue());
    }

    @SuppressWarnings("try")
    @Override
    public CommandStatus execute(final CertificateAuthorityCommand command) {
        MDC.put("command", command.getCommandGroup() + ":" + command.getCommandType() + ":" + command.getCertificateAuthorityId());
        try {
            return executeCommandWithRetries(command);
        } finally {
            MDC.remove("command");
        }
    }

    private CommandStatus executeCommandWithRetries(CertificateAuthorityCommand command) {
        int retryCount = 0;
        while (true) {
            try {
                return executeTimedCommand(command);
            } catch (OptimisticLockException | PessimisticLockException | TransientDataAccessException e) {
                // Locking exceptions are most often transient, so retry a few times
                if (retryCount >= MAX_RETRIES) {
                    log.error("Error processing command after {} tries: {}", retryCount, command, e);
                    // Metrics are registered on the `LockCertificateAuthorityHandler` since this is the one
                    // mostly triggering this problem, but they could also occur later in a TX. Unfortunately
                    // we don't know when/where exactly.
                    commandHandlerMetrics.track(lockCertificateAuthorityHandler).failure();
                    throw e;
                } else {
                    retryCount++;
                    commandRetryCounter.increment();
                    long sleepForMs = (20 + (long) (Math.random() * 30)) << retryCount;
                    log.info("Command failed with possibly transient locking exception {}, retry {} in {} ms: {}", e.getClass().getName(), retryCount, sleepForMs, command);
                    sleepUninterruptibly(sleepForMs, TimeUnit.MILLISECONDS);
                }
            } catch (EntityTagDoesNotMatchException | PreconditionRequiredException | IllegalResourceException | NotHolderOfResourcesException | PrivateAsnsUsedException e) {
                // Do not log these user generated commands: This causes very noisy logs.
                log.info("Aborted a (user) command: {} for reason {}", command, e.getMessage());
                throw e;
            } catch (Exception e) {
                log.warn("Error processing command: {}", command, e);
                throw e;
            }
        }
    }

    private CommandStatus executeTimedCommand(CertificateAuthorityCommand command) {
        Timer timer = commandExecutionTimers.computeIfAbsent(command.getClass(), clazz ->
            Timer.builder("rpkicore.command.execution.duration")
                .description("execution duration of command")
                .tag("command", clazz.getSimpleName())
                .maximumExpectedValue(Duration.ofSeconds(10))
                .publishPercentileHistogram()
                .register(meterRegistry)
        );
        return timer.record(() -> executeCommand(command));
    }

    @SuppressWarnings("try")
    private CommandStatus executeCommand(CertificateAuthorityCommand command) {
        final CommandStatus commandStatus = new CommandStatus();
        transactionTemplate.executeWithoutResult(status -> {
            EventDelegateTracker.get().reset();
            CommandContext commandContext = commandAuditService.startRecording(command);
            List<EventSubscription> subscriptions = eventVisitors.stream().map(visitor -> ManagedCertificateAuthority.subscribe(visitor, commandContext)).collect(Collectors.toList());
            try (
                EventSubscription commandAuditSubscription = ManagedCertificateAuthority.EVENTS.subscribe(commandContext::recordEvent)
            ) {
                commandStatus.setTransactionStatus(status);
                commandDispatcher.dispatch(command, commandStatus);
                commandAuditService.finishRecording(commandContext);
                log.debug("Command completed.");
            } catch (CommandWithoutEffectException e) {
                log.debug("Command without effect: {}", command);
                commandStatus.setHasEffect(false);
                status.setRollbackOnly();
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            } finally {
                subscriptions.forEach(EventSubscription::close);
                EventDelegateTracker.get().reset();
            }
        });
        return commandStatus;
    }

    @VisibleForTesting
    void setCommandDispatcher(MessageDispatcher commandDispatcher) {
        this.commandDispatcher = commandDispatcher;
    }
}
