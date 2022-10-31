package net.ripe.rpki.core.write.services.command;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.CommandContext;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import net.ripe.rpki.services.impl.handlers.CertificateAuthorityCommandHandler;
import net.ripe.rpki.services.impl.handlers.CommandHandlerMetrics;
import net.ripe.rpki.services.impl.handlers.MessageDispatcher;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.OptimisticLockException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static net.ripe.rpki.core.write.services.command.CommandServiceImpl.MAX_RETRIES;
import static net.ripe.rpki.server.api.commands.CertificateAuthorityCommandGroup.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CommandServiceImplTest {

    private MessageDispatcher messageDispatcher;
    private List<TransactionStatus> transactionStatuses;


    private CertificateAuthorityCommand command;

    private CommandServiceImpl subject;
    private SimpleMeterRegistry meterRegistry;

    @Before
    public void setUp() {
        command = mock(CertificateAuthorityCommand.class);
        messageDispatcher = mock(MessageDispatcher.class);
        transactionStatuses = new ArrayList<>();
        CommandAuditService commandAuditService = mock(CommandAuditService.class);
        when(commandAuditService.startRecording(any())).thenAnswer((args) -> new CommandContext(args.getArgument(0)));

        final TransactionTemplate transactionTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                transactionStatuses.add(new DefaultTransactionStatus(null,true,true,true,true,null));
                return action.doInTransaction(transactionStatuses.get(transactionStatuses.size()-1));
            }
        };

        meterRegistry = new SimpleMeterRegistry();
        subject = new CommandServiceImpl(messageDispatcher, transactionTemplate, Collections.emptyList(), commandAuditService, null, meterRegistry);
    }

    @Test
    public void should_dispatch_command() {
        CommandStatus status = subject.execute(command);
        verify(messageDispatcher).dispatch(command, status);
    }

    @Test
    public void should_rollback_transaction_when_dispatcher_throws_command_without_effect_exception() {
        doThrow(new CommandWithoutEffectException(command)).when(messageDispatcher).dispatch(eq(command), any(CommandStatus.class));

        final CommandStatus status = subject.execute(command);
        assertFalse(status.isHasEffect());
    }

    @Test
    public void should_rollback_transaction_when_exception_is_thrown() {
        doThrow(new RuntimeException("test exception")).when(messageDispatcher).dispatch(eq(command), any(CommandStatus.class));

        try {
            subject.execute(command);
            fail("Exception expected");
        } catch (Exception expected) {
        }

        assertTrue(transactionStatuses.get(0).isRollbackOnly());
    }

    @Test
    public void should_rollback_transaction_when_command_has_no_effect_exception_is_thrown() {
        doThrow(new CommandWithoutEffectException("test exception")).when(messageDispatcher).dispatch(eq(command), any(CommandStatus.class));

        subject.execute(command);

        assertTrue(transactionStatuses.get(0).isRollbackOnly());
    }

    @Test
    public void should_set_and_clear_current_command_on_logging_mdc() {
        AtomicReference<String> mdcMessage = new AtomicReference<>();
        doAnswer(invocation -> {
            mdcMessage.set(MDC.get("command"));
            return null;
        }).when(messageDispatcher).dispatch(any(), any());

        subject.execute(new TestCommand(new VersionedId(12, 13)));

        assertThat(mdcMessage.get()).isEqualTo("USER:TestCommand:12");
        assertThat(MDC.get("command")).isNull();
    }

    private static class TestCommand extends CertificateAuthorityCommand {

        TestCommand(VersionedId certificateAuthorityId) {
            super(certificateAuthorityId, USER);
        }

        @Override
        public String getCommandSummary() {
            return getClass().getName();
        }
    }


    private static class TopTestCommand extends TestCommand {
        TopTestCommand() {
            super(new VersionedId(1L));
        }
    }

    private static class NestedTestCommand extends TestCommand {
        NestedTestCommand() {
            super(new VersionedId(2L));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void should_rollback_nested_transaction_when_it_has_no_effect() {
        final CommandStatus[] nestedCommandStatus = {null};
        final MessageDispatcher messageDispatcher = new MessageDispatcher() {
            @Override
            protected List<CertificateAuthorityCommandHandler<CertificateAuthorityCommand>> makeOrderedHandlerList() {
                final CertificateAuthorityCommandHandler<?> handler1 = new CertificateAuthorityCommandHandler<TopTestCommand>() {
                    @Override
                    public Class<TopTestCommand> commandType() {
                        return TopTestCommand.class;
                    }

                    @Override
                    public void handle(TopTestCommand command, CommandStatus commandStatus) {
                        nestedCommandStatus[0] = subject.execute(new NestedTestCommand());
                    }
                };
                final CertificateAuthorityCommandHandler<?> handler2 = new CertificateAuthorityCommandHandler<NestedTestCommand>() {
                    @Override
                    public Class<NestedTestCommand> commandType() {
                        return NestedTestCommand.class;
                    }

                    @Override
                    public void handle(NestedTestCommand command, CommandStatus commandStatus) {
                        throw new CommandWithoutEffectException("do nothing here");
                    }
                };
                final List<CertificateAuthorityCommandHandler<CertificateAuthorityCommand>> hs = new ArrayList<>();
                hs.add((CertificateAuthorityCommandHandler<CertificateAuthorityCommand>) handler1);
                hs.add((CertificateAuthorityCommandHandler<CertificateAuthorityCommand>) handler2);
                return hs;
            }
        };
        messageDispatcher.setMetrics(new CommandHandlerMetrics(new SimpleMeterRegistry()));
        messageDispatcher.init();
        subject.setCommandDispatcher(messageDispatcher);

        final CommandStatus commandStatus = subject.execute(new TopTestCommand());

        //Top transaction has effect so it is not rolled back
        assertTrue(commandStatus.isHasEffect());
        assertFalse(commandStatus.getTransactionStatus().isRollbackOnly());

        //Nested has no effect so transaction is rolled back.
        assertFalse(nestedCommandStatus[0].isHasEffect());
        assertTrue(nestedCommandStatus[0].getTransactionStatus().isRollbackOnly());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void should_rollback_transaction_when_nested_command_throws_an_exception() {
        final MessageDispatcher messageDispatcher = new MessageDispatcher() {
            @Override
            protected List<CertificateAuthorityCommandHandler<CertificateAuthorityCommand>> makeOrderedHandlerList() {
                final CertificateAuthorityCommandHandler<?> handler1 = new CertificateAuthorityCommandHandler<TopTestCommand>() {
                    @Override
                    public Class<TopTestCommand> commandType() {
                        return TopTestCommand.class;
                    }

                    @Override
                    public void handle(TopTestCommand command, CommandStatus commandStatus) {
                        subject.execute(new NestedTestCommand());
                    }
                };
                final CertificateAuthorityCommandHandler<?> handler2 = new CertificateAuthorityCommandHandler<NestedTestCommand>() {
                    @Override
                    public Class<NestedTestCommand> commandType() {
                        return NestedTestCommand.class;
                    }

                    @Override
                    public void handle(NestedTestCommand command, CommandStatus commandStatus) {
                        throw new RuntimeException("I'm crashing");
                    }
                };
                final List<CertificateAuthorityCommandHandler<CertificateAuthorityCommand>> hs = new ArrayList<>();
                hs.add((CertificateAuthorityCommandHandler<CertificateAuthorityCommand>) handler1);
                hs.add((CertificateAuthorityCommandHandler<CertificateAuthorityCommand>) handler2);
                return hs;
            }
        };
        messageDispatcher.setMetrics(new CommandHandlerMetrics(new SimpleMeterRegistry()));
        messageDispatcher.init();
        subject.setCommandDispatcher(messageDispatcher);

        try {
            subject.execute(new TopTestCommand());
            fail("Exception expected");
        } catch (Exception expected) {
        }
    }

    @Test
    public void should_retry_transaction_on_locking_exception() {
        assertThat(meterRegistry.get("rpkicore.command.transaction.retries").counter().count()).isZero();

        AtomicInteger count = new AtomicInteger(0);
        doAnswer((invocation) -> {
            count.incrementAndGet();
            throw new OptimisticLockException("test exception");
        }).when(messageDispatcher).dispatch(eq(command), any(CommandStatus.class));

        assertThatThrownBy(() -> subject.execute(command)).isInstanceOf(OptimisticLockException.class);

        assertThat(count.get()).isEqualTo(1 + MAX_RETRIES);
        // one regular execution, two retries.
        assertThat(meterRegistry.get("rpkicore.command.transaction.retries").counter().count()).isEqualTo(MAX_RETRIES);
    }

    @Test
    public void should_retry_on_transaction_exception_only() {
        AtomicInteger count = new AtomicInteger(0);
        doAnswer((invocation) -> {
            count.incrementAndGet();
            throw new IllegalStateException("test exception");
        }).when(messageDispatcher).dispatch(eq(command), any(CommandStatus.class));

        assertThatThrownBy(() -> subject.execute(command)).isInstanceOf(IllegalStateException.class);

        // no retries
        assertThat(count.get()).isEqualTo(1);
        assertThat(meterRegistry.get("rpkicore.command.transaction.retries").counter().count()).isZero();
    }

    @Test
    public void should_measure_command_execution_duration() {
        subject.execute(command);

        assertThat(meterRegistry.get("rpkicore.command.execution.duration").timer()).isNotNull();
    }
}
