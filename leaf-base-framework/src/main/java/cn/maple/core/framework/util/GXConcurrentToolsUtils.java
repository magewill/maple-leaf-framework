package cn.maple.core.framework.util;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.NumberUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class GXConcurrentToolsUtils {
    public static final String FLAG_SPECIAL_VALUE = "BRT";

    private static final int DEFAULT_TIME_OUT = 5;

    private static final Logger LOG = LoggerFactory.getLogger(GXConcurrentToolsUtils.class);

    private static final double DEFAULT_CORE_POOL_SIZE_FACTOR = 1.0;

    private static final double DEFAULT_MAX_POOL_SIZE_FACTOR = 2.0;

    private static final int DEFAULT_QUEUE_CAPACITY = 10000;

    private static final long DEFAULT_KEEP_ALIVE_TIME = 60000;

    private static final ThreadPoolExecutor EXECUTOR_SERVICE;

    private static final AtomicLong TASK_COUNTER = new AtomicLong(0);

    private static final AtomicLong SUCCESS_COUNTER = new AtomicLong(0);

    private static final AtomicLong FAILURE_COUNTER = new AtomicLong(0);

    private static final AtomicLong TOTAL_EXECUTION_TIME = new AtomicLong(0);

    private static final AtomicLong MAX_EXECUTION_TIME = new AtomicLong(0);

    private static final AtomicLong ACTIVE_TASKS = new AtomicLong(0);

    static {
        int cpuCores = Runtime.getRuntime().availableProcessors();

        double corePoolSizeFactor = NumberUtil.parseDouble(
                System.getProperty("maple.thread.core.factor"),
                DEFAULT_CORE_POOL_SIZE_FACTOR
        );
        int corePoolSize = Math.max(1, (int) (cpuCores * corePoolSizeFactor));

        double maxPoolSizeFactor = NumberUtil.parseDouble(
                System.getProperty("maple.thread.max.factor"),
                DEFAULT_MAX_POOL_SIZE_FACTOR
        );
        int maxPoolSize = Math.max(corePoolSize, (int) (cpuCores * maxPoolSizeFactor));

        int queueCapacity = NumberUtil.parseInt(
                System.getProperty("maple.thread.queue.capacity"),
                DEFAULT_QUEUE_CAPACITY
        );

        long keepAliveTime = NumberUtil.parseLong(
                System.getProperty("maple.thread.keepalive"),
                DEFAULT_KEEP_ALIVE_TIME
        );

        ThreadFactory threadFactory = ThreadFactoryBuilder.create()
                .setNamePrefix("maple-concurrent-thread-pool-")
                .setUncaughtExceptionHandler((t, e) -> LOG.error("Uncaught exception in thread {}", t.getName(), e))
                .build();

        EXECUTOR_SERVICE = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        EXECUTOR_SERVICE.allowCoreThreadTimeOut(true);

        LOG.info("Initialized thread pool: coreSize={}, maxSize={}, queueCapacity={}, keepAliveTime={}ms",
                corePoolSize, maxPoolSize, queueCapacity, keepAliveTime);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                LOG.info("Shutting down thread pool via JVM shutdown hook...");
                EXECUTOR_SERVICE.shutdown();
                if (!EXECUTOR_SERVICE.awaitTermination(5, TimeUnit.SECONDS)) {
                    int unfinishedTasks = EXECUTOR_SERVICE.getQueue().size();
                    LOG.warn("{} tasks did not complete in time, forcing shutdown", unfinishedTasks);
                    EXECUTOR_SERVICE.shutdownNow();
                }
                LOG.info("Thread pool shutdown successfully via JVM shutdown hook");
            } catch (InterruptedException e) {
                EXECUTOR_SERVICE.shutdownNow();
                Thread.currentThread().interrupt();
                LOG.error("Thread pool shutdown interrupted via JVM shutdown hook", e);
            }
        }));
    }

    private GXConcurrentToolsUtils() {
        throw new AssertionError("Utility class, cannot be instantiated");
    }

    private static <T> T withMdcContext(Map<String, String> mdcContext, Supplier<T> task) {
        Map<String, String> originalMdc = MDC.getCopyOfContextMap();
        try {
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }
            return task.get();
        } finally {
            if (originalMdc != null) {
                MDC.setContextMap(originalMdc);
            } else {
                MDC.clear();
            }
        }
    }

    private static void ensureTraceId(Map<String, String> mdcContext) {
        if (mdcContext == null || !mdcContext.containsKey("traceId")) {
            GXTraceIdContextUtils.setTraceIdIfAbsent();
        }
    }

    public static <T> CompletableFuture<T> composerFuture(Supplier<T> callable, ConcurrentMap<String, Object> results, String resultKey) {
        Objects.requireNonNull(callable, "callable cannot be null");
        Objects.requireNonNull(results, "results cannot be null");
        Objects.requireNonNull(resultKey, "resultKey cannot be null");

        final long startTime = System.currentTimeMillis();

        final Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        final long taskId = TASK_COUNTER.incrementAndGet();

        ACTIVE_TASKS.incrementAndGet();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Task {} for key {} submitted. Active tasks: {}. {}",
                    taskId, resultKey, ACTIVE_TASKS.get(), getThreadPoolStatus());
        }

        Supplier<T> wrappedTask = () -> withMdcContext(mdcContext, () -> {
            try {
                ensureTraceId(mdcContext);

                return callable.get();
            } catch (Exception e) {
                Throwable rootCause = findRootCause(e);
                String errorType = rootCause != null ? rootCause.getClass().getName() : e.getClass().getName();
                LOG.error("Task {} for key {} execution error: {} ({})",
                        taskId, resultKey, e.getMessage(), errorType, e);
                throw e;
            }
        });

        final CompletableFuture<T> future = CompletableFuture.supplyAsync(wrappedTask, EXECUTOR_SERVICE);

        future.whenComplete((result, ex) -> {
            try {
                ACTIVE_TASKS.decrementAndGet();

                long executionTime = System.currentTimeMillis() - startTime;
                TOTAL_EXECUTION_TIME.addAndGet(executionTime);

                updateMaxExecutionTime(executionTime);

                if (ex == null) {
                    SUCCESS_COUNTER.incrementAndGet();

                    results.put(resultKey, result);

                    if (executionTime > 1000) {
                        LOG.warn("Task {} for key {} completed in {} ms (slow execution). {}",
                                taskId, resultKey, executionTime, getThreadPoolStatus());
                    } else if (LOG.isDebugEnabled()) {
                        LOG.debug("Task {} for key {} completed in {} ms. {}",
                                taskId, resultKey, executionTime, getThreadPoolStatus());
                    }
                } else {
                    FAILURE_COUNTER.incrementAndGet();

                    Throwable rootCause = findRootCause(ex);
                    String errorMessage = rootCause != null ? rootCause.getMessage() : ex.getMessage();
                    String errorType = rootCause != null ? rootCause.getClass().getName() : ex.getClass().getName();

                    String stackTrace = getStackTraceFirstLines(rootCause != null ? rootCause : ex, 3);

                    LOG.error("Task {} for key {} failed after {} ms: {} ({})\nStack trace: {}\n{}",
                            taskId, resultKey, executionTime, errorMessage, errorType,
                            stackTrace, getThreadPoolStatus(), ex);

                    results.put(resultKey, FLAG_SPECIAL_VALUE);
                }
            } catch (Exception e) {
                LOG.error("Error in task completion callback: {}", e.getMessage(), e);
            }
        });

        return future;
    }

    private static void updateMaxExecutionTime(long executionTime) {
        while (true) {
            long currentMax = MAX_EXECUTION_TIME.get();
            if (executionTime <= currentMax) {
                break;
            }
            if (MAX_EXECUTION_TIME.compareAndSet(currentMax, executionTime)) {
                break;
            }
        }
    }

    public static String getTaskStatistics() {
        long totalTasks = TASK_COUNTER.get();
        long successTasks = SUCCESS_COUNTER.get();
        long failureTasks = FAILURE_COUNTER.get();
        long totalTime = TOTAL_EXECUTION_TIME.get();
        long maxTime = MAX_EXECUTION_TIME.get();
        long activeTasks = ACTIVE_TASKS.get();

        double avgTime = totalTasks > 0 ? (double) totalTime / totalTasks : 0;
        double successRate = totalTasks > 0 ? (double) successTasks / totalTasks * 100 : 0;

        return String.format(
                "Task Statistics: Total=%d, Active=%d, Success=%d, Failure=%d, Success Rate=%.2f%%, Avg Time=%.2fms, Max Time=%dms",
                totalTasks,
                activeTasks,
                successTasks,
                failureTasks,
                successRate,
                avgTime,
                maxTime
        );
    }

    public static String getThreadPoolStatus() {
        int activeThreads = EXECUTOR_SERVICE.getActiveCount();
        int poolSize = EXECUTOR_SERVICE.getPoolSize();
        int corePoolSize = EXECUTOR_SERVICE.getCorePoolSize();
        int maxPoolSize = EXECUTOR_SERVICE.getMaximumPoolSize();
        int queueSize = EXECUTOR_SERVICE.getQueue().size();
        long completedTasks = EXECUTOR_SERVICE.getCompletedTaskCount();
        long totalTasks = EXECUTOR_SERVICE.getTaskCount();

        return String.format(
                "Thread Pool Status: Active=%d, Current=%d, Core=%d, Max=%d, QueueSize=%d, Completed=%d, Total=%d, Utilization=%.2f%%",
                activeThreads,
                poolSize,
                corePoolSize,
                maxPoolSize,
                queueSize,
                completedTasks,
                totalTasks,
                poolSize > 0 ? (double) activeThreads / poolSize * 100 : 0
        );
    }

    public static void allOf(List<CompletableFuture<?>> completableFutures, int timeOut, TimeUnit unit) {
        Objects.requireNonNull(completableFutures, "completableFutures cannot be null");
        if (completableFutures.isEmpty()) {
            throw new IllegalArgumentException("completableFutures cannot be empty");
        }
        if (timeOut < 0) {
            throw new IllegalArgumentException("timeOut cannot be negative: " + timeOut);
        }
        Objects.requireNonNull(unit, "unit cannot be null");

        final long startTime = System.currentTimeMillis();
        final int taskCount = completableFutures.size();

        final Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        final String initialPoolStatus = getThreadPoolStatus();

        boolean wasInterrupted = Thread.currentThread().isInterrupted();

        try {
            withMdcContext(mdcContext, () -> {
                ensureTraceId(mdcContext);

                LOG.info("Waiting for {} tasks to complete with timeout {} {}. {}",
                        taskCount,
                        timeOut,
                        unit,
                        initialPoolStatus);
                return null;
            });

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    completableFutures.toArray(new CompletableFuture[0])
            );

            allFutures.get(timeOut, unit);

            long executionTime = System.currentTimeMillis() - startTime;
            withMdcContext(mdcContext, () -> {
                LOG.info("All {} tasks completed in {} ms. {}. {}",
                        taskCount,
                        executionTime,
                        getTaskStatistics(),
                        getThreadPoolStatus());
                return null;
            });

        } catch (InterruptedException e) {
            long executionTime = System.currentTimeMillis() - startTime;

            TaskStatusInfo statusInfo = collectTaskStatusInfo(completableFutures, taskCount);

            handleTaskCancellation(completableFutures, statusInfo, executionTime, mdcContext,
                    "Thread interrupted", e);

            wasInterrupted = true;

            throw new RuntimeException(String.format(
                    "Thread interrupted while waiting for tasks: %s. Only %d of %d tasks completed (%.2f%%)",
                    e.getMessage(),
                    statusInfo.completedCount,
                    taskCount,
                    statusInfo.completionRate), e);
        } catch (ExecutionException e) {
            long executionTime = System.currentTimeMillis() - startTime;

            Throwable rootCause = findRootCause(e);
            String errorMessage = rootCause != null ? rootCause.getMessage() : e.getMessage();
            String errorType = rootCause != null ? rootCause.getClass().getName() : e.getClass().getName();

            TaskStatusInfo statusInfo = collectTaskStatusInfo(completableFutures, taskCount);

            handleTaskCancellation(completableFutures, statusInfo, executionTime, mdcContext,
                    String.format("Task execution failed: %s (%s)", errorMessage, errorType), e);

            throw new RuntimeException(String.format(
                    "Task execution failed: %s (%s). Only %d of %d tasks completed (%.2f%%)",
                    errorMessage,
                    errorType,
                    statusInfo.completedCount,
                    taskCount,
                    statusInfo.completionRate), e);
        } catch (TimeoutException e) {
            long executionTime = System.currentTimeMillis() - startTime;

            TaskStatusInfo statusInfo = collectTaskStatusInfo(completableFutures, taskCount);

            handleTaskCancellation(completableFutures, statusInfo, executionTime, mdcContext,
                    String.format("Tasks timed out after %d %s", timeOut, unit), e);

            throw new RuntimeException(String.format(
                    "Tasks timed out after %d %s. Only %d of %d tasks completed (%.2f%%)",
                    timeOut,
                    unit,
                    statusInfo.completedCount,
                    taskCount,
                    statusInfo.completionRate), e);
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;

            Throwable rootCause = findRootCause(e);
            String errorMessage = rootCause != null ? rootCause.getMessage() : e.getMessage();
            String errorType = rootCause != null ? rootCause.getClass().getName() : e.getClass().getName();

            TaskStatusInfo statusInfo = collectTaskStatusInfo(completableFutures, taskCount);

            handleTaskCancellation(completableFutures, statusInfo, executionTime, mdcContext,
                    String.format("Unexpected error: %s (%s)", errorMessage, errorType), e);

            throw new RuntimeException(String.format(
                    "Unexpected error while waiting for tasks: %s (%s)",
                    errorMessage,
                    errorType), e);
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void shutdownThreadPool() {
        if (EXECUTOR_SERVICE.isShutdown()) {
            LOG.info("Thread pool is already shut down, skipping shutdown operation");
            return;
        }

        long shutdownTimeout = NumberUtil.parseLong(
                System.getProperty("maple.thread.shutdown.timeout"),
                5L
        );
        if (shutdownTimeout < 0) {
            shutdownTimeout = 5L;
            LOG.warn("Invalid shutdown timeout specified, using default 5 seconds");
        }

        boolean wasInterrupted = Thread.currentThread().isInterrupted();

        try {
            String poolStatus = getThreadPoolStatus();
            LOG.info("Initiating thread pool shutdown. Current status: {}", poolStatus);

            EXECUTOR_SERVICE.shutdown();

            if (!EXECUTOR_SERVICE.awaitTermination(shutdownTimeout, TimeUnit.SECONDS)) {
                int unfinishedTasks = EXECUTOR_SERVICE.getQueue().size();
                LOG.warn("Thread pool did not terminate within {} seconds, {} tasks remain, forcing shutdown",
                        shutdownTimeout, unfinishedTasks);

                List<Runnable> unexecutedTasks = EXECUTOR_SERVICE.shutdownNow();
                LOG.warn("Forced shutdown completed, {} tasks were not executed", unexecutedTasks.size());
            } else {
                LOG.info("Thread pool shut down gracefully within {} seconds", shutdownTimeout);
            }

            LOG.info("Thread pool shutdown completed. Final status: {}", getThreadPoolStatus());
        } catch (InterruptedException e) {
            EXECUTOR_SERVICE.shutdownNow();
            Thread.currentThread().interrupt();
            LOG.error("Thread pool shutdown interrupted, forced shutdown executed", e);
        } catch (Exception e) {
            LOG.error("Unexpected error during thread pool shutdown: {}", e.getMessage(), e);
            EXECUTOR_SERVICE.shutdownNow();
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Throwable findRootCause(Throwable throwable) {
        return findRootCause(throwable, new java.util.HashSet<>(), 0);
    }

    private static Throwable findRootCause(Throwable throwable, java.util.Set<Throwable> seen, int depth) {
        final int MAX_DEPTH = 20;

        if (throwable == null) {
            return null;
        }

        if (depth >= MAX_DEPTH || !seen.add(throwable)) {
            return throwable;
        }

        Throwable cause = throwable.getCause();
        if (cause == null) {
            return throwable;
        }

        if (cause == throwable) {
            return throwable;
        }

        return findRootCause(cause, seen, depth + 1);
    }

    private static TaskStatusInfo collectTaskStatusInfo(List<CompletableFuture<?>> futures, int totalCount) {
        long completedCount = futures.stream().filter(CompletableFuture::isDone).count();
        long unfinishedCount = totalCount - completedCount;
        double completionRate = totalCount > 0 ? (double) completedCount / totalCount * 100 : 0;

        return new TaskStatusInfo(completedCount, unfinishedCount, completionRate);
    }

    private static int cancelUnfinishedTasks(List<CompletableFuture<?>> futures) {
        if (futures == null || futures.isEmpty()) {
            return 0;
        }

        AtomicLong cancelledCount = new AtomicLong(0);
        AtomicLong failedToCancel = new AtomicLong(0);
        AtomicLong alreadyDoneCount = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        boolean wasInterrupted = Thread.currentThread().isInterrupted();

        try {
            for (CompletableFuture<?> future : futures) {
                if (future == null) {
                    continue;
                }

                if (future.isDone()) {
                    alreadyDoneCount.incrementAndGet();
                    continue;
                }

                try {
                    boolean cancelled = future.cancel(true);
                    if (cancelled) {
                        cancelledCount.incrementAndGet();
                    } else {
                        failedToCancel.incrementAndGet();
                        if (future.isDone()) {
                            alreadyDoneCount.incrementAndGet();
                            failedToCancel.decrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    failedToCancel.incrementAndGet();
                    LOG.warn("Failed to cancel a task: {}", e.getMessage(), e);
                }
            }
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        if (cancelledCount.get() > 0 || failedToCancel.get() > 0) {
            LOG.info("Cancelled {} tasks, failed to cancel {} tasks, already done {} tasks in {} ms",
                    cancelledCount.get(), failedToCancel.get(), alreadyDoneCount.get(), duration);
        }

        return cancelledCount.intValue();
    }

    private static void handleTaskCancellation(List<CompletableFuture<?>> futures,
                                               TaskStatusInfo statusInfo,
                                               long executionTime,
                                               Map<String, String> mdcContext,
                                               String errorMessage,
                                               Throwable throwable) {
        withMdcContext(mdcContext, () -> {
            LOG.error("{} after {} ms while waiting for {} tasks. Completion rate: {}/{}({}%). {}. {}",
                    errorMessage,
                    executionTime,
                    statusInfo.completedCount + statusInfo.unfinishedCount,
                    statusInfo.completedCount,
                    statusInfo.completedCount + statusInfo.unfinishedCount,
                    String.format("%.2f", statusInfo.completionRate),
                    getTaskStatistics(),
                    getThreadPoolStatus(),
                    throwable);
            return null;
        });

        int cancelledCount = cancelUnfinishedTasks(futures);
        withMdcContext(mdcContext, () -> {
            LOG.warn("Cancelled {} of {} incomplete tasks due to {}",
                    cancelledCount, statusInfo.unfinishedCount, errorMessage.toLowerCase());
            return null;
        });
    }

    public static void allOf(List<CompletableFuture<?>> completableFutures) {
        Objects.requireNonNull(completableFutures, "completableFutures cannot be null");
        allOf(completableFutures, DEFAULT_TIME_OUT, TimeUnit.SECONDS);
    }

    private static String getStackTraceFirstLines(Throwable throwable, int lineCount) {
        if (throwable == null) {
            return "";
        }

        StackTraceElement[] stackTrace = throwable.getStackTrace();
        if (stackTrace == null || stackTrace.length == 0) {
            return "<no stack trace available>";
        }

        StringBuilder sb = new StringBuilder();
        int count = Math.min(lineCount, stackTrace.length);

        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append("    at ").append(stackTrace[i]);
        }

        if (stackTrace.length > count) {
            sb.append("\n    ... ").append(stackTrace.length - count).append(" more");
        }

        return sb.toString();
    }

    private record TaskStatusInfo(long completedCount, long unfinishedCount, double completionRate) {
    }
}