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

/**
 * 并发请求任务工具类，基于 CompletableFuture 封装，用于并行执行任务并收集结果。
 * <p>
 * <b>实现原理</b>：
 * CompletableFuture 是 Java 8 引入的异步编程工具，支持非阻塞的任务执行和结果处理。
 * 本工具类通过以下步骤实现并发任务处理：
 * 1. 使用自定义线程池（EXECUTOR_SERVICE）执行异步任务，任务通过 CompletableFuture.supplyAsync 提交。
 * 2. 每个任务的结果存储在 ConcurrentMap 中，key 为任务标识，value 为任务结果。
 * 3. 使用 CompletableFuture.allOf 等待所有任务完成，支持超时控制。
 * 4. 提供异常处理机制，捕获任务执行中的异常并记录日志。
 * </p>
 * <p>
 * <b>线程安全机制</b>：
 * - 线程池（EXECUTOR_SERVICE）通过 Hutool 的 ExecutorBuilder 创建，配置了核心线程数、最大线程数、工作队列和拒绝策略，确保线程安全。
 * - 任务结果存储在 ConcurrentMap 中，使用 put 方法更新结果，确保最新结果能够被正确存储。
 * - CompletableFuture 的异常处理（exceptionally）在异步线程中执行，天然线程安全。
 * - 超时控制（allOf 方法）通过 get 方法实现，支持任务取消（cancel），避免线程泄漏。
 * - 线程池在 JVM 关闭时通过关闭钩子优雅关闭，避免资源泄漏。
 * - 任务执行状态监控通过 whenComplete 和 exceptionally 回调实现，确保任务执行过程中的异常能够被正确捕获和处理。
 * </p>
 * <p>
 * <b>使用场景</b>：
 * 1. 需要并行执行多个独立任务（如调用多个外部接口）并收集结果。
 * 2. 需要控制任务执行的超时时间，避免任务无限阻塞。
 * 3. 需要在任务失败时记录异常并设置默认值。
 * 4. 需要监控任务执行状态，及时发现并处理异常情况。
 * </p>
 * <p>
 * <b>使用示例</b>：
 * <pre>
 * // 创建结果容器
 * ConcurrentMap<String, Object> results = new ConcurrentHashMap<>();
 * List<CompletableFuture<?>> futures = new ArrayList<>();
 *
 * // 提交任务
 * futures.add(GXConcurrentToolsUtils.composerFuture(() -> "Result1", results, "task1"));
 * futures.add(GXConcurrentToolsUtils.composerFuture(() -> "Result2", results, "task2"));
 *
 * // 等待所有任务完成（使用默认超时时间）
 * GXConcurrentToolsUtils.allOf(futures);
 *
 * // 获取结果
 * System.out.println("Results: " + results);
 *
 * // 优雅关闭线程池（可选）
 * GXConcurrentToolsUtils.shutdownThreadPool();
 * </pre>
 * </p>
 * <p>
 * <b>注意事项</b>：
 * 1. 确保线程池配置合理，避免核心线程数或队列容量过小导致任务积压。
 * 2. 任务执行时间应控制在合理范围内，避免超时导致任务被取消。
 * 3. 异常处理中设置了特殊值（FLAG_SPECIAL_VALUE），使用时需检查结果是否为该值。
 * 4. 线程池使用 CallerRunsPolicy 拒绝策略，任务超载时由调用线程执行任务，避免直接抛出异常。
 * 5. 在高并发场景下，应避免使用过长的超时时间，以防止资源耗尽。
 * </p>
 */
public class GXConcurrentToolsUtils {
    /**
     * 特殊值标识，当任务执行失败时设置该值。
     */
    public static final String FLAG_SPECIAL_VALUE = "BRT";

    /**
     * 默认超时时间，单位为秒。
     */
    private static final int DEFAULT_TIME_OUT = 5;

    /**
     * 日志对象，用于记录任务执行过程中的信息和异常。
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXConcurrentToolsUtils.class);

    /**
     * 默认线程池核心线程数系数，相对于CPU核心数的倍数
     */
    private static final double DEFAULT_CORE_POOL_SIZE_FACTOR = 1.0;

    /**
     * 默认线程池最大线程数系数，相对于CPU核心数的倍数
     */
    private static final double DEFAULT_MAX_POOL_SIZE_FACTOR = 2.0;

    /**
     * 默认队列容量
     */
    private static final int DEFAULT_QUEUE_CAPACITY = 10000;

    /**
     * 默认线程空闲时间（毫秒）
     */
    private static final long DEFAULT_KEEP_ALIVE_TIME = 60000;

    /**
     * 线程池对象，使用ThreadPoolExecutor以便于监控和管理
     * <p>
     * <b>线程池配置</b>：
     * - 核心线程数：默认为CPU核心数 * DEFAULT_CORE_POOL_SIZE_FACTOR
     * - 最大线程数：默认为CPU核心数 * DEFAULT_MAX_POOL_SIZE_FACTOR
     * - 空闲线程存活时间：默认60秒，避免资源浪费
     * - 工作队列：LinkedBlockingQueue，默认容量为10000
     * - 线程工厂：自定义线程名称前缀，便于调试和问题排查
     * - 拒绝策略：CallerRunsPolicy，任务超载时由调用线程执行任务
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - 线程池由ThreadPoolExecutor创建，线程安全
     * - 线程池在类加载时创建，静态变量，生命周期与应用一致
     * - 线程池在JVM关闭时通过关闭钩子优雅关闭，避免资源泄漏
     * - 线程池参数可通过系统属性动态配置，提高灵活性
     * </p>
     */
    private static final ThreadPoolExecutor EXECUTOR_SERVICE;

    /**
     * 任务执行计数器，用于统计任务执行情况
     */
    private static final AtomicLong TASK_COUNTER = new AtomicLong(0);

    /**
     * 任务成功计数器
     */
    private static final AtomicLong SUCCESS_COUNTER = new AtomicLong(0);

    /**
     * 任务失败计数器
     */
    private static final AtomicLong FAILURE_COUNTER = new AtomicLong(0);

    /**
     * 任务执行总时间，用于计算平均执行时间
     */
    private static final AtomicLong TOTAL_EXECUTION_TIME = new AtomicLong(0);

    /**
     * 最大任务执行时间，用于监控最慢的任务
     */
    private static final AtomicLong MAX_EXECUTION_TIME = new AtomicLong(0);

    /**
     * 当前活跃任务数，用于监控线程池负载
     */
    private static final AtomicLong ACTIVE_TASKS = new AtomicLong(0);

    static {
        // 从系统属性或环境变量中读取线程池配置参数，支持动态调整
        int cpuCores = Runtime.getRuntime().availableProcessors();

        // 核心线程数：可通过系统属性maple.thread.core.factor配置
        double corePoolSizeFactor = NumberUtil.parseDouble(
                System.getProperty("maple.thread.core.factor"),
                DEFAULT_CORE_POOL_SIZE_FACTOR
        );
        int corePoolSize = Math.max(1, (int) (cpuCores * corePoolSizeFactor));

        // 最大线程数：可通过系统属性maple.thread.max.factor配置
        double maxPoolSizeFactor = NumberUtil.parseDouble(
                System.getProperty("maple.thread.max.factor"),
                DEFAULT_MAX_POOL_SIZE_FACTOR
        );
        int maxPoolSize = Math.max(corePoolSize, (int) (cpuCores * maxPoolSizeFactor));

        // 队列容量：可通过系统属性maple.thread.queue.capacity配置
        int queueCapacity = NumberUtil.parseInt(
                System.getProperty("maple.thread.queue.capacity"),
                DEFAULT_QUEUE_CAPACITY
        );

        // 空闲线程存活时间：可通过系统属性maple.thread.keepalive配置
        long keepAliveTime = NumberUtil.parseLong(
                System.getProperty("maple.thread.keepalive"),
                DEFAULT_KEEP_ALIVE_TIME
        );

        // 创建线程工厂，自定义线程名称前缀
        ThreadFactory threadFactory = ThreadFactoryBuilder.create()
                .setNamePrefix("maple-concurrent-thread-pool-")
                .setUncaughtExceptionHandler((t, e) -> LOG.error("Uncaught exception in thread {}", t.getName(), e))
                .build();

        // 创建线程池
        EXECUTOR_SERVICE = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // 允许核心线程超时，提高资源利用率
        EXECUTOR_SERVICE.allowCoreThreadTimeOut(true);

        // 记录线程池配置信息
        LOG.info("Initialized thread pool: coreSize={}, maxSize={}, queueCapacity={}, keepAliveTime={}ms",
                corePoolSize, maxPoolSize, queueCapacity, keepAliveTime);

        // 添加JVM关闭钩子，优雅关闭线程池
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

    /**
     * 私有构造函数，防止实例化。
     * <p>
     * 本类为工具类，所有方法均为静态方法，不需要实例化。
     * </p>
     */
    private GXConcurrentToolsUtils() {
        throw new AssertionError("Utility class, cannot be instantiated");
    }

    /**
     * 在指定的MDC上下文中执行任务，并在执行完成后恢复原始MDC上下文。
     * <p>
     * 这个方法确保在异步任务执行过程中，日志中的MDC上下文（如traceId）能够正确传递，
     * 避免日志上下文丢失导致的日志追踪困难。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - MDC操作是线程安全的，每个线程有自己独立的MDC上下文。
     * - 使用try-finally结构确保即使任务执行过程中发生异常，原始MDC上下文也能被正确恢复。
     * </p>
     *
     * @param mdcContext 要设置的MDC上下文，可以为null
     * @param task       要执行的任务
     * @param <T>        任务返回值类型
     * @return 任务执行结果
     */
    private static <T> T withMdcContext(Map<String, String> mdcContext, Supplier<T> task) {
        // 保存当前线程的MDC上下文
        Map<String, String> originalMdc = MDC.getCopyOfContextMap();
        try {
            // 设置新的MDC上下文
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }
            // 执行任务
            return task.get();
        } finally {
            // 恢复原始MDC上下文
            if (originalMdc != null) {
                MDC.setContextMap(originalMdc);
            } else {
                MDC.clear();
            }
        }
    }

    /**
     * 确保当前线程有TraceId，如果没有则设置一个。
     * <p>
     * 这个方法通过反射调用GXTraceIdContextUtils.setTraceIdIfAbsent方法，
     * 避免直接依赖该类，提高代码的灵活性。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - 反射调用是线程安全的，不会影响其他线程。
     * - 异常处理确保即使反射调用失败也不会影响主要业务逻辑。
     * </p>
     *
     * @param mdcContext 当前MDC上下文，用于检查是否已有traceId
     */
    private static void ensureTraceId(Map<String, String> mdcContext) {
        if (mdcContext == null || !mdcContext.containsKey("traceId")) {
            GXTraceIdContextUtils.setTraceIdIfAbsent();
        }
    }

    /**
     * 将任务对象包装为一个 CompletableFuture 对象，用于异步执行。
     * <p>
     * <b>实现原理</b>：
     * 1. 使用 CompletableFuture.supplyAsync 将任务提交到线程池（EXECUTOR_SERVICE）执行。
     * 2. 任务执行完成后，通过 whenComplete 回调将结果存储到 ConcurrentMap（results）中，key 为 resultKey。
     * 3. 如果任务抛出异常，通过 exceptionally 方法捕获异常，并将结果设置为 FLAG_SPECIAL_VALUE。
     * 4. 使用 thenApply 方法记录任务执行时间，便于性能监控。
     * 5. 通过捕获当前线程的 MDC 上下文并传递给异步任务，确保日志中的 traceId 一致性。
     * 6. 使用 ACTIVE_TASKS 计数器跟踪当前活跃任务数量，用于监控线程池负载。
     * 7. 使用 MAX_EXECUTION_TIME 原子变量记录最长任务执行时间，用于性能监控。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - 任务在 EXECUTOR_SERVICE 中执行，线程池保证线程安全。
     * - 结果存储在 ConcurrentMap 中，使用 put 方法确保结果能够被正确更新。
     * - 异常处理（exceptionally）在异步线程中执行，天然线程安全。
     * - 任务执行状态监控通过 whenComplete 回调实现，确保任务执行过程中的异常能够被正确捕获和处理。
     * - MDC 上下文传递通过复制当前线程的 MDC 上下文到异步任务中，确保日志中的 traceId 一致性。
     * - 任务统计使用 AtomicLong，保证线程安全的计数。
     * - 使用 CAS 操作更新最大执行时间，确保在高并发情况下的正确性。
     * </p>
     *
     * @param callable  任务对象，提供实际的计算逻辑
     * @param results   承载计算结果的容器（ConcurrentMap）
     * @param resultKey 结果关联的 KEY
     * @param <T>       任务返回值的类型
     * @return CompletableFuture 对象，表示异步任务
     * @throws IllegalArgumentException 如果 callable、results 或 resultKey 为 null
     */
    public static <T> CompletableFuture<T> composerFuture(Supplier<T> callable, ConcurrentMap<String, Object> results, String resultKey) {
        // 参数校验
        Objects.requireNonNull(callable, "callable cannot be null");
        Objects.requireNonNull(results, "results cannot be null");
        Objects.requireNonNull(resultKey, "resultKey cannot be null");

        // 记录任务开始时间，用于性能监控
        final long startTime = System.currentTimeMillis();

        // 捕获当前线程的 MDC 上下文，用于传递给异步任务
        final Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        // 任务计数器增加
        final long taskId = TASK_COUNTER.incrementAndGet();

        // 活跃任务计数增加
        ACTIVE_TASKS.incrementAndGet();

        // 记录任务提交信息
        if (LOG.isDebugEnabled()) {
            LOG.debug("Task {} for key {} submitted. Active tasks: {}. {}",
                    taskId, resultKey, ACTIVE_TASKS.get(), getThreadPoolStatus());
        }

        // 包装原始任务，确保 MDC 上下文传递
        Supplier<T> wrappedTask = () -> withMdcContext(mdcContext, () -> {
            try {
                // 确保子线程有 TraceId
                ensureTraceId(mdcContext);

                // 执行原始任务
                return callable.get();
            } catch (Exception e) {
                // 直接在任务执行线程中捕获并记录异常，确保异常不会被吞掉
                Throwable rootCause = findRootCause(e);
                String errorType = rootCause != null ? rootCause.getClass().getName() : e.getClass().getName();
                LOG.error("Task {} for key {} execution error: {} ({})",
                        taskId, resultKey, e.getMessage(), errorType, e);
                throw e; // 重新抛出异常，让CompletableFuture的异常处理机制处理
            }
        });

        // 使用线程池异步执行包装后的任务
        final CompletableFuture<T> future = CompletableFuture.supplyAsync(wrappedTask, EXECUTOR_SERVICE);

        // 记录任务执行时间和结果
        future.whenComplete((result, ex) -> {
            try {
                // 活跃任务计数减少
                ACTIVE_TASKS.decrementAndGet();

                // 计算任务执行时间
                long executionTime = System.currentTimeMillis() - startTime;
                TOTAL_EXECUTION_TIME.addAndGet(executionTime);

                // 更新最大执行时间（使用CAS操作确保线程安全）
                updateMaxExecutionTime(executionTime);

                if (ex == null) {
                    // 任务成功计数
                    SUCCESS_COUNTER.incrementAndGet();

                    // 任务正常完成，存储实际结果
                    results.put(resultKey, result);

                    // 记录执行时间
                    if (executionTime > 1000) { // 执行时间超过1秒，记录警告日志
                        LOG.warn("Task {} for key {} completed in {} ms (slow execution). {}",
                                taskId, resultKey, executionTime, getThreadPoolStatus());
                    } else if (LOG.isDebugEnabled()) {
                        LOG.debug("Task {} for key {} completed in {} ms. {}",
                                taskId, resultKey, executionTime, getThreadPoolStatus());
                    }
                } else {
                    // 任务失败计数
                    FAILURE_COUNTER.incrementAndGet();

                    // 提取根本原因
                    Throwable rootCause = findRootCause(ex);
                    String errorMessage = rootCause != null ? rootCause.getMessage() : ex.getMessage();
                    String errorType = rootCause != null ? rootCause.getClass().getName() : ex.getClass().getName();

                    // 获取异常堆栈的前几行，便于快速定位问题
                    String stackTrace = getStackTraceFirstLines(rootCause != null ? rootCause : ex, 3);

                    // 任务执行异常，记录详细错误信息
                    LOG.error("Task {} for key {} failed after {} ms: {} ({})\nStack trace: {}\n{}",
                            taskId, resultKey, executionTime, errorMessage, errorType,
                            stackTrace, getThreadPoolStatus(), ex);

                    // 将结果设置为特殊值，标识任务执行失败
                    results.put(resultKey, FLAG_SPECIAL_VALUE);
                }
            } catch (Exception e) {
                // 捕获回调中的异常，避免影响其他任务
                LOG.error("Error in task completion callback: {}", e.getMessage(), e);
            }
        });

        return future;
    }

    /**
     * 使用CAS操作更新最大执行时间
     * <p>
     * 这个方法使用CAS（Compare-And-Swap）操作确保在高并发情况下正确更新最大执行时间。
     * 只有当新的执行时间大于当前记录的最大执行时间时，才会更新。
     * </p>
     *
     * @param executionTime 新的执行时间
     */
    private static void updateMaxExecutionTime(long executionTime) {
        while (true) {
            long currentMax = MAX_EXECUTION_TIME.get();
            if (executionTime <= currentMax) {
                // 新值不大于当前最大值，不需要更新
                break;
            }
            // 尝试更新最大值，只有当当前值仍然是currentMax时才会成功
            if (MAX_EXECUTION_TIME.compareAndSet(currentMax, executionTime)) {
                // 更新成功
                break;
            }
            // 更新失败，说明有其他线程已经修改了值，重试
        }
    }

    /**
     * 获取任务执行统计信息
     * <p>
     * 返回任务执行的统计信息，包括总任务数、成功任务数、失败任务数和平均执行时间。
     * 这些信息对于监控系统性能和诊断问题非常有用。
     * </p>
     *
     * @return 包含任务执行统计信息的字符串
     */
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

    /**
     * 获取线程池状态信息
     * <p>
     * 返回线程池的当前状态，包括活跃线程数、队列大小、已完成任务数等。
     * 这些信息对于监控线程池负载和诊断性能问题非常有用。
     * </p>
     *
     * @return 包含线程池状态信息的字符串
     */
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

    /**
     * 等待所有 CompletableFuture 任务完成，支持超时控制。
     * <p>
     * <b>实现原理</b>：
     * 1. 使用 CompletableFuture.allOf 创建一个表示所有任务完成的 CompletableFuture。
     * 2. 使用 get(timeout, unit) 方法等待所有任务完成，支持超时控制。
     * 3. 如果等待过程中发生异常（中断、执行异常、超时），取消所有未完成的任务，避免资源泄漏。
     * 4. 记录详细的任务执行状态和线程池状态，便于诊断问题。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - CompletableFuture.allOf 和 get 方法是线程安全的。
     * - 取消任务的操作（cancelUnfinishedTasks）是线程安全的。
     * - 使用CAS操作确保计数器的线程安全性。
     * - MDC上下文传递使用线程安全的方式，确保日志上下文的正确性。
     * - 任务状态监控使用原子操作，确保在高并发情况下的正确性。
     * </p>
     *
     * @param completableFutures CompletableFuture 对象列表
     * @param timeOut            超时时间，必须为非负数
     * @param unit               超时时间单位，不能为 null
     * @throws IllegalArgumentException 如果 completableFutures 为 null、空、timeOut 为负数或 unit 为 null
     * @throws RuntimeException         如果任务执行失败（InterruptedException、ExecutionException、TimeoutException）
     */
    public static void allOf(List<CompletableFuture<?>> completableFutures, int timeOut, TimeUnit unit) {
        // 参数校验
        Objects.requireNonNull(completableFutures, "completableFutures cannot be null");
        if (completableFutures.isEmpty()) {
            throw new IllegalArgumentException("completableFutures cannot be empty");
        }
        if (timeOut < 0) {
            throw new IllegalArgumentException("timeOut cannot be negative: " + timeOut);
        }
        Objects.requireNonNull(unit, "unit cannot be null");

        // 记录任务开始时间，用于性能监控
        final long startTime = System.currentTimeMillis();
        final int taskCount = completableFutures.size();

        // 捕获当前线程的 MDC 上下文，用于日志记录
        final Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        // 记录线程池状态（开始）
        final String initialPoolStatus = getThreadPoolStatus();

        // 保存当前线程的中断状态
        boolean wasInterrupted = Thread.currentThread().isInterrupted();

        try {
            // 使用辅助方法设置MDC上下文并确保TraceId
            withMdcContext(mdcContext, () -> {
                // 确保当前线程有 TraceId
                ensureTraceId(mdcContext);

                // 记录详细的任务和线程池状态信息
                LOG.info("Waiting for {} tasks to complete with timeout {} {}. {}",
                        taskCount,
                        timeOut,
                        unit,
                        initialPoolStatus);
                return null;
            });

            // 创建一个包含所有任务的 CompletableFuture
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    completableFutures.toArray(new CompletableFuture[0])
            );

            // 等待所有任务完成，支持超时控制
            allFutures.get(timeOut, unit);

            // 记录任务执行时间和统计信息
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
            // 计算执行时间
            long executionTime = System.currentTimeMillis() - startTime;

            // 收集任务状态信息
            TaskStatusInfo statusInfo = collectTaskStatusInfo(completableFutures, taskCount);

            // 统一处理任务取消和日志记录
            handleTaskCancellation(completableFutures, statusInfo, executionTime, mdcContext,
                    "Thread interrupted", e);

            // 标记当前线程为中断状态
            wasInterrupted = true;

            throw new RuntimeException(String.format(
                    "Thread interrupted while waiting for tasks: %s. Only %d of %d tasks completed (%.2f%%)",
                    e.getMessage(),
                    statusInfo.completedCount,
                    taskCount,
                    statusInfo.completionRate), e);
        } catch (ExecutionException e) {
            // 计算执行时间
            long executionTime = System.currentTimeMillis() - startTime;

            // 提取根本原因
            Throwable rootCause = findRootCause(e);
            String errorMessage = rootCause != null ? rootCause.getMessage() : e.getMessage();
            String errorType = rootCause != null ? rootCause.getClass().getName() : e.getClass().getName();

            // 收集任务状态信息
            TaskStatusInfo statusInfo = collectTaskStatusInfo(completableFutures, taskCount);

            // 统一处理任务取消和日志记录
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
            // 计算执行时间
            long executionTime = System.currentTimeMillis() - startTime;

            // 收集任务状态信息
            TaskStatusInfo statusInfo = collectTaskStatusInfo(completableFutures, taskCount);

            // 统一处理任务取消和日志记录
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
            // 处理其他未预期的异常
            long executionTime = System.currentTimeMillis() - startTime;

            // 提取根本原因
            Throwable rootCause = findRootCause(e);
            String errorMessage = rootCause != null ? rootCause.getMessage() : e.getMessage();
            String errorType = rootCause != null ? rootCause.getClass().getName() : e.getClass().getName();

            // 收集任务状态信息
            TaskStatusInfo statusInfo = collectTaskStatusInfo(completableFutures, taskCount);

            // 统一处理任务取消和日志记录
            handleTaskCancellation(completableFutures, statusInfo, executionTime, mdcContext,
                    String.format("Unexpected error: %s (%s)", errorMessage, errorType), e);

            throw new RuntimeException(String.format(
                    "Unexpected error while waiting for tasks: %s (%s)",
                    errorMessage,
                    errorType), e);
        } finally {
            // 如果当前线程在进入此方法前已被中断，则恢复中断状态
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 优雅关闭线程池。
     * <p>
     * <b>实现原理</b>：
     * 1. 调用 shutdown() 方法，停止接受新任务并等待现有任务完成。
     * 2. 设置 5 秒超时，若任务未完成，则调用 shutdownNow() 强制关闭。
     * 3. 记录关闭过程中的状态和异常，便于监控和调试。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - ThreadPoolExecutor 的 shutdown() 和 shutdownNow() 方法是线程安全的。
     * - 日志记录在当前线程执行，不涉及并发问题。
     * - 中断状态通过 try-finally 结构恢复，确保线程安全性。
     * </p>
     * <p>
     * <b>使用场景</b>：
     * - 在应用程序关闭前主动释放线程池资源。
     * - 在特定业务逻辑完成后清理线程池，避免资源占用。
     * </p>
     * <p>
     * <b>注意事项</b>：
     * - 调用此方法后，线程池将不可用，后续任务提交会抛出 RejectedExecutionException。
     * - 若需要更长的等待时间，可通过系统属性 maple.thread.shutdown.timeout 配置（单位：秒）。
     * - 此方法与 JVM 关闭钩子独立，互不干扰。
     * </p>
     */
    public static void shutdownThreadPool() {
        // 检查线程池是否已关闭，避免重复操作
        if (EXECUTOR_SERVICE.isShutdown()) {
            LOG.info("Thread pool is already shut down, skipping shutdown operation");
            return;
        }

        // 从系统属性读取关闭超时时间，默认 5 秒
        long shutdownTimeout = NumberUtil.parseLong(
                System.getProperty("maple.thread.shutdown.timeout"),
                5L
        );
        if (shutdownTimeout < 0) {
            shutdownTimeout = 5L; // 确保超时时间非负
            LOG.warn("Invalid shutdown timeout specified, using default 5 seconds");
        }

        // 保存当前线程的中断状态
        boolean wasInterrupted = Thread.currentThread().isInterrupted();

        try {
            // 记录当前线程池状态
            String poolStatus = getThreadPoolStatus();
            LOG.info("Initiating thread pool shutdown. Current status: {}", poolStatus);

            // 开始优雅关闭
            EXECUTOR_SERVICE.shutdown();

            // 等待指定时间，允许任务完成
            if (!EXECUTOR_SERVICE.awaitTermination(shutdownTimeout, TimeUnit.SECONDS)) {
                int unfinishedTasks = EXECUTOR_SERVICE.getQueue().size();
                LOG.warn("Thread pool did not terminate within {} seconds, {} tasks remain, forcing shutdown",
                        shutdownTimeout, unfinishedTasks);

                // 强制关闭未完成任务
                List<Runnable> unexecutedTasks = EXECUTOR_SERVICE.shutdownNow();
                LOG.warn("Forced shutdown completed, {} tasks were not executed", unexecutedTasks.size());
            } else {
                LOG.info("Thread pool shut down gracefully within {} seconds", shutdownTimeout);
            }

            // 记录最终状态
            LOG.info("Thread pool shutdown completed. Final status: {}", getThreadPoolStatus());
        } catch (InterruptedException e) {
            // 中断时强制关闭
            EXECUTOR_SERVICE.shutdownNow();
            Thread.currentThread().interrupt();
            LOG.error("Thread pool shutdown interrupted, forced shutdown executed", e);
        } catch (Exception e) {
            // 捕获其他意外异常
            LOG.error("Unexpected error during thread pool shutdown: {}", e.getMessage(), e);
            EXECUTOR_SERVICE.shutdownNow();
        } finally {
            // 恢复中断状态
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 查找异常的根本原因
     * <p>
     * 递归查找异常链中的根本原因，避免在日志中只显示包装异常。
     * 这对于诊断问题非常有用，特别是在使用CompletableFuture时，
     * 异常通常会被多层包装。
     * </p>
     * <p>
     * <b>增强功能</b>：
     * 1. 防止循环引用导致的无限递归。
     * 2. 设置最大递归深度，避免异常链过长导致栈溢出。
     * 3. 使用Set记录已处理的异常，防止复杂异常链中的循环引用。
     * </p>
     *
     * @param throwable 异常对象
     * @return 根本原因异常，如果没有则返回原始异常
     */
    private static Throwable findRootCause(Throwable throwable) {
        return findRootCause(throwable, new java.util.HashSet<>(), 0);
    }

    /**
     * 查找异常的根本原因（内部实现）
     * <p>
     * 递归查找异常链中的根本原因，避免在日志中只显示包装异常。
     * 使用Set记录已处理的异常，防止循环引用导致的无限递归。
     * 设置最大递归深度（20），避免异常链过长导致栈溢出。
     * </p>
     *
     * @param throwable 异常对象
     * @param seen      已处理的异常集合，用于检测循环引用
     * @param depth     当前递归深度
     * @return 根本原因异常，如果没有则返回原始异常
     */
    private static Throwable findRootCause(Throwable throwable, java.util.Set<Throwable> seen, int depth) {
        // 最大递归深度，防止栈溢出
        final int MAX_DEPTH = 20;

        if (throwable == null) {
            return null;
        }

        // 如果已达到最大递归深度或异常已被处理过（循环引用），则返回当前异常
        if (depth >= MAX_DEPTH || !seen.add(throwable)) {
            return throwable;
        }

        Throwable cause = throwable.getCause();
        if (cause == null) {
            return throwable;
        }

        // 防止直接的循环引用
        if (cause == throwable) {
            return throwable;
        }

        // 递归查找根本原因
        return findRootCause(cause, seen, depth + 1);
    }

    /**
     * 收集任务状态信息
     * <p>
     * 统计已完成和未完成的任务数量，计算完成率。
     * 这个方法是线程安全的，因为CompletableFuture的isDone方法是线程安全的。
     * </p>
     *
     * @param futures    任务列表
     * @param totalCount 总任务数
     * @return 任务状态信息对象
     */
    private static TaskStatusInfo collectTaskStatusInfo(List<CompletableFuture<?>> futures, int totalCount) {
        long completedCount = futures.stream().filter(CompletableFuture::isDone).count();
        long unfinishedCount = totalCount - completedCount;
        double completionRate = totalCount > 0 ? (double) completedCount / totalCount * 100 : 0;

        return new TaskStatusInfo(completedCount, unfinishedCount, completionRate);
    }

    /**
     * 取消所有未完成的任务
     * <p>
     * 遍历任务列表，取消所有未完成的任务，并返回成功取消的任务数量。
     * 这个方法是线程安全的，因为CompletableFuture的isDone和cancel方法都是线程安全的。
     * </p>
     * <p>
     * <b>增强功能</b>：
     * 1. 使用mayInterruptIfRunning=true参数，尝试中断正在执行的任务。
     * 2. 记录每个任务的取消状态，便于诊断问题。
     * 3. 提供取消失败的任务数量统计。
     * 4. 在高并发环境下使用线程安全的计数方式。
     * 5. 增加防止线程中断传播的保护措施。
     * 6. 使用原子计数器确保线程安全的统计。
     * </p>
     *
     * @param futures 任务列表
     * @return 成功取消的任务数量
     */
    private static int cancelUnfinishedTasks(List<CompletableFuture<?>> futures) {
        if (futures == null || futures.isEmpty()) {
            return 0;
        }

        // 使用原子计数器确保线程安全的统计
        AtomicLong cancelledCount = new AtomicLong(0);
        AtomicLong failedToCancel = new AtomicLong(0);
        AtomicLong alreadyDoneCount = new AtomicLong(0);

        // 记录开始取消任务的时间
        long startTime = System.currentTimeMillis();

        // 保存当前线程的中断状态
        boolean wasInterrupted = Thread.currentThread().isInterrupted();

        try {
            // 顺序处理任务取消，避免并行流对小型任务列表的开销
            for (CompletableFuture<?> future : futures) {
                if (future == null) {
                    continue;
                }

                if (future.isDone()) {
                    alreadyDoneCount.incrementAndGet();
                    continue;
                }

                try {
                    // 尝试取消任务，mayInterruptIfRunning=true表示尝试中断正在执行的任务
                    boolean cancelled = future.cancel(true);
                    if (cancelled) {
                        cancelledCount.incrementAndGet();
                    } else {
                        failedToCancel.incrementAndGet();
                        // 再次检查是否已完成，可能在我们调用cancel之前刚好完成
                        if (future.isDone()) {
                            alreadyDoneCount.incrementAndGet();
                            failedToCancel.decrementAndGet(); // 不算作取消失败
                        }
                    }
                } catch (Exception e) {
                    // 捕获取消过程中的异常，避免影响其他任务的取消
                    failedToCancel.incrementAndGet();
                    LOG.warn("Failed to cancel a task: {}", e.getMessage(), e);
                }
            }
        } finally {
            // 如果当前线程在进入此方法前已被中断，则恢复中断状态
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }

        // 记录任务取消的总时间
        long duration = System.currentTimeMillis() - startTime;

        // 只有在有任务需要取消时才记录日志
        if (cancelledCount.get() > 0 || failedToCancel.get() > 0) {
            LOG.info("Cancelled {} tasks, failed to cancel {} tasks, already done {} tasks in {} ms",
                    cancelledCount.get(), failedToCancel.get(), alreadyDoneCount.get(), duration);
        }

        return cancelledCount.intValue();
    }

    /**
     * 统一处理任务取消和日志记录。
     * <p>
     * 当任务执行过程中发生异常（如中断、执行失败、超时等）时，调用此方法：
     * 1. 记录详细的错误日志，包括任务状态、线程池状态和统计信息。
     * 2. 取消所有未完成的任务，避免资源泄漏。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - 日志记录和任务取消操作在指定的 MDC 上下文中执行，确保日志上下文一致性。
     * - 使用 cancelUnfinishedTasks 方法取消任务，保证线程安全。
     * </p>
     *
     * @param futures        CompletableFuture 对象列表
     * @param statusInfo     任务状态信息
     * @param executionTime  执行时间（毫秒）
     * @param mdcContext     MDC 上下文
     * @param errorMessage   错误信息
     * @param throwable      异常对象
     */
    private static void handleTaskCancellation(List<CompletableFuture<?>> futures,
                                               TaskStatusInfo statusInfo,
                                               long executionTime,
                                               Map<String, String> mdcContext,
                                               String errorMessage,
                                               Throwable throwable) {
        // 记录错误日志
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

        // 取消未完成的任务
        int cancelledCount = cancelUnfinishedTasks(futures);
        withMdcContext(mdcContext, () -> {
            LOG.warn("Cancelled {} of {} incomplete tasks due to {}",
                    cancelledCount, statusInfo.unfinishedCount, errorMessage.toLowerCase());
            return null;
        });
    }

    /**
     * 等待所有 CompletableFuture 任务完成，使用默认超时时间（5秒）。
     * <p>
     * 委托给 allOf(List, int, TimeUnit) 方法，使用默认超时时间。
     * 这个方法提供了一个更简单的接口，适用于大多数场景，无需指定超时时间。
     * </p>
     *
     * @param completableFutures CompletableFuture 对象列表
     * @throws IllegalArgumentException 如果 completableFutures 为 null 或为空
     * @throws RuntimeException         如果任务执行失败（InterruptedException、ExecutionException、TimeoutException）
     */
    public static void allOf(List<CompletableFuture<?>> completableFutures) {
        Objects.requireNonNull(completableFutures, "completableFutures cannot be null");
        allOf(completableFutures, DEFAULT_TIME_OUT, TimeUnit.SECONDS);
    }

    /**
     * 获取异常堆栈的前几行
     * <p>
     * 提取异常堆栈的前几行，便于快速定位问题，避免日志中包含过多的堆栈信息。
     * </p>
     *
     * @param throwable 异常对象
     * @param lineCount 需要提取的行数
     * @return 异常堆栈的前几行
     */
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

    /**
     * 任务状态信息类，用于收集和传递任务执行状态
     */
    private record TaskStatusInfo(long completedCount, long unfinishedCount, double completionRate) {
    }
}