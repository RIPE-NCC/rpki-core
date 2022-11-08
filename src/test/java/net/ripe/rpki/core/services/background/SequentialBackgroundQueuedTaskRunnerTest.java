package net.ripe.rpki.core.services.background;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SequentialBackgroundQueuedTaskRunnerTest {

    private static final Consumer<Exception> NO_EXCEPTION = Assertions::fail;

    @Mock
    private ActiveNodeService activeNodeService;
    private SequentialBackgroundQueuedTaskRunner subject;

    @Before
    public void setUp() {
        when(activeNodeService.isActiveNode()).thenReturn(true);
        BackgroundTaskRunner backgroundTaskRunner = new BackgroundTaskRunner(activeNodeService, new SimpleMeterRegistry());
        subject = new SequentialBackgroundQueuedTaskRunner(backgroundTaskRunner);
        subject.start();
    }

    @After
    public void tearDown() {
        subject.stop();
    }

    @Test
    public void should_run_tasks_sequentially() throws InterruptedException {
        CountDownLatch task1Completed = new CountDownLatch(1);
        CountDownLatch task2Submitted = new CountDownLatch(1);
        CountDownLatch task2Completed = new CountDownLatch(1);

        subject.submit("1", () -> {
            try {
                assertThat(task2Submitted.await(100, TimeUnit.MILLISECONDS)).isTrue();
                assertThat(task2Completed.await(100, TimeUnit.MILLISECONDS)).isFalse();

                task1Completed.countDown();
            } catch (InterruptedException e) {
                fail(e);
            }
        }, NO_EXCEPTION);
        subject.submit("2", () -> {
            // Task 1 must have completed before task 2 starts running.
            assertThat(task1Completed.getCount()).isZero();

            task2Completed.countDown();
        }, NO_EXCEPTION);

        task2Submitted.countDown();
        assertThat(task1Completed.await(1, TimeUnit.SECONDS)).describedAs("task1 completed").isTrue();
        assertThat(task2Completed.await(1, TimeUnit.SECONDS)).describedAs("task2 completed").isTrue();
    }

    @Test
    public void should_run_many_tasks_sequentially() throws InterruptedException {
        final int threadCount = 100;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Integer> data = new ArrayList<>(threadCount);
        AtomicBoolean running = new AtomicBoolean(false);
        for (int i = 0; i < threadCount; ++i) {
            final int threadId = i;
            subject.submit("thread " + threadId, () -> {
                try {
                    start.await();
                    assertThat(running.compareAndSet(false, true)).isTrue();
                    data.add(threadId);
                    done.countDown();
                    assertThat(running.compareAndSet(true, false)).isTrue();
                } catch (InterruptedException e) {
                    fail(e);
                }
            }, NO_EXCEPTION);
        }

        start.countDown();
        assertThat(done.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(data).isEqualTo(Stream.iterate(0, x -> x + 1).limit(threadCount).collect(Collectors.toList()));
    }

    @Test
    public void should_reject_task_submission_when_stopped() {
        subject.stop();

        assertThatThrownBy(() -> subject.submit("test", () -> {}, NO_EXCEPTION))
            .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    public void should_handle_concurrent_submits() throws InterruptedException {
        final int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; ++i) {
            final int threadId = i;
            executor.execute(() -> {
                try {
                    ready.countDown();
                    start.await();
                    subject.submit("thread " + threadId, done::countDown, NO_EXCEPTION);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue();

        start.countDown();

        assertThat(done.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void should_pass_exception_to_onException_handler() throws InterruptedException {
        CountDownLatch handled = new CountDownLatch(1);
        AtomicReference<Exception> caught = new AtomicReference<>();

        subject.submit(
            "test exception",
            () -> {
                throw new RuntimeException("test");
            },
            (exception) -> {
                caught.set(exception);
                handled.countDown();
            }
        );

        assertThat(handled.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(caught).hasValueSatisfying(
            exception -> assertThat(exception).isInstanceOf(RuntimeException.class).hasMessage("test")
        );
    }

}
