package net.ripe.rpki.core.services.background;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.server.api.configuration.Environment;
import net.ripe.rpki.server.api.security.RunAsUserHolder;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.util.Time;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static net.ripe.rpki.server.api.security.RunAsUser.ADMIN;

@Slf4j
public abstract class BackgroundServiceWithAdminPrivilegesOnActiveNode implements BackgroundService {
    private static final ReentrantLock GLOBAL_LOCK = new ReentrantLock();

    private final ActiveNodeService activeNodeService;
    private final AtomicBoolean running;
    private final boolean useGlobalLocking;
    private volatile Instant waitingForLockSince;

    BackgroundServiceWithAdminPrivilegesOnActiveNode(ActiveNodeService activeNodeService, boolean useGlobalLocking) {
        this.activeNodeService = activeNodeService;
        this.running = new AtomicBoolean(false);
        this.useGlobalLocking = useGlobalLocking;
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
        return activeNodeService.isActiveNode(Environment.getInstanceName());
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
                    log.info("Waiting for lock on background services???");
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
            log.info("Skipping execution: not an active node ({})", activeNodeService.getCurrentNodeName());
            status = BackgroundServiceExecutionResult.Status.SKIPPED;
        }
        return Pair.of(status, duration);
    }

    protected abstract void runService();

    protected interface Command {
        void execute();

        void onException(Exception e);
    }

    protected class MaxExceptionsTemplate {
        private final int maxAllowed;
        private final AtomicInteger numberOfExceptions = new AtomicInteger(0);

        public MaxExceptionsTemplate(int maxAllowed) {
            this.maxAllowed = maxAllowed;
        }

        public void wrap(final Command command) {
            if (maxExceptionsOccurred()) {
                return;
            }
            try {
                command.execute();
            } catch (Exception e) {
                numberOfExceptions.incrementAndGet();
                command.onException(e);
            }
        }

        boolean maxExceptionsOccurred() {
            return numberOfExceptions.get() > maxAllowed;
        }

        public void checkIfMaxExceptionsOccurred() {
            if (maxExceptionsOccurred()) {
                String msg = "Too many exceptions encountered running job: '" + getName() + "'. Suspecting problems that affect ALL CAs.";
                log.error(msg);
                throw new BackgroundServiceException(msg);
            }
        }
    }
}
