package net.ripe.rpki.services.impl.handlers;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.CertificateAuthorityModificationCommand;
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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MessageDispatcherTest {

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;

    private List<String> executedHandlers = new ArrayList<>();


    @Handler(order = 200)
    private final class MyCommandPersistenceHandler extends CommandPersistenceHandler {
        public MyCommandPersistenceHandler() {
            super(certificateAuthorityRepository, null);
        }

        @Override
        public void handle(CertificateAuthorityCommand command, CommandStatus commandStatus) {
            executedHandlers.add("Persistence");
        }
    }

    @Handler(order = 0)
    private final class MyCertificateAuthorityConcurrentModificationHandler extends CertificateAuthorityConcurrentModificationHandler {
        public MyCertificateAuthorityConcurrentModificationHandler() {
            super(certificateAuthorityRepository, null, new SimpleMeterRegistry());
        }

        @Override
        public void handle(CertificateAuthorityModificationCommand command, CommandStatus commandStatus) {
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
        MessageDispatcher subject = new MessageDispatcher();
        subject.setApplicationContext(applicationContext);
        subject.setMetrics(new CommandHandlerMetrics(new SimpleMeterRegistry()));

        Map<String, Object> beans = new HashMap<>();

        KeyManagementInitiateRollCommandHandler autoRolloverChildCAsCommandHandler = new MyAutoRolloverChildCAsCommandHandler();
        beans.put("autoroll", autoRolloverChildCAsCommandHandler);

        CertificateAuthorityConcurrentModificationHandler concurrentModificationHandler = new MyCertificateAuthorityConcurrentModificationHandler();
        beans.put("concurrent", concurrentModificationHandler);

        CommandPersistenceHandler commandPersistenceHandler = new MyCommandPersistenceHandler();
        beans.put("persist", commandPersistenceHandler);

        when(applicationContext.getBeansWithAnnotation(Handler.class)).thenReturn(beans);

        subject.init();

        subject.dispatch(new KeyManagementInitiateRollCommand(new VersionedId(0L), 0), CommandStatus.create());

        assertEquals(3, executedHandlers.size());
        assertEquals("Concurrency", executedHandlers.get(0));
        assertEquals("Rollover", executedHandlers.get(1));
        assertEquals("Persistence", executedHandlers.get(2));
    }
}
