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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

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
            return createVirtualThreadExecutor("event", "maple-framework-mybatis-event-virtual-",
                    maxPoolSize, normalizedAwaitTerminationSeconds);
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(normalizedQueueCapacity);
        executor.setKeepAliveSeconds(normalizedKeepAliveSeconds);
        executor.setThreadNamePrefix("maple-framework-mybatis-event-async-");
        executor.setThreadGroupName("maple-framework-mybatis-event-group");
        executor.setRejectedExecutionHandler(createRejectedExecutionHandler());
        executor.setTaskDecorator(createTaskDecorator());
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
                    maxPoolSize, normalizedAwaitTerminationSeconds);
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(Math.max(0, properties.getQueueCapacity()));
        executor.setThreadNamePrefix("maple-framework-mybatis-validation-");
        executor.setThreadGroupName("maple-framework-mybatis-validation-group");
        executor.setRejectedExecutionHandler(createRejectedExecutionHandler());
        executor.setTaskDecorator(createTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(normalizedAwaitTerminationSeconds);
        executor.initialize();
        log.info("MyBatis validation executor initialized with platform threads");
        return executor;
    }

    @PreDestroy
    public void recordUnexecutedTasksOnShutdown() {
        ThreadPoolTaskExecutor executor = this.eventExecutor;
        if (executor == null) {
            return;
        }
        int remaining = executor.getThreadPoolExecutor().getQueue().size();
        for (int index = 0; index < remaining; index++) {
            GXMyBatisListenerMetrics.recordCancelled();
        }
        if (remaining > 0) {
            log.warn("MyBatis event executor shut down with {} unexecuted tasks", remaining);
        }
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
                                                          int maxPoolSize, int awaitTerminationSeconds) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(threadNamePrefix);
        executor.setVirtualThreads(true);
        executor.setTaskDecorator(createTaskDecorator());
        executor.setTaskTerminationTimeout((long) awaitTerminationSeconds * 1000L);
        executor.setCancelRemainingTasksOnClose(false);
        executor.setRejectTasksWhenLimitReached(true);
        executor.setConcurrencyLimit(resolveVirtualConcurrencyLimit(maxPoolSize));
        log.info("MyBatis {} async executor initialized with virtual threads: concurrencyLimit={}, taskTerminationTimeoutSeconds={}",
                executorType, executor.getConcurrencyLimit(), awaitTerminationSeconds);
        return executor;
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

    private RejectedExecutionHandler createRejectedExecutionHandler() {
        return (runnable, pool) -> {
            GXMyBatisListenerMetrics.recordRejected();
            GXMyBatisListenerMetrics.updateQueueSize(pool.getQueue().size());
            log.error("MyBatis event task rejected: operationId={}, activeCount={}, queueSize={}, poolSize={}, corePoolSize={}, maximumPoolSize={}, shutdown={}",
                    GXMyBatisListenerTaskContext.operationIdOf(runnable),
                    pool.getActiveCount(), pool.getQueue().size(), pool.getPoolSize(),
                    pool.getCorePoolSize(), pool.getMaximumPoolSize(), pool.isShutdown());
            if (pool.isShutdown()) {
                throw new RejectedExecutionException("MyBatis event executor has been shut down");
            }
            throw new RejectedExecutionException("MyBatis event executor is saturated");
        };
    }

    private TaskDecorator createTaskDecorator() {
        long longTaskThresholdMs = properties.getLongTaskThresholdMs();
        return runnable -> {
            String operationId = GXMyBatisListenerTaskContext.currentOperationId();
            Runnable wrappedRunnable = GXMdcThreadUtils.wrap(
                    GXDynamicContextHolder.wrap(GXDataFilterThreadLocalUtils.wrap(runnable)),
                    MDC.getCopyOfContextMap()
            );
            Runnable task = () -> {
                long startTime = System.nanoTime();
                boolean success = false;
                GXMyBatisListenerMetrics.taskStarted();
                if (log.isDebugEnabled()) {
                    log.debug("MyBatis event task started");
                }
                try {
                    wrappedRunnable.run();
                    success = true;
                } catch (Throwable throwable) {
                    long executionTime = toElapsedMillis(startTime);
                    log.error("MyBatis event task failed: executionTime={} ms", executionTime, throwable);
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
                        log.warn("Long running MyBatis event task detected: executionTime={} ms", executionTime);
                    } else if (success && log.isDebugEnabled()) {
                        log.debug("MyBatis event task completed: executionTime={} ms", executionTime);
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
