package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


public class BackgroundServiceBeanTest {

    private MyBackgroundServiceBean subject;

    private ActiveNodeService activeNodeService;



    @Before
    public void setUp() {
        activeNodeService = mock(ActiveNodeService.class);
        when(activeNodeService.isActiveNode()).thenReturn(true);

        subject = new MyBackgroundServiceBean(activeNodeService);
    }

    @Test
    public void shouldReportNotRunningStateBeforeTheServiceMethodIsInvoked() {
        assertFalse(subject.isWaitingOrRunning());
    }

    @Test
    public void shouldBeActiveIfCurrentHostnameIsTheSameAsTheActive() {
        assertTrue(subject.isActive());
    }

    @Test
    public void shouldBeInactiveStateIfCurrentHostnameIsDifferentFromTheActive() {
        when(activeNodeService.isActiveNode()).thenReturn(false);

        assertFalse(subject.isActive());
    }

    @Test
    public void shouldExecuteServiceIfActiveAndNotRunning() {
        // Allow subject to terminate.
        subject.stoppingLatch.countDown();

        subject.execute();

        assertEquals(1, subject.getExecutionCounter());
    }

    @Test
    public void shouldNotExecuteServiceIfInactive() {
        when(activeNodeService.isActiveNode()).thenReturn(false);

        subject.execute();

        assertEquals(0, subject.getExecutionCounter());
    }

    @Test
    public void shouldNotExecuteServiceIfAlreadyRunning() throws Exception {
        new Thread(() -> subject.execute()).start();

        // Wait for process to run.
        if (!subject.runningLatch.await(1, TimeUnit.SECONDS)) {
            fail("failed to start within timeout");
        }
        assertEquals("process did not start running", 1, subject.getExecutionCounter());

        subject.execute(); // Skips process, since other thread is running.

        subject.stoppingLatch.countDown(); // Allow second thread to terminate.

        assertEquals(1, subject.getExecutionCounter()); // the second execution is skipped
    }

    private static class MyBackgroundServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {
        private int executionCounter;
        private final CountDownLatch runningLatch;
        private final CountDownLatch stoppingLatch;

        private MyBackgroundServiceBean(ActiveNodeService activeNodeService) {
            super(new BackgroundTaskRunner(activeNodeService, new SimpleMeterRegistry()));
            this.runningLatch = new CountDownLatch(1);
            this.stoppingLatch = new CountDownLatch(1);
        }

        @Override
        public String getName() {
            return "Test Service";
        }

        @Override
        protected void runService() {
            executionCounter++; //called from the execute() method within a synchronized block hence thread-safe
            runningLatch.countDown();
            try {
                if (!stoppingLatch.await(1, TimeUnit.SECONDS)) {
                    fail("failed to stop within timeout");
                }
            } catch (InterruptedException e) {
            }
        }

        public synchronized int getExecutionCounter() {
            return executionCounter;
        }
    }
}
