package cn.maple.core.datasource.config;

import cn.maple.core.datasource.util.GXDataFilterThreadLocalUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Thread pool configuration for asynchronous MyBatis listener events.
 */
@Log4j2
@Configuration
public class GXMyBatisAsyncListenerExecutorConfig {
    private static final double DEFAULT_CORE_POOL_SIZE_FACTOR = 1.0;
    private static final double DEFAULT_MAX_POOL_SIZE_FACTOR = 1.0;
    private static final int DEFAULT_QUEUE_CAPACITY = 1000;
    private static final int DEFAULT_KEEP_ALIVE_SECONDS = 120;
    private static final long DEFAULT_LONG_TASK_THRESHOLD_MS = 30000;
    private static final int DEFAULT_AWAIT_TERMINATION_SECONDS = 60;

    @Value("${maple.framework.mybatis.async.listener.core-pool-size-factor:" + DEFAULT_CORE_POOL_SIZE_FACTOR + "}")
    private double corePoolSizeFactor;

    @Value("${maple.framework.mybatis.async.listener.max-pool-size-factor:" + DEFAULT_MAX_POOL_SIZE_FACTOR + "}")
    private double maxPoolSizeFactor;

    @Value("${maple.framework.mybatis.async.listener.queue-capacity:" + DEFAULT_QUEUE_CAPACITY + "}")
    private int queueCapacity;

    @Value("${maple.framework.mybatis.async.listener.keep-alive-seconds:" + DEFAULT_KEEP_ALIVE_SECONDS + "}")
    private int keepAliveSeconds;

    @Value("${maple.framework.mybatis.async.listener.long-task-threshold-ms:" + DEFAULT_LONG_TASK_THRESHOLD_MS + "}")
    private long longTaskThresholdMs;

    @Value("${maple.framework.mybatis.async.listener.await-termination-seconds:" + DEFAULT_AWAIT_TERMINATION_SECONDS + "}")
    private int awaitTerminationSeconds;

    @Value("${maple.framework.mybatis.async.listener.virtual.enabled:${spring.threads.virtual.enabled:false}}")
    private boolean virtualThreadsEnabled;

    @Value("${maple.framework.mybatis.async.listener.virtual.concurrency-limit:0}")
    private int virtualConcurrencyLimit;

    @Bean("myBatisEventAsyncTaskExecutor")
    public AsyncTaskExecutor myBatisEventAsyncTaskExecutor() {
        int cpuCores = Math.max(1, Runtime.getRuntime().availableProcessors());
        int corePoolSize = Math.max(1, (int) Math.ceil(cpuCores * normalizeFactor(corePoolSizeFactor, DEFAULT_CORE_POOL_SIZE_FACTOR)));
        int maxPoolSize = Math.max(corePoolSize, (int) Math.ceil(corePoolSize * normalizeFactor(maxPoolSizeFactor, DEFAULT_MAX_POOL_SIZE_FACTOR)));
        int normalizedQueueCapacity = Math.max(0, queueCapacity);
        int normalizedKeepAliveSeconds = Math.max(0, keepAliveSeconds);
        int normalizedAwaitTerminationSeconds = Math.max(0, awaitTerminationSeconds);

        if (virtualThreadsEnabled) {
            return createVirtualThreadExecutor(maxPoolSize, normalizedAwaitTerminationSeconds);
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

        log.info("MyBatis event async executor initialized: corePoolSize={}, maxPoolSize={}, queueCapacity={}, keepAliveSeconds={}",
                corePoolSize, maxPoolSize, normalizedQueueCapacity, normalizedKeepAliveSeconds);
        return executor;
    }

    private AsyncTaskExecutor createVirtualThreadExecutor(int maxPoolSize, int normalizedAwaitTerminationSeconds) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("maple-framework-mybatis-event-virtual-");
        executor.setVirtualThreads(true);
        executor.setTaskDecorator(createTaskDecorator());
        executor.setTaskTerminationTimeout(toMillis(normalizedAwaitTerminationSeconds));
        executor.setCancelRemainingTasksOnClose(false);
        executor.setRejectTasksWhenLimitReached(false);
        executor.setConcurrencyLimit(resolveVirtualConcurrencyLimit(maxPoolSize));
        log.info("MyBatis event async executor initialized with virtual threads: concurrencyLimit={}, taskTerminationTimeoutSeconds={}",
                executor.getConcurrencyLimit(), normalizedAwaitTerminationSeconds);
        return executor;
    }

    private int resolveVirtualConcurrencyLimit(int maxPoolSize) {
        if (virtualConcurrencyLimit > 0) {
            return virtualConcurrencyLimit;
        }
        return Math.max(1, maxPoolSize);
    }

    private long toMillis(int seconds) {
        return (long) seconds * 1000L;
    }

    private double normalizeFactor(double factor, double defaultValue) {
        if (!Double.isFinite(factor) || factor <= 0) {
            return defaultValue;
        }
        return factor;
    }

    private RejectedExecutionHandler createRejectedExecutionHandler() {
        return (runnable, pool) -> {
            log.error("MyBatis event task rejected: activeCount={}, queueSize={}, poolSize={}, corePoolSize={}, maximumPoolSize={}, shutdown={}",
                    pool.getActiveCount(), pool.getQueue().size(), pool.getPoolSize(),
                    pool.getCorePoolSize(), pool.getMaximumPoolSize(), pool.isShutdown());
            if (pool.isShutdown()) {
                throw new RejectedExecutionException("MyBatis event executor has been shut down");
            }
            try {
                new ThreadPoolExecutor.CallerRunsPolicy().rejectedExecution(runnable, pool);
            } catch (RuntimeException e) {
                throw new RejectedExecutionException("Failed to run rejected MyBatis event task", e);
            }
        };
    }

    private TaskDecorator createTaskDecorator() {
        return runnable -> {
            Runnable wrappedRunnable = GXDynamicContextHolder.wrap(GXDataFilterThreadLocalUtils.wrap(runnable));
            return () -> {
                long startTime = System.nanoTime();
                String taskId = "task-" + System.nanoTime();
                boolean success = false;
                if (log.isDebugEnabled()) {
                    log.debug("MyBatis event task started: {}", taskId);
                }
                try {
                    wrappedRunnable.run();
                    success = true;
                } catch (Throwable throwable) {
                    long executionTime = toElapsedMillis(startTime);
                    log.error("MyBatis event task failed: executionTime={} ms, taskId={}", executionTime, taskId, throwable);
                    if (throwable instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    if (throwable instanceof Error error) {
                        throw error;
                    }
                    throw new IllegalStateException(throwable);
                } finally {
                    long executionTime = toElapsedMillis(startTime);
                    if (longTaskThresholdMs > 0 && executionTime > longTaskThresholdMs) {
                        log.warn("Long running MyBatis event task detected: executionTime={} ms, taskId={}", executionTime, taskId);
                    } else if (success && log.isDebugEnabled()) {
                        log.debug("MyBatis event task completed: executionTime={} ms, taskId={}", executionTime, taskId);
                    }
                }
            };
        };
    }

    private long toElapsedMillis(long startTimeNanos) {
        return (System.nanoTime() - startTimeNanos) / 1_000_000L;
    }
}
