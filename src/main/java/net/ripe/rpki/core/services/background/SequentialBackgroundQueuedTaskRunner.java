package net.ripe.rpki.core.services.background;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs background tasks sequentially while holding the global services lock.
 */
@Service
@Slf4j
public class SequentialBackgroundQueuedTaskRunner extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode
    implements SmartLifecycle
{

    private ExecutorService executor;

    public SequentialBackgroundQueuedTaskRunner(BackgroundTaskRunner backgroundTaskRunner) {
        super(backgroundTaskRunner);
    }

    @Override
    protected void runService(Map<String, String> parameters) {
        log.warn("this service runs automatically when tasks are submitted");
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    public void submit(@NonNull String description, @NonNull Runnable action, @NonNull Consumer<Exception> onException) {
        if (!isRunning()) {
            throw new RejectedExecutionException("execution has been stopped, tasks can no longer be submitted");
        }
        executor.execute(() -> executeTask(description, task(action, onException)));
    }

    @Override
    public boolean isRunning() {
        return executor != null && !executor.isShutdown();
    }

    @Override
    public void start() {
        executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, getName()));
        log.info("started");
    }

    @Override
    public void stop() {
        if (!isRunning()) {
            return;
        }
        try {
            log.info("stopping");
            executor.shutdown();
            boolean stopped = executor.awaitTermination(30, TimeUnit.SECONDS);
            log.info("{}", stopped ? "stopped" : "executor termination timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor = null;
        }
    }

    private void executeTask(String description, BackgroundTaskRunner.Task task) {
        log.info("starting execution of task: {}", description);

        Pair<BackgroundServiceExecutionResult.Status, Long> result;
        do {
            result = doExecute(() -> {
                try {
                    task.execute();
                } catch (Exception e) {
                    log.error("error running task {}", description, e);
                    task.onException(e);
                }
            });
        } while (result.getLeft() == BackgroundServiceExecutionResult.Status.SKIPPED);

        log.info("finished execution of task: {} in {}ms", description, result.getRight());
    }
}
