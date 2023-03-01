package net.ripe.rpki.core.services.background;

import lombok.NonNull;
import net.ripe.rpki.server.api.security.RunAsUserHolder;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.util.Time;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static net.ripe.rpki.server.api.security.RunAsUser.ADMIN;

public abstract class BackgroundServiceWithAdminPrivilegesOnActiveNode implements BackgroundService {

    private static final ReentrantLock GLOBAL_LOCK = new ReentrantLock(true);

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    private enum State {
        IDLE, WAITING, RUNNING
    }

    /** Internal lock to protect <code>state</code> and <code>waitingForLockSince</code>. */
    private final Lock lock = new ReentrantLock(true);

    /**
     * The current state of this service. The state always transitions from <code>IDLE -> WAITING -> RUNNING</code> and
     * then back to <code>IDLE</code>.
     * <p>
     * Protected by <code>lock</code>`
     */
    @NonNull
    private State state = State.IDLE;

    /**
     * Time the state last changed.
     * <p>
     * Protected by <code>lock</code>`
     */
    @NonNull
    private Instant stateChangedAt = Instant.now();

    private final BackgroundTaskRunner backgroundTaskRunner;
    private final boolean useGlobalLocking;

    BackgroundServiceWithAdminPrivilegesOnActiveNode(BackgroundTaskRunner backgroundTaskRunner, boolean useGlobalLocking) {
        this.useGlobalLocking = useGlobalLocking;
        this.backgroundTaskRunner = backgroundTaskRunner;
    }

    @Override
    public String getStatus() {
        lock.lock();
        try {
            Duration duration = Duration.between(stateChangedAt, Instant.now());
            return state.name().toLowerCase(Locale.ROOT) + " for " + Time.formatDuration(duration);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isActive() {
        return backgroundTaskRunner.isActiveNode();
    }

    @Override
    public boolean isWaitingOrRunning() {
        lock.lock();
        try {
            switch (state) {
            case IDLE:
                return false;
            case WAITING:
            case RUNNING:
                return true;
            }
            throw new IllegalStateException("unknown state " + state);
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public BackgroundServiceExecutionResult execute(Map<String, String> parameters) {
        final Time.Timed<Pair<BackgroundServiceExecutionResult.Status, Long>> timed = Time.timed(() -> doExecute(() -> runService(parameters)));
        final long pureDuration = timed.getResult().getRight();
        final long fullDuration = timed.getTime();
        return new BackgroundServiceExecutionResult(pureDuration, fullDuration, timed.getResult().getLeft());
    }

    /**
     * @return Pair<status of execution, real duration of execution>
     * @param runService the code to run after acquiring the necessary locks
     */
    protected Pair<BackgroundServiceExecutionResult.Status, Long> doExecute(Runnable runService) {
        if (!isActive()) {
            log.info("Skipping execution: not an active node ({})", backgroundTaskRunner.getCurrentNodeName());
            return Pair.of(BackgroundServiceExecutionResult.Status.SKIPPED, 0L);
        }

        State oldState = tryStartWaiting();
        if (oldState != State.IDLE) {
            log.info("{} execution skipped because the previous execution is still {}.", getName(), oldState);
            return Pair.of(BackgroundServiceExecutionResult.Status.SKIPPED, 0L);
        }

        // Only allow a single service to execute at a time.
        if (useGlobalLocking && !GLOBAL_LOCK.tryLock()) {
            log.info("Waiting for lock on background services…");
            GLOBAL_LOCK.lock();
        }
        try {
            // Now we have the global lock, if needed. Update our state and run the service.
            updateState(State.RUNNING);
            try {
                RunAsUserHolder.set(ADMIN);
                try {
                    log.info("Started execution of background service: {}", getName());
                    long duration = Time.timed(runService);
                    log.info("Finished execution of background service: {}, duration: {}ms.", getName(), duration);
                    return Pair.of(BackgroundServiceExecutionResult.Status.SUCCESS, duration);
                } catch (Exception e) {
                    log.error("Execution of {} has been interrupted", getName(), e);
                    return Pair.of(BackgroundServiceExecutionResult.Status.FAILURE, 0L);
                } finally {
                    RunAsUserHolder.clear();
                }
            } finally {
                updateState(State.IDLE);
            }
        } finally {
            if (useGlobalLocking) {
                GLOBAL_LOCK.unlock();
            }
        }
    }

    private State tryStartWaiting() {
        lock.lock();
        try {
            State oldState = state;
            switch (oldState) {
            case RUNNING:
            case WAITING:
                break;
            case IDLE:
                state = State.WAITING;
                stateChangedAt = Instant.now();
                break;
            }
            return oldState;
        } finally {
            lock.unlock();
        }
    }

    private void updateState(State state) {
        lock.lock();
        try {
            this.state = state;
            this.stateChangedAt = Instant.now();
        } finally {
            lock.unlock();
        }
    }

    protected void runParallel(Stream<BackgroundTaskRunner.Task> tasks) {
        backgroundTaskRunner.runParallel(tasks);
    }

    protected BackgroundTaskRunner.Task task(Runnable task, Consumer<Exception> onError) {
        return backgroundTaskRunner.task(task, onError);
    }

    protected boolean parseForceUpdateParameter(Map<String, String> parameters) {
        return Boolean.parseBoolean(parameters.getOrDefault(FORCE_UPDATE_PARAMETER, "false"));
    }

    protected Optional<Integer> parseBatchSizeParameter(Map<String, String> parameters)
        throws IllegalArgumentException
    {
        return Optional.ofNullable(parameters.get(BATCH_SIZE_PARAMETER))
            .map(s -> {
                var value = Integer.parseUnsignedInt(s);
                if (value <= 0) {
                    throw new IllegalArgumentException("batch size must be greater than 0");
                }
                return value;
            });
    }

    protected abstract void runService(Map<String, String> parameters);

}
