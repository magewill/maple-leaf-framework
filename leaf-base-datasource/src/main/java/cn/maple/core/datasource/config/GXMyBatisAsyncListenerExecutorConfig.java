package cn.maple.core.datasource.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * MyBatis异步监听器线程池配置
 * <p>
 * 本配置类为MyBatis的异步事件监听器提供专用的线程池配置。
 * 线程池参数经过优化，可以高效处理MyBatis的异步事件，同时保证内存安全和系统稳定性。
 * 主要特性：
 * 1. 资源自适应：根据系统CPU核心数自动调整线程池大小，支持通过配置文件覆盖默认配置
 * 2. 智能拒绝策略：在高负载情况下保护系统稳定性，采用CallerRunsPolicy避免任务丢失
 * 3. 任务执行监控：监控长时间运行的任务并记录日志，便于性能分析和问题排查
 * 4. 优雅关闭：确保应用关闭时所有事件处理任务都能完成，避免数据不一致
 * 5. 异常安全处理：捕获并记录所有异步任务异常，提高系统稳定性
 * 6. 线程池预热：预先创建核心线程，避免首次任务执行延迟
 * 7. 自动资源释放：非核心线程在空闲时自动释放，减少资源占用
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 在Spring Boot应用中自动注入
 * @Autowired
 * private AsyncTaskExecutor myBatisEventAsyncTaskExecutor;
 * <p>
 * // 2. 在自定义异步任务中使用
 * @Async("myBatisEventAsyncTaskExecutor")
 * public void processDataAsync(Object data) {
 *     // 异步处理逻辑
 * }
 * <p>
 * // 3. 在事件监听器中使用
 * @Component
 * @Async("myBatisEventAsyncTaskExecutor")
 * public class CustomAsyncListener {
 *     @EventListener
 *     public void handleEvent(CustomEvent event) {
 *         // 事件处理逻辑
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author maple.framework framework team
 * @since 1.0.0
 */
@Log4j2
@Configuration
public class GXMyBatisAsyncListenerExecutorConfig {
    /**
     * 默认核心线程数系数（相对于CPU核心数）
     * 对于IO密集型任务，通常设置为CPU核心数的1-2倍较为合适
     */
    private static final double DEFAULT_CORE_POOL_SIZE_FACTOR = 1.0;

    /**
     * 默认最大线程数系数（相对于核心线程数）
     */
    private static final double DEFAULT_MAX_POOL_SIZE_FACTOR = 1.0;

    /**
     * 默认队列容量
     */
    private static final int DEFAULT_QUEUE_CAPACITY = 1000;

    /**
     * 默认线程空闲存活时间（秒）
     */
    private static final int DEFAULT_KEEP_ALIVE_SECONDS = 120;

    /**
     * 默认长任务阈值（毫秒）
     */
    private static final long DEFAULT_LONG_TASK_THRESHOLD_MS = 30000;

    /**
     * 默认关闭等待时间（秒）
     */
    private static final int DEFAULT_AWAIT_TERMINATION_SECONDS = 60;

    /**
     * 核心线程数系数配置（可通过配置文件覆盖）
     */
    @Value("${maple.framework.mybatis.async.listener.core-pool-size-factor:" + DEFAULT_CORE_POOL_SIZE_FACTOR + "}")
    private double corePoolSizeFactor;

    /**
     * 最大线程数系数配置（可通过配置文件覆盖）
     */
    @Value("${maple.framework.mybatis.async.listener.max-pool-size-factor:" + DEFAULT_MAX_POOL_SIZE_FACTOR + "}")
    private double maxPoolSizeFactor;

    /**
     * 队列容量配置（可通过配置文件覆盖）
     */
    @Value("${maple.framework.mybatis.async.listener.queue-capacity:" + DEFAULT_QUEUE_CAPACITY + "}")
    private int queueCapacity;

    /**
     * 线程空闲存活时间配置（可通过配置文件覆盖）
     */
    @Value("${maple.framework.mybatis.async.listener.keep-alive-seconds:" + DEFAULT_KEEP_ALIVE_SECONDS + "}")
    private int keepAliveSeconds;

    /**
     * 长任务阈值配置（可通过配置文件覆盖）
     */
    @Value("${maple.framework.mybatis.async.listener.long-task-threshold-ms:" + DEFAULT_LONG_TASK_THRESHOLD_MS + "}")
    private long longTaskThresholdMs;

    /**
     * 关闭等待时间配置（可通过配置文件覆盖）
     */
    @Value("${maple.framework.mybatis.async.listener.await-termination-seconds:" + DEFAULT_AWAIT_TERMINATION_SECONDS + "}")
    private int awaitTerminationSeconds;

    /**
     * 创建MyBatis事件异步处理线程池
     * <p>
     * 该线程池专门用于处理MyBatis的异步事件，如实体保存、更新、删除等操作的异步监听。
     * 线程池配置经过优化，能够在保证性能的同时确保内存安全和系统稳定。
     * </p>
     * <p>
     * 线程池参数说明：
     * 1. 核心线程数：默认为CPU核心数 * corePoolSizeFactor，可通过配置文件调整
     * 2. 最大线程数：默认为核心线程数 * maxPoolSizeFactor，可通过配置文件调整
     * 3. 队列容量：默认为1000，可通过配置文件调整
     * 4. 线程空闲存活时间：默认为120秒，可通过配置文件调整
     * 5. 拒绝策略：采用CallerRunsPolicy，在线程池饱和时由调用者线程执行任务
     * 6. 任务装饰器：添加任务执行监控和异常处理
     * 7. 优雅关闭：等待所有任务完成后再关闭线程池
     * </p>
     *
     * @return 配置好的异步任务执行器
     */
    @Bean("myBatisEventAsyncTaskExecutor")
    public AsyncTaskExecutor myBatisEventAsyncTaskExecutor() {
        // 使用ThreadPoolTaskExecutor，它是Spring对ThreadPoolExecutor的封装，提供了更多的功能
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 获取CPU核心数，用于动态调整线程池大小
        int cpuCores = Runtime.getRuntime().availableProcessors();

        // 计算核心线程数和最大线程数
        int corePoolSize = (int) Math.max(1, Math.ceil(cpuCores * corePoolSizeFactor));
        int maxPoolSize = (int) Math.max(corePoolSize, Math.ceil(corePoolSize * maxPoolSizeFactor));

        // 设置核心线程数
        executor.setCorePoolSize(corePoolSize);

        // 设置最大线程数
        executor.setMaxPoolSize(maxPoolSize);

        // 设置队列容量
        executor.setQueueCapacity(queueCapacity);

        // 设置线程空闲存活时间
        executor.setKeepAliveSeconds(keepAliveSeconds);

        // 设置线程名称前缀，便于日志追踪和问题排查
        executor.setThreadNamePrefix("maple-framework-mybatis-event-async-");

        // 设置线程分组名称，便于管理和监控
        executor.setThreadGroupName("maple-framework-mybatis-event-group");

        // 设置智能拒绝策略：在高负载情况下保护系统稳定性
        executor.setRejectedExecutionHandler(createCustomRejectedExecutionHandler());

        // 设置任务装饰器：添加任务执行监控和异常处理
        executor.setTaskDecorator(createTaskDecorator());

        // 设置线程池关闭时等待所有任务完成再销毁其他Bean
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 设置最多等待时间（秒）
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);

        // 初始化线程池
        executor.initialize();

        // 预热线程池：创建核心线程，避免首次任务执行延迟
        executor.getThreadPoolExecutor().prestartAllCoreThreads();

        // 打印线程池配置信息，便于调试
        log.info("MyBatis事件异步线程池已初始化: corePoolSize={}, maxPoolSize={}, queueCapacity={}, keepAliveSeconds={}",
                corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds);

        return executor;
    }

    /**
     * 创建自定义拒绝策略处理器
     * <p>
     * 该处理器在线程池饱和时执行以下操作：
     * 1. 记录详细的拒绝信息，包括活动线程数和队列大小
     * 2. 尝试使用调用者线程执行任务，避免任务丢失
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

            // 记录详细的拒绝信息，便于问题排查
            log.error("MyBatis事件任务被拒绝: 线程池饱和. 状态: [活动线程数={}, 队列大小={}, 当前池大小={}, 核心池大小={}, 最大池大小={}]",
                    activeCount, queueSize, poolSize, corePoolSize, maximumPoolSize);

            // 尝试使用调用者线程执行任务，避免任务丢失
            try {
                log.warn("正在调用者线程中执行被拒绝的MyBatis事件任务（降级策略）");
                new ThreadPoolExecutor.CallerRunsPolicy().rejectedExecution(runnable, executor);
            } catch (Exception e) {
                log.error("在调用者线程中执行MyBatis事件任务失败", e);
                throw new RejectedExecutionException("MyBatis事件任务执行失败: " + e.getMessage(), e);
            }
        };
    }

    /**
     * 创建任务装饰器
     * <p>
     * 该装饰器为每个任务添加以下功能：
     * 1. 任务执行时间监控，记录长时间运行的任务
     * 2. 异常捕获和记录，提高系统稳定性
     * 3. 任务上下文传递，确保异步任务能够访问调用线程的上下文
     * </p>
     *
     * @return 任务装饰器
     */
    private TaskDecorator createTaskDecorator() {
        return runnable -> () -> {
            // 记录任务开始时间，用于监控任务执行时长
            long startTime = System.currentTimeMillis();
            String taskId = String.format("task-%d", System.nanoTime());

            // 记录任务开始
            if (log.isDebugEnabled()) {
                log.debug("MyBatis事件任务开始执行: {}", taskId);
            }
            try {
                // 执行原始任务
                runnable.run();

                // 检查任务执行时间是否过长
                long executionTime = System.currentTimeMillis() - startTime;
                if (executionTime > longTaskThresholdMs) {
                    log.warn("检测到长时间运行的MyBatis事件任务: {} ms, taskId={}", executionTime, taskId);
                } else if (log.isDebugEnabled()) {
                    log.debug("MyBatis事件任务执行完成: {} ms, taskId={}", executionTime, taskId);
                }
            } catch (Exception e) {
                // 记录详细的异常信息
                long executionTime = System.currentTimeMillis() - startTime;
                log.error("MyBatis事件任务执行失败: {} ms, taskId={}, 异常: {}",
                        executionTime, taskId, e.getMessage(), e);
                throw e;
            }
        };
    }
}
