package net.ripe.rpki.core.services.background;

import net.ripe.rpki.server.api.security.RunAsUserHolder;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.util.Time;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static net.ripe.rpki.server.api.security.RunAsUser.ADMIN;

public abstract class BackgroundServiceWithAdminPrivilegesOnActiveNode implements BackgroundService {

    private static final ReentrantLock GLOBAL_LOCK = new ReentrantLock();

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final boolean useGlobalLocking;
    private final BackgroundTaskRunner backgroundTaskRunner;
    private volatile Instant waitingForLockSince;

    BackgroundServiceWithAdminPrivilegesOnActiveNode(BackgroundTaskRunner backgroundTaskRunner, boolean useGlobalLocking) {
        this.useGlobalLocking = useGlobalLocking;
        this.backgroundTaskRunner = backgroundTaskRunner;
    }

    @Override
    public String getStatus() {
        // This implementation is not entirely thread-safe, that would require some more refactoring.
        Instant waiting = waitingForLockSince;
        if (waiting != null) {
            return "blocked since " + waiting;
        } else if (isRunning()) {
            return "running";
        } else {
            return "not running";
        }
    }

    @Override
    public boolean isActive() {
        return backgroundTaskRunner.isActiveNode();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isBlocked() {
        return useGlobalLocking && GLOBAL_LOCK.isLocked();
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public BackgroundServiceExecutionResult execute() {
        final Time.Timed<Pair<BackgroundServiceExecutionResult.Status, Long>> timed = Time.timed(this::doExecute);
        final long pureDuration = timed.getResult().getRight();
        final long fullDuration = timed.getTime();
        return new BackgroundServiceExecutionResult(pureDuration, fullDuration, timed.getResult().getLeft());
    }

    /**
     * @return Pair<status of execution, real duration of execution>
     */
    private Pair<BackgroundServiceExecutionResult.Status, Long> doExecute() {
        long duration = 0;
        BackgroundServiceExecutionResult.Status status = BackgroundServiceExecutionResult.Status.FAILURE;
        if (isActive()) {
            if (running.compareAndSet(false, true)) {
                // Only allow a single service to execute at a time.
                if (useGlobalLocking && !GLOBAL_LOCK.tryLock()) {
                    log.info("Waiting for lock on background services…");
                    waitingForLockSince = Instant.now();
                    try {
                        GLOBAL_LOCK.lock();
                    } finally {
                        waitingForLockSince = null;
                    }
                }
                RunAsUserHolder.set(ADMIN);
                try {
                    log.info("Started execution of background service: {}", getName());
                    duration = Time.timed(this::runService);
                    status = BackgroundServiceExecutionResult.Status.SUCCESS;
                    log.info("Finished execution of background service: {}, duration: {}ms.", getName(), duration);
                } catch (Exception e) {
                    log.error("Execution of {} has been interrupted", getName(), e);
                    status = BackgroundServiceExecutionResult.Status.FAILURE;
                } finally {
                    RunAsUserHolder.clear();
                    if (useGlobalLocking) {
                        GLOBAL_LOCK.unlock();
                    }
                    running.set(false);
                }
            } else {
                status = BackgroundServiceExecutionResult.Status.SKIPPED;
                log.info("{} execution skipped because the previous execution is still ongoing.", getName());
            }
        } else {
            log.info("Skipping execution: not an active node ({})", backgroundTaskRunner.getCurrentNodeName());
            status = BackgroundServiceExecutionResult.Status.SKIPPED;
        }
        return Pair.of(status, duration);
    }

    protected void runParallel(Stream<BackgroundTaskRunner.Task> tasks) {
        backgroundTaskRunner.runParallel(tasks);
    }

    protected BackgroundTaskRunner.Task task(Runnable task, Consumer<Exception> onError) {
        return backgroundTaskRunner.task(task, onError);
    }

    protected abstract void runService();

}
