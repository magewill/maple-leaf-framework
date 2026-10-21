package cn.maple.core.datasource.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        try (AnnotationConfigApplicationContext context = contextWithDefaultProperties()) {
            assertTrue(context.containsBean("myBatisValidationAsyncTaskExecutor"));
            assertTrue(context.getBean("myBatisEventAsyncTaskExecutor", AsyncTaskExecutor.class)
                    != context.getBean("myBatisValidationAsyncTaskExecutor", AsyncTaskExecutor.class));
        }
    }

    @Test
    void asyncTaskInheritsDatasourceContextAndClearsItAfterwards() throws Exception {
        GXMyBatisAsyncListenerExecutorConfig config = configured(false);
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.myBatisEventAsyncTaskExecutor();
        try {
            GXDynamicContextHolder.push("slave1");
            CountDownLatch firstRun = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<String> capturedContext =
                    new java.util.concurrent.atomic.AtomicReference<>();
            executor.execute(() -> {
                capturedContext.set(GXDynamicContextHolder.peek());
                firstRun.countDown();
            });
            assertTrue(firstRun.await(2, TimeUnit.SECONDS));
            awaitIdle(executor);
            assertEquals("slave1", capturedContext.get());

            GXDynamicContextHolder.poll();
            CountDownLatch secondRun = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<String> nextTaskContext =
                    new java.util.concurrent.atomic.AtomicReference<>("unset");
            executor.execute(() -> {
                nextTaskContext.set(GXDynamicContextHolder.peek());
                secondRun.countDown();
            });
            assertTrue(secondRun.await(2, TimeUnit.SECONDS));
            assertNull(nextTaskContext.get());
        } finally {
            GXDynamicContextHolder.clear();
            executor.shutdown();
        }
    }

    @Test
    void validationExecutorUsesPlatformThreadsWhenVirtualThreadsAreDisabled() {
        try (AnnotationConfigApplicationContext context = contextWithDefaultProperties()) {
            Object executor = context.getBean("myBatisValidationAsyncTaskExecutor");
            assertInstanceOf(ThreadPoolTaskExecutor.class, executor);
        }
    }

    @Test
    void validationExecutorUsesVirtualThreadsWhenEnabled() {
        GXMyBatisAsyncListenerProperties properties = defaultProperties();
        properties.getVirtual().setEnabled(true);
        GXMyBatisAsyncListenerExecutorConfig config =
                new GXMyBatisAsyncListenerExecutorConfig(properties, new StandardEnvironment());
        assertInstanceOf(SimpleAsyncTaskExecutor.class, config.myBatisValidationAsyncTaskExecutor());
    }

    private AnnotationConfigApplicationContext contextWithDefaultProperties() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(GXMyBatisAsyncListenerProperties.class, GXMyBatisAsyncListenerProperties::new);
        context.register(GXMyBatisAsyncListenerExecutorConfig.class);
        context.refresh();
        return context;
    }

    private GXMyBatisAsyncListenerExecutorConfig configured(boolean virtualThreadsEnabled) {
        GXMyBatisAsyncListenerProperties properties = defaultProperties();
        properties.setCorePoolSizeFactor(0.001D);
        properties.setMaxPoolSizeFactor(0.001D);
        properties.setQueueCapacity(0);
        properties.setKeepAliveSeconds(0);
        properties.setLongTaskThresholdMs(0L);
        properties.setAwaitTerminationSeconds(1);
        properties.getVirtual().setEnabled(virtualThreadsEnabled);
        properties.getVirtual().setConcurrencyLimit(1);
        return new GXMyBatisAsyncListenerExecutorConfig(properties, new StandardEnvironment());
    }

    private GXMyBatisAsyncListenerProperties defaultProperties() {
        GXMyBatisAsyncListenerProperties properties = new GXMyBatisAsyncListenerProperties();
        properties.setAwaitTerminationSeconds(1);
        return properties;
    }

    private void awaitIdle(ThreadPoolTaskExecutor executor) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline && executor.getActiveCount() > 0) {
            Thread.sleep(5L);
        }
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
