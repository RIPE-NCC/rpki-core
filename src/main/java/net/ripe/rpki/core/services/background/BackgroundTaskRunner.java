package net.ripe.rpki.core.services.background;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.server.api.security.RunAsUserHolder;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Slf4j
@Component
public class BackgroundTaskRunner implements SmartLifecycle {
    public static final int MAX_ALLOWED_EXCEPTIONS = 20;

    private final ActiveNodeService activeNodeService;

    // Create a separate pool to avoid blocking the whole
    // ForkJoinPool.default() with threads waiting for IO
    private final ForkJoinPool forkJoinPool = new ForkJoinPool();
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    public BackgroundTaskRunner(ActiveNodeService activeNodeService, MeterRegistry meterRegistry) {
        this.activeNodeService = activeNodeService;
        ExecutorServiceMetrics.monitor(meterRegistry, forkJoinPool, "rpkicore.backgroundtask.forkjoinpool");
    }

    public boolean isActiveNode() {
        return activeNodeService.isActiveNode();
    }

    public String getCurrentNodeName() {
        return activeNodeService.getCurrentNodeName();
    }

    @Override
    public void start() {
        // do nothing
    }

    @Override
    public void stop() {
        if (stopping.getAndSet(true)) {
            // Already stopping
            return;
        }

        log.info("application is shutting down, stopping background services");

        boolean quiescent = forkJoinPool.awaitQuiescence(30, TimeUnit.SECONDS);

        log.info("background services stopped {}", quiescent ? "successfully" : "timed out");
    }

    @Override
    public boolean isRunning() {
        return !forkJoinPool.isShutdown();
    }

    public interface Task {
        void execute();

        void onException(Exception e);
    }

    public void runParallel(Stream<Task> tasks) {
        MaxExceptionsTemplate maxExceptionsTemplate = new MaxExceptionsTemplate(20);
        forkJoinPool.submit(() -> tasks.parallel().forEach(task -> maxExceptionsTemplate.wrap(task))).join();
        if (maxExceptionsTemplate.maxExceptionsOccurred()) {
            throw new BackgroundServiceException("Too many exceptions encountered, suspecting problems that affect ALL CAs.");
        }
    }

    public Task task(Runnable task, Consumer<Exception> onException) {
        return new Task() {

            @Override
            public void execute() {
                RunAsUserHolder.asAdmin(task::run);
            }

            @Override
            public void onException(Exception e) {
                onException.accept(e);
            }
        };
    }

    private class MaxExceptionsTemplate {
        private final int maxAllowed;
        private final AtomicInteger numberOfExceptions = new AtomicInteger(0);

        public MaxExceptionsTemplate(int maxAllowed) {
            this.maxAllowed = maxAllowed;
        }

        public void wrap(final Task task) {
            if (stopping.get() || maxExceptionsOccurred()) {
                return;
            }
            try {
                task.execute();
            } catch (Exception e) {
                numberOfExceptions.incrementAndGet();
                task.onException(e);
            }
        }

        boolean maxExceptionsOccurred() {
            return numberOfExceptions.get() > maxAllowed;
        }
    }
}
