package cn.maple.core.datasource.config;

import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerMetrics;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerTaskContext;
import cn.maple.core.datasource.util.GXDataFilterThreadLocalUtils;
import cn.maple.core.framework.util.GXMdcThreadUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executor configuration for MyBatis write-operation listeners.
 *
 * <p>Property keys are unchanged; values are bound through {@link GXMyBatisAsyncListenerProperties}.
 * Virtual-thread mode and pooled mode now share the same capacity semantics.</p>
 */
@Log4j2
@Configuration
public class GXMyBatisAsyncListenerExecutorConfig {
    private final GXMyBatisAsyncListenerProperties properties;
    private final Environment environment;

    private volatile ThreadPoolTaskExecutor eventExecutor;
    private volatile AsyncTaskExecutor virtualEventExecutor;

    public GXMyBatisAsyncListenerExecutorConfig(GXMyBatisAsyncListenerProperties properties,
                                                Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Bean("myBatisEventAsyncTaskExecutor")
    public AsyncTaskExecutor myBatisEventAsyncTaskExecutor() {
        int corePoolSize = resolveCorePoolSize();
        int maxPoolSize = resolveMaxPoolSize(corePoolSize);
        int normalizedQueueCapacity = Math.max(0, properties.getQueueCapacity());
        int normalizedKeepAliveSeconds = Math.max(0, properties.getKeepAliveSeconds());
        int normalizedAwaitTerminationSeconds = Math.max(0, properties.getAwaitTerminationSeconds());

        if (virtualThreadsEnabled()) {
            AsyncTaskExecutor executor = createVirtualThreadExecutor("event",
                    "maple-framework-mybatis-event-virtual-", maxPoolSize,
                    normalizedAwaitTerminationSeconds, true, true);
            this.virtualEventExecutor = executor;
            return executor;
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(normalizedQueueCapacity);
        executor.setKeepAliveSeconds(normalizedKeepAliveSeconds);
        executor.setThreadNamePrefix("maple-framework-mybatis-event-async-");
        executor.setThreadGroupName("maple-framework-mybatis-event-group");
        executor.setRejectedExecutionHandler(createRejectedExecutionHandler("event", true));
        executor.setTaskDecorator(createEventTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(normalizedAwaitTerminationSeconds);
        executor.initialize();
        executor.getThreadPoolExecutor().prestartAllCoreThreads();
        this.eventExecutor = executor;

        log.info("MyBatis event async executor initialized: corePoolSize={}, maxPoolSize={}, queueCapacity={}, keepAliveSeconds={}",
                corePoolSize, maxPoolSize, normalizedQueueCapacity, normalizedKeepAliveSeconds);
        return executor;
    }

    @Bean("myBatisValidationAsyncTaskExecutor")
    public AsyncTaskExecutor myBatisValidationAsyncTaskExecutor() {
        int maxPoolSize = resolveMaxPoolSize(resolveCorePoolSize());
        int normalizedAwaitTerminationSeconds = Math.max(0, properties.getAwaitTerminationSeconds());
        if (virtualThreadsEnabled()) {
            return createVirtualThreadExecutor("validation", "maple-framework-mybatis-validation-virtual-",
                    maxPoolSize, normalizedAwaitTerminationSeconds, false, false);
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(Math.max(0, properties.getQueueCapacity()));
        executor.setThreadNamePrefix("maple-framework-mybatis-validation-");
        executor.setThreadGroupName("maple-framework-mybatis-validation-group");
        // Database validation is not event delivery: its rejections and task timings must not be
        // mixed into the listener counters (FR-020).
        executor.setRejectedExecutionHandler(createRejectedExecutionHandler("validation", false));
        executor.setTaskDecorator(createContextTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(normalizedAwaitTerminationSeconds);
        executor.initialize();
        log.info("MyBatis validation executor initialized with platform threads");
        return executor;
    }

    @PreDestroy
    public void recordUnexecutedTasksOnShutdown() {
        ThreadPoolTaskExecutor executor = this.eventExecutor;
        if (executor != null) {
            recordCancelledTasks(executor.getThreadPoolExecutor().getQueue().size());
            return;
        }
        AsyncTaskExecutor virtualExecutor = this.virtualEventExecutor;
        if (virtualExecutor == null) {
            return;
        }
        // Virtual threads have no queue, so any in-flight task is the work that can remain unfinished.
        // The executor's own counter is used instead of the metrics snapshot, which also reflects tasks
        // that already completed but have not been accounted for yet.
        int unexecutedTasks = virtualExecutor instanceof GXVirtualThreadEventExecutor trackingExecutor
                ? trackingExecutor.inFlightTasks()
                : GXMyBatisListenerMetrics.snapshot().activeTasks();
        recordCancelledTasks(unexecutedTasks);
    }

    private void recordCancelledTasks(int unexecutedTasks) {
        if (unexecutedTasks <= 0) {
            return;
        }
        for (int index = 0; index < unexecutedTasks; index++) {
            GXMyBatisListenerMetrics.recordCancelled();
        }
        log.warn("MyBatis event executor shut down with {} unexecuted tasks", unexecutedTasks);
    }

    private int resolveCorePoolSize() {
        int cpuCores = Math.max(1, Runtime.getRuntime().availableProcessors());
        return Math.max(1, (int) Math.ceil(cpuCores * normalizeFactor(properties.getCorePoolSizeFactor(), 1.0D)));
    }

    private int resolveMaxPoolSize(int corePoolSize) {
        return Math.max(corePoolSize,
                (int) Math.ceil(corePoolSize * normalizeFactor(properties.getMaxPoolSizeFactor(), 1.0D)));
    }

    private boolean virtualThreadsEnabled() {
        Boolean configured = properties.getVirtual().getEnabled();
        if (configured != null) {
            return configured;
        }
        return environment.getProperty("spring.threads.virtual.enabled", Boolean.class, Boolean.FALSE);
    }

    private AsyncTaskExecutor createVirtualThreadExecutor(String executorType, String threadNamePrefix,
                                                          int maxPoolSize, int awaitTerminationSeconds,
                                                          boolean trackRejections, boolean trackMetrics) {
        SimpleAsyncTaskExecutor executor = trackRejections
                ? new GXVirtualThreadEventExecutor(threadNamePrefix)
                : new SimpleAsyncTaskExecutor(threadNamePrefix);
        executor.setVirtualThreads(true);
        executor.setTaskDecorator(trackMetrics ? createEventTaskDecorator() : createContextTaskDecorator());
        executor.setTaskTerminationTimeout((long) awaitTerminationSeconds * 1000L);
        executor.setCancelRemainingTasksOnClose(false);
        executor.setRejectTasksWhenLimitReached(true);
        executor.setConcurrencyLimit(resolveVirtualConcurrencyLimit(maxPoolSize));
        log.info("MyBatis {} async executor initialized with virtual threads: concurrencyLimit={}, taskTerminationTimeoutSeconds={}",
                executorType, executor.getConcurrencyLimit(), awaitTerminationSeconds);
        return executor;
    }

    /**
     * Counts and logs throttled rejections, which {@link SimpleAsyncTaskExecutor} would otherwise
     * report only by throwing, so {@code rejected} stays distinguishable from {@code failed}
     * (FR-020).
     */
    private static final class GXVirtualThreadEventExecutor extends SimpleAsyncTaskExecutor {
        private static final long serialVersionUID = 1L;

        private final AtomicInteger inFlightTasks = new AtomicInteger();

        private GXVirtualThreadEventExecutor(String threadNamePrefix) {
            super(threadNamePrefix);
        }

        // Overriding the deprecated overload is required: submit() and @Async both route through it,
        // so this is the only place where a throttled virtual-thread rejection can be counted.
        @SuppressWarnings("deprecation")
        @Override
        public void execute(Runnable task, long startTimeout) {
            inFlightTasks.incrementAndGet();
            try {
                super.execute(() -> {
                    try {
                        task.run();
                    } finally {
                        inFlightTasks.decrementAndGet();
                    }
                }, startTimeout);
            } catch (TaskRejectedException exception) {
                inFlightTasks.decrementAndGet();
                GXMyBatisListenerMetrics.recordRejected();
                GXMyBatisListenerMetrics.updateQueueSize(0);
                String operationId = GXMyBatisListenerTaskContext.currentOperationId();
                log.error("MyBatis event task rejected: operationId={}, mode=virtual, concurrencyLimit={}, activeTasks={}, reason={}",
                        operationId == null ? GXMyBatisListenerTaskContext.operationIdOf(task) : operationId,
                        getConcurrencyLimit(), GXMyBatisListenerMetrics.snapshot().activeTasks(),
                        exception.getMessage());
                throw exception;
            }
        }

        private int inFlightTasks() {
            return inFlightTasks.get();
        }
    }

    private int resolveVirtualConcurrencyLimit(int maxPoolSize) {
        int configuredLimit = properties.getVirtual().getConcurrencyLimit();
        if (configuredLimit < 0) {
            return -1;
        }
        if (configuredLimit > 0) {
            return configuredLimit;
        }
        return Math.max(1, maxPoolSize);
    }

    private double normalizeFactor(double factor, double defaultValue) {
        if (!Double.isFinite(factor) || factor <= 0) {
            return defaultValue;
        }
        return factor;
    }

    private RejectedExecutionHandler createRejectedExecutionHandler(String executorType, boolean trackMetrics) {
        return (runnable, pool) -> {
            if (trackMetrics) {
                GXMyBatisListenerMetrics.recordRejected();
                GXMyBatisListenerMetrics.updateQueueSize(pool.getQueue().size());
            }
            log.error("MyBatis {} task rejected: operationId={}, activeCount={}, queueSize={}, poolSize={}, corePoolSize={}, maximumPoolSize={}, shutdown={}",
                    executorType, GXMyBatisListenerTaskContext.operationIdOf(runnable),
                    pool.getActiveCount(), pool.getQueue().size(), pool.getPoolSize(),
                    pool.getCorePoolSize(), pool.getMaximumPoolSize(), pool.isShutdown());
            if (pool.isShutdown()) {
                throw new RejectedExecutionException("MyBatis " + executorType + " executor has been shut down");
            }
            throw new RejectedExecutionException("MyBatis " + executorType + " executor is saturated");
        };
    }

    /**
     * Propagates the datasource/MDC context onto the executor thread. Shared by both decorators so
     * listener tasks and validation tasks carry exactly the same context.
     */
    private Runnable wrapContext(Runnable runnable) {
        return GXMdcThreadUtils.wrap(
                GXDynamicContextHolder.wrap(GXDataFilterThreadLocalUtils.wrap(runnable)),
                MDC.getCopyOfContextMap());
    }

    /**
     * Context-only decorator for executors that do not deliver listener events, so their tasks never
     * touch listener counters or emit listener task logs.
     */
    private TaskDecorator createContextTaskDecorator() {
        return this::wrapContext;
    }

    private TaskDecorator createEventTaskDecorator() {
        long longTaskThresholdMs = properties.getLongTaskThresholdMs();
        return runnable -> {
            String operationId = GXMyBatisListenerTaskContext.currentOperationId();
            // A callback that writes again would otherwise restart the recursion counter at 0 on the
            // fresh executor thread, so the depth of the submitting write travels with the task and an
            // asynchronous callback chain is bounded exactly like a synchronous one (spec 边缘用例：递归触发).
            int deliveryDepth = GXMyBatisListenerTaskContext.currentDeliveryDepth();
            Runnable wrappedRunnable = GXMyBatisListenerTaskContext.withDeliveryDepth(deliveryDepth,
                    wrapContext(runnable));
            Runnable task = () -> {
                long startTime = System.nanoTime();
                boolean success = false;
                GXMyBatisListenerMetrics.taskStarted();
                if (log.isDebugEnabled()) {
                    log.debug("MyBatis event task started: operationId={}", operationId);
                }
                try {
                    wrappedRunnable.run();
                    success = true;
                } catch (Throwable throwable) {
                    long executionTime = toElapsedMillis(startTime);
                    log.error("MyBatis event task failed: operationId={}, executionTime={} ms",
                            operationId, executionTime, throwable);
                    if (throwable instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    if (throwable instanceof Error error) {
                        throw error;
                    }
                    throw new IllegalStateException(throwable);
                } finally {
                    GXMyBatisListenerMetrics.taskFinished();
                    updateQueueSizeQuietly();
                    long executionTime = toElapsedMillis(startTime);
                    if (longTaskThresholdMs > 0 && executionTime > longTaskThresholdMs) {
                        log.warn("Long running MyBatis event task detected: operationId={}, executionTime={} ms",
                                operationId, executionTime);
                    } else if (success && log.isDebugEnabled()) {
                        log.debug("MyBatis event task completed: operationId={}, executionTime={} ms",
                                operationId, executionTime);
                    }
                }
            };
            return GXMyBatisListenerTaskContext.label(operationId, task);
        };
    }

    private void updateQueueSizeQuietly() {
        ThreadPoolTaskExecutor executor = this.eventExecutor;
        if (executor == null) {
            return;
        }
        ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
        if (threadPoolExecutor != null) {
            GXMyBatisListenerMetrics.updateQueueSize(threadPoolExecutor.getQueue().size());
        }
    }

    private long toElapsedMillis(long startTimeNanos) {
        return (System.nanoTime() - startTimeNanos) / 1_000_000L;
    }
}
