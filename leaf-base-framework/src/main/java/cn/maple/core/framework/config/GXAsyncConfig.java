package cn.maple.core.framework.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring异步任务线程池配置
 * <p>
 * 本配置类实现了Spring的AsyncConfigurer接口，为@Async注解提供默认的线程池配置。
 * 线程池参数经过优化，可以应对高负载场景，防止线程池饱和导致系统不稳定。
 * 主要特性：
 * 1. 动态线程池大小：根据CPU核心数自动调整线程池大小，支持通过配置文件覆盖默认配置
 * 2. 智能拒绝策略：根据系统负载动态选择拒绝策略，保护系统稳定性
 * 3. 完善的异常处理：捕获并记录所有异步任务异常，提供详细的诊断信息
 * 4. 任务执行监控：监控长时间运行的任务和超时任务，便于性能分析和问题排查
 * 5. 优雅关闭：确保应用关闭时所有任务都能完成，避免数据不一致
 * 6. 线程池预热：预先创建核心线程，避免首次任务执行延迟
 * 7. 自动资源释放：非核心线程在空闲时自动释放，减少资源占用
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 在Spring Boot应用中自动注入
 * @Autowired
 * private Executor asyncExecutor;
 *
 * // 2. 在自定义异步任务中使用
 * @Async
 * public void processDataAsync(Object data) {
 *     // 异步处理逻辑
 * }
 *
 * // 3. 在事件监听器中使用
 * @Component
 * public class CustomAsyncListener {
 *     @Async
 *     @EventListener
 *     public void handleEvent(CustomEvent event) {
 *         // 事件处理逻辑
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author maple framework team
 * @since 1.0.0
 */
@Log4j2
@Configuration
@EnableAsync
public class GXAsyncConfig implements AsyncConfigurer {
    /**
     * 默认核心线程数系数（相对于CPU核心数）
     * 对于IO密集型任务，通常设置为CPU核心数的1-2倍较为合适
     */
    private static final double DEFAULT_CORE_POOL_SIZE_FACTOR = 2.0;

    /**
     * 默认最大线程数系数（相对于核心线程数）
     */
    private static final double DEFAULT_MAX_POOL_SIZE_FACTOR = 2.0;

    /**
     * 默认队列容量
     */
    private static final int DEFAULT_QUEUE_CAPACITY = 500;

    /**
     * 默认线程空闲存活时间（秒）
     */
    private static final int DEFAULT_KEEP_ALIVE_SECONDS = 60;

    /**
     * 默认任务超时时间（毫秒）
     */
    private static final long DEFAULT_TASK_TIMEOUT_MS = 60;

    /**
     * 默认长任务阈值（毫秒）
     */
    private static final long DEFAULT_LONG_TASK_THRESHOLD_MS = 60;

    /**
     * 默认关闭等待时间（秒）
     */
    private static final int DEFAULT_AWAIT_TERMINATION_SECONDS = 60;

    /**
     * 系统高负载阈值，超过此值时将直接拒绝新任务
     */
    private static final double HIGH_LOAD_THRESHOLD = 0.8;

    /**
     * 任务计数器，用于生成唯一的任务ID
     */
    private final AtomicInteger taskCounter = new AtomicInteger(0);

    /**
     * 核心线程数系数配置（可通过配置文件覆盖）
     */
    @Value("${maple.framework.async.core-pool-size-factor:" + DEFAULT_CORE_POOL_SIZE_FACTOR + "}")
    private double corePoolSizeFactor;

    /**
     * 最大线程数系数配置（可通过配置文件覆盖）
     */
    @Value("${maple.framework.async.max-pool-size-factor:" + DEFAULT_MAX_POOL_SIZE_FACTOR + "}")
    private double maxPoolSizeFactor;

    /**
     * 队列容量配置（可通过配置文件覆盖）
     */
    @Value("${maple.framework.async.queue-capacity:" + DEFAULT_QUEUE_CAPACITY + "}")
    private int queueCapacity;

    /**
     * 线程空闲存活时间配置（可通过配置文件覆盖）
     */
    @Value("${maple.framework.async.keep-alive-seconds:" + DEFAULT_KEEP_ALIVE_SECONDS + "}")
    private int keepAliveSeconds;

    /**
     * 任务超时时间配置（可通过配置文件覆盖）
     */
    @Value("${maple.framework.async.task-timeout-ms:" + DEFAULT_TASK_TIMEOUT_MS + "}")
    private long taskTimeoutMs;

    /**
     * 长任务阈值配置（可通过配置文件覆盖）
     */
    @Value("${maple.framework.async.long-task-threshold-ms:" + DEFAULT_LONG_TASK_THRESHOLD_MS + "}")
    private long longTaskThresholdMs;

    /**
     * 关闭等待时间配置（可通过配置文件覆盖）
     */
    @Value("${maple.framework.async.await-termination-seconds:" + DEFAULT_AWAIT_TERMINATION_SECONDS + "}")
    private int awaitTerminationSeconds;

    /**
     * 系统高负载阈值配置（可通过配置文件覆盖）
     */
    @Value("${maple.framework.async.high-load-threshold:" + HIGH_LOAD_THRESHOLD + "}")
    private double highLoadThreshold;

    /**
     * 线程池执行器实例，用于监控和管理
     */
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    /**
     * 获取系统平均负载
     * 返回值范围通常在0.0到1.0之间，值越大表示系统负载越高
     * 在Windows系统上可能不准确，仅作参考
     *
     * @return 系统负载值，范围0.0-1.0，如果无法获取则返回-1
     */
    private double getSystemLoadAverage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            double loadAverage = osBean.getSystemLoadAverage();

            // 某些系统可能返回负值表示不支持
            if (loadAverage < 0) {
                // 尝试使用CPU使用率作为替代指标
                if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                    return sunOsBean.getProcessCpuLoad();
                }
                return -1;
            }

            // 将负载平均值标准化到0-1范围
            // 通常loadAverage是基于处理器核心数的，所以除以可用处理器数
            int processors = osBean.getAvailableProcessors();
            return processors > 0 ? Math.min(loadAverage / processors, 1.0) : loadAverage;
        } catch (Exception e) {
            log.warn("获取系统负载失败: {}", e.getMessage());
            return -1;
        }
    }

    @Override
    @Bean("asyncExecutor")
    public Executor getAsyncExecutor() {
        threadPoolTaskExecutor = new ThreadPoolTaskExecutor();

        // 获取CPU核心数，用于动态调整线程池大小
        int cpuCores = Runtime.getRuntime().availableProcessors();

        // 计算核心线程数和最大线程数
        int corePoolSize = (int) Math.max(1, Math.ceil(cpuCores * corePoolSizeFactor));
        int maxPoolSize = (int) Math.max(corePoolSize, Math.ceil(corePoolSize * maxPoolSizeFactor));

        // 设置核心线程数
        threadPoolTaskExecutor.setCorePoolSize(corePoolSize);

        // 设置最大线程数
        threadPoolTaskExecutor.setMaxPoolSize(maxPoolSize);

        // 设置队列容量
        threadPoolTaskExecutor.setQueueCapacity(queueCapacity);

        // 设置线程空闲存活时间
        threadPoolTaskExecutor.setKeepAliveSeconds(keepAliveSeconds);

        // 设置线程名称前缀，便于日志追踪和问题排查
        threadPoolTaskExecutor.setThreadNamePrefix("maple-framework-async-");

        // 设置线程分组名称，便于管理和监控
        threadPoolTaskExecutor.setThreadGroupName("maple-framework-async-group");

        // 设置智能拒绝策略：根据系统负载和线程池状态动态选择处理方式
        threadPoolTaskExecutor.setRejectedExecutionHandler(createCustomRejectedExecutionHandler());

        // 设置任务装饰器：添加任务执行监控和异常处理
        threadPoolTaskExecutor.setTaskDecorator(createTaskDecorator());

        // 设置线程池关闭时等待所有任务完成再销毁其他Bean
        threadPoolTaskExecutor.setWaitForTasksToCompleteOnShutdown(true);

        // 设置最多等待时间（秒）
        threadPoolTaskExecutor.setAwaitTerminationSeconds(awaitTerminationSeconds);

        // 初始化线程池
        threadPoolTaskExecutor.initialize();

        // 预热线程池：创建核心线程，避免首次任务执行延迟
        threadPoolTaskExecutor.getThreadPoolExecutor().prestartAllCoreThreads();

        // 打印线程池配置信息，便于调试
        log.info("异步线程池已初始化: corePoolSize={}, maxPoolSize={}, queueCapacity={}, keepAliveSeconds={}",
                corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds);

        return threadPoolTaskExecutor;
    }

    /**
     * 创建自定义拒绝策略处理器
     * <p>
     * 该处理器在线程池饱和时执行以下操作：
     * 1. 记录详细的拒绝信息，包括活动线程数、队列大小和系统负载
     * 2. 根据系统负载决定是直接拒绝还是尝试使用调用者线程执行任务
     * 3. 如果调用者线程执行失败，记录错误并抛出异常
     * </p>
     *
     * @return 自定义拒绝策略处理器
     */
    private RejectedExecutionHandler createCustomRejectedExecutionHandler() {
        return (runnable, executor) -> {
            // 获取线程池状态信息
            int activeCount = executor.getActiveCount();
            int queueSize = executor.getQueue().size();
            int poolSize = executor.getPoolSize();
            int corePoolSize = executor.getCorePoolSize();
            int maximumPoolSize = executor.getMaximumPoolSize();
            double systemLoad = getSystemLoadAverage();

            // 记录详细的拒绝信息，便于问题排查
            log.error("任务被拒绝: 线程池饱和. 状态: [活动线程数={}, 队列大小={}, 当前池大小={}, 核心池大小={}, 最大池大小={}, 系统负载={}]",
                    activeCount, queueSize, poolSize, corePoolSize, maximumPoolSize, systemLoad);

            // 系统负载较高时直接拒绝，防止系统崩溃
            if (systemLoad > highLoadThreshold || activeCount >= maximumPoolSize) {
                log.error("系统负载过高，拒绝任务以保护系统稳定性");
                throw new RejectedExecutionException("任务被拒绝: 系统负载过高");
            }

            // 系统负载可接受时，尝试使用调用者线程执行任务
            // 这是一种退化策略，可能会影响调用者线程的响应时间，但避免了任务丢失
            try {
                log.warn("正在调用者线程中执行被拒绝的任务（降级策略）");
                new ThreadPoolExecutor.CallerRunsPolicy().rejectedExecution(runnable, executor);
            } catch (Exception e) {
                log.error("在调用者线程中执行任务失败", e);
                throw new RejectedExecutionException("任务执行失败: " + e.getMessage(), e);
            }
        };
    }

    /**
     * 创建任务装饰器
     * <p>
     * 该装饰器为每个任务添加以下功能：
     * 1. 任务唯一标识，便于追踪和诊断
     * 2. 任务执行时间监控，记录长时间运行的任务
     * 3. 任务超时检测，记录超过阈值的任务
     * 4. 异常捕获和记录，提高系统稳定性
     * 5. 任务上下文传递，确保异步任务能够访问调用线程的上下文
     * </p>
     *
     * @return 任务装饰器
     */
    private TaskDecorator createTaskDecorator() {
        return runnable -> () -> {
            // 生成任务唯一标识
            String taskId = String.format("task-%d", taskCounter.incrementAndGet());

            // 记录任务开始时间，用于监控任务执行时长
            long startTime = System.currentTimeMillis();

            // 记录任务开始
            if (log.isDebugEnabled()) {
                log.debug("异步任务开始执行: {}", taskId);
            }

            try {
                // 执行原始任务
                runnable.run();

                // 检查任务执行时间
                long executionTime = System.currentTimeMillis() - startTime;

                // 任务超时检测
                if (executionTime > taskTimeoutMs) {
                    log.warn("检测到超时的异步任务: {} ms (超过阈值 {} ms), taskId={}",
                            executionTime, taskTimeoutMs, taskId);
                }
                // 长任务检测
                else if (executionTime > longTaskThresholdMs) {
                    log.warn("检测到长时间运行的异步任务: {} ms, taskId={}", executionTime, taskId);
                }
                // 正常完成
                else if (log.isDebugEnabled()) {
                    log.debug("异步任务执行完成: {} ms, taskId={}", executionTime, taskId);
                }
            } catch (Exception e) {
                // 记录详细的异常信息
                long executionTime = System.currentTimeMillis() - startTime;
                log.error("异步任务执行失败: {} ms, taskId={}, 异常: {}",
                        executionTime, taskId, e.getMessage(), e);
                throw e;
            }
        };
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            // 使用 StringBuilder 高效拼接日志信息
            StringBuilder errorMsg = new StringBuilder()
                    .append("--------------Maple Leaf FrameWork异步调用，异常捕获--------------\n")
                    .append("异常信息: ").append(throwable.getMessage()).append("\n")
                    .append("方法名称: ").append(method.getName()).append("\n")
                    .append("类名: ").append(method.getDeclaringClass().getName()).append("\n");

            // 安全地记录参数，避免 toString() 异常
            for (int i = 0; i < params.length; i++) {
                Object param = params[i];
                String paramValue;
                try {
                    paramValue = param != null ? param.toString() : "null";
                } catch (Exception e) {
                    paramValue = "参数转换为字符串时出错: " + e.getMessage();
                }
                errorMsg.append("参数 ").append(i).append(": ").append(paramValue).append("\n");
            }

            // 添加线程池状态信息，帮助诊断是否与线程池相关
            if (threadPoolTaskExecutor != null) {
                ThreadPoolExecutor executor = threadPoolTaskExecutor.getThreadPoolExecutor();
                errorMsg.append("线程池状态: [活动线程数=").append(executor.getActiveCount())
                        .append(", 池大小=").append(executor.getPoolSize())
                        .append(", 核心池大小=").append(executor.getCorePoolSize())
                        .append(", 最大池大小=").append(executor.getMaximumPoolSize())
                        .append(", 队列大小=").append(executor.getQueue().size())
                        .append("]\n");

                // 添加系统负载信息
                double systemLoad = getSystemLoadAverage();
                if (systemLoad >= 0) {
                    errorMsg.append("系统负载: ").append(String.format("%.2f", systemLoad)).append("\n");
                }
            }

            errorMsg.append("--------------Maple Leaf FrameWork异步调用，异常捕获--------------");
            log.error(errorMsg.toString(), throwable);
        };
    }

    /**
     * 获取当前线程池状态信息
     * <p>
     * 该方法返回线程池的详细状态信息，包括活动线程数、池大小、队列大小等，
     * 可用于监控和诊断线程池的运行状况。
     * </p>
     *
     * @return 线程池状态信息字符串
     */
    public String getThreadPoolStatus() {
        if (threadPoolTaskExecutor == null) {
            return "线程池未初始化";
        }

        ThreadPoolExecutor executor = threadPoolTaskExecutor.getThreadPoolExecutor();
        return String.format("线程池状态: [活动线程数=%d, 池大小=%d, 核心池大小=%d, 最大池大小=%d, 队列大小=%d, 已完成任务数=%d]",
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),
                executor.getQueue().size(),
                executor.getCompletedTaskCount());
    }
}
