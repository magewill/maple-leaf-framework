package cn.maple.core.datasource.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXMyBatisAsyncListenerExecutorConfigTest {
    @Test
    void rejectsSaturatedPlatformThreadTasksInsteadOfRunningThemOnTheCaller() throws Exception {
        GXMyBatisAsyncListenerExecutorConfig config = configured(false);
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.myBatisEventAsyncTaskExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean rejectedTaskRan = new AtomicBoolean();
        try {
            executor.execute(() -> await(started, release));
            assertTrue(started.await(1, TimeUnit.SECONDS));

            assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> rejectedTaskRan.set(true)));
            assertFalse(rejectedTaskRan.get());
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void rejectsSaturatedVirtualThreadTasksInsteadOfBlockingTheSubmitter() throws Exception {
        GXMyBatisAsyncListenerExecutorConfig config = configured(true);
        SimpleAsyncTaskExecutor executor = (SimpleAsyncTaskExecutor) config.myBatisEventAsyncTaskExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> submission = null;
        java.util.concurrent.ExecutorService probe = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            executor.execute(() -> await(started, release));
            assertTrue(started.await(1, TimeUnit.SECONDS));

            submission = probe.submit(() -> executor.execute(() -> {
            }));
            long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (!submission.isDone() && System.nanoTime() < deadlineNanos) {
                Thread.onSpinWait();
            }

            assertTrue(submission.isDone());
            ExecutionException exception = assertThrows(ExecutionException.class, submission::get);
            assertInstanceOf(RejectedExecutionException.class, exception.getCause());
        } finally {
            release.countDown();
            if (submission != null) {
                submission.cancel(true);
            }
            probe.shutdownNow();
            executor.close();
        }
    }

    @Test
    void registersAnIndependentExecutorForDatabaseValidation() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(GXMyBatisAsyncListenerExecutorConfig.class)) {
            assertTrue(context.containsBean("myBatisValidationAsyncTaskExecutor"));
            assertTrue(context.getBean("myBatisEventAsyncTaskExecutor", AsyncTaskExecutor.class)
                    != context.getBean("myBatisValidationAsyncTaskExecutor", AsyncTaskExecutor.class));
        }
    }

    private GXMyBatisAsyncListenerExecutorConfig configured(boolean virtualThreadsEnabled) {
        GXMyBatisAsyncListenerExecutorConfig config = new GXMyBatisAsyncListenerExecutorConfig();
        ReflectionTestUtils.setField(config, "corePoolSizeFactor", 0.001D);
        ReflectionTestUtils.setField(config, "maxPoolSizeFactor", 0.001D);
        ReflectionTestUtils.setField(config, "queueCapacity", 0);
        ReflectionTestUtils.setField(config, "keepAliveSeconds", 0);
        ReflectionTestUtils.setField(config, "longTaskThresholdMs", 0L);
        ReflectionTestUtils.setField(config, "awaitTerminationSeconds", 1);
        ReflectionTestUtils.setField(config, "virtualThreadsEnabled", virtualThreadsEnabled);
        ReflectionTestUtils.setField(config, "virtualConcurrencyLimit", 1);
        return config;
    }

    private void await(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try {
            release.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
