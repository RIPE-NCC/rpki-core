package net.ripe.rpki.services.impl.handlers;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.KeyManagementInitiateRollCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MessageDispatcherTest {

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;

    private List<String> executedHandlers = new ArrayList<>();

    public static abstract class TestHandler implements CertificateAuthorityCommandHandler<CertificateAuthorityCommand> {
        @Override
        public Class<CertificateAuthorityCommand> commandType() {
            return CertificateAuthorityCommand.class;
        }
    }

    @Handler(order = 200)
    private final class MyCommandPersistenceHandler extends TestHandler {
        @Override
        public void handle(CertificateAuthorityCommand command, CommandStatus commandStatus) {
            executedHandlers.add("Persistence");
        }
    }

    @Handler(order = 0)
    private final class MyCertificateAuthorityConcurrentModificationHandler extends TestHandler {
        @Override
        public void handle(CertificateAuthorityCommand command, CommandStatus commandStatus) {
            executedHandlers.add("Concurrency");
        }
    }

    @Handler
    private final class MyAutoRolloverChildCAsCommandHandler extends KeyManagementInitiateRollCommandHandler {

        MyAutoRolloverChildCAsCommandHandler() {
            super(certificateAuthorityRepository, null, null, null, null);
        }

        @Override
        public void handle(KeyManagementInitiateRollCommand command, CommandStatus commandStatus) {
            executedHandlers.add("Rollover");
        }
    }

    @Test
    public void shouldSortHandlers() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        MessageDispatcher subject = new MessageDispatcher();
        subject.setApplicationContext(applicationContext);
        subject.setMetrics(new CommandHandlerMetrics(registry));

        Map<String, Object> beans = new HashMap<>();

        KeyManagementInitiateRollCommandHandler autoRolloverChildCAsCommandHandler = new MyAutoRolloverChildCAsCommandHandler();
        beans.put("autoroll", autoRolloverChildCAsCommandHandler);

        MyCertificateAuthorityConcurrentModificationHandler concurrentModificationHandler = new MyCertificateAuthorityConcurrentModificationHandler();
        beans.put("concurrent", concurrentModificationHandler);

        TestHandler commandPersistenceHandler = new MyCommandPersistenceHandler();
        beans.put("persist", commandPersistenceHandler);

        when(applicationContext.getBeansWithAnnotation(Handler.class)).thenReturn(beans);

        subject.init();

        subject.dispatch(new KeyManagementInitiateRollCommand(new VersionedId(0L), 0), CommandStatus.create());

        assertEquals(3, executedHandlers.size());
        assertEquals("Concurrency", executedHandlers.get(0));
        assertEquals("Rollover", executedHandlers.get(1));
        assertEquals("Persistence", executedHandlers.get(2));

        // 3 handlers, with {noop, success, failure} each
        assertThat(registry.find("rpkicore.commandhandler.call").counters()).asList().hasSize(9);
        // with a timer per handler
        assertThat(registry.find("rpkicore.commandhandler.duration").timers()).asList().hasSize(3);
    }
}
