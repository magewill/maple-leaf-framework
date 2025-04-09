package cn.maple.core.framework.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Spring异步任务线程池配置
 * <p>
 * 本配置类实现了Spring的AsyncConfigurer接口，为@Async注解提供默认的线程池配置。
 * 线程池参数经过优化，可以应对高负载场景，防止线程池饱和导致系统不稳定。
 * 主要特性：
 * 1. 动态线程池大小：根据CPU核心数自动调整线程池大小
 * 2. 智能拒绝策略：根据系统负载动态选择拒绝策略
 * 3. 完善的异常处理：捕获并记录所有异步任务异常
 * 4. 任务执行监控：监控长时间运行的任务和超时任务
 * 5. 优雅关闭：确保应用关闭时所有任务都能完成
 * </p>
 *
 * @author maple framework team
 */
@Log4j2
@Configuration
public class GXAsyncConfig implements AsyncConfigurer {
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
            log.warn("Failed to get system load average: {}", e.getMessage());
            return -1;
        }
    }

    @Override
    @Bean("asyncExecutor")
    public Executor getAsyncExecutor() {
        threadPoolTaskExecutor = new ThreadPoolTaskExecutor();

        // 获取核心线程数：根据 CPU 核心数设置，推荐值为 CPU 核心数 * 2
        int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
        // 设置核心线程数
        threadPoolTaskExecutor.setCorePoolSize(corePoolSize);

        // 设置最大线程数：核心线程数的 2 倍，确保有足够的线程处理突发任务
        // 在高负载情况下，最大线程数不宜过大，防止系统资源耗尽
        int maxPoolSize = corePoolSize * 2;
        threadPoolTaskExecutor.setMaxPoolSize(maxPoolSize);

        // 设置队列容量：根据任务量和内存情况调整
        // 队列过小可能导致频繁触发拒绝策略，队列过大可能导致内存溢出
        // 建议根据实际业务场景和监控数据进行调优
        int queueCapacity = 500;
        threadPoolTaskExecutor.setQueueCapacity(queueCapacity);

        // 设置线程空闲存活时间（秒）：非核心线程的空闲时间
        threadPoolTaskExecutor.setKeepAliveSeconds(60);

        // 设置线程名前缀：便于日志追踪和问题排查
        threadPoolTaskExecutor.setThreadNamePrefix("maple-framework-async-thread-");

        // 设置线程分组名称：便于管理和监控
        threadPoolTaskExecutor.setThreadGroupName("maple-framework-async-thread-group");

        // 设置智能拒绝策略：根据系统负载和线程池状态动态选择处理方式
        threadPoolTaskExecutor.setRejectedExecutionHandler((runnable, executor) -> {
            // 获取线程池状态信息
            int activeCount = executor.getActiveCount();
            int queueSize = executor.getQueue().size();
            double systemLoad = getSystemLoadAverage();

            // 记录详细的拒绝信息，便于问题排查
            log.error("Task rejected: thread pool saturated. Stats: [activeThreads={}, queueSize={}, systemLoad={}]",
                    activeCount, queueSize, systemLoad);

            // 系统负载较高时直接拒绝，防止系统崩溃
            if (systemLoad > 0.8 || activeCount >= maxPoolSize) {
                log.error("System under heavy load, rejecting task to protect system stability");
                throw new RejectedExecutionException("Task rejected: System under heavy load");
            }

            // 系统负载可接受时，尝试使用调用者线程执行任务
            // 这是一种退化策略，可能会影响调用者线程的响应时间，但避免了任务丢失
            try {
                log.warn("Executing rejected task in caller thread as fallback strategy");
                new ThreadPoolExecutor.CallerRunsPolicy().rejectedExecution(runnable, executor);
            } catch (Exception e) {
                log.error("Failed to execute task in caller thread", e);
                throw new RejectedExecutionException("Task execution failed in both worker and caller threads");
            }
        });

        // 优雅关闭：等待所有任务完成后再关闭线程池
        threadPoolTaskExecutor.setWaitForTasksToCompleteOnShutdown(true);

        // 最多等待时间：避免关闭时间过长
        threadPoolTaskExecutor.setAwaitTerminationSeconds(60);

        // 任务装饰器：添加任务执行超时检测和异常处理
        threadPoolTaskExecutor.setTaskDecorator(runnable -> {
            return () -> {
                // 记录任务开始时间，用于监控任务执行时长
                long startTime = System.currentTimeMillis();
                // 任务超时时间（毫秒）：10分钟
                final long timeout = TimeUnit.MINUTES.toMillis(10);

                try {
                    // 执行原始任务
                    runnable.run();

                    // 检查任务执行时间是否过长
                    long executionTime = System.currentTimeMillis() - startTime;
                    if (executionTime > timeout) {
                        log.warn("Long-running async task detected: {} ms (exceeded timeout of {} ms)",
                                executionTime, timeout);
                    } else if (executionTime > 60000) { // 60秒
                        log.warn("Long-running async task detected: {} ms", executionTime);
                    }
                } catch (Exception e) {
                    // 记录详细的异常信息
                    log.error("Async task execution failed after {} ms",
                            (System.currentTimeMillis() - startTime), e);
                    throw e;
                }
            };
        });

        // 初始化线程池
        threadPoolTaskExecutor.initialize();

        // 预热线程池：创建核心线程，避免首次任务执行延迟
        threadPoolTaskExecutor.getThreadPoolExecutor().prestartAllCoreThreads();

        // 打印线程池配置信息，便于调试
        log.info("Async thread pool initialized with corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                corePoolSize, maxPoolSize, queueCapacity);

        return threadPoolTaskExecutor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            // 使用 StringBuilder 高效拼接日志信息
            StringBuilder errorMsg = new StringBuilder()
                    .append("--------------Maple Leaf FrameWork异步调用，异常捕获--------------\n")
                    .append("Exception message: ").append(throwable.getMessage()).append("\n")
                    .append("Method name: ").append(method.getName()).append("\n")
                    .append("Class name: ").append(method.getDeclaringClass().getName()).append("\n");

            // 安全地记录参数，避免 toString() 异常
            for (int i = 0; i < params.length; i++) {
                Object param = params[i];
                String paramValue;
                try {
                    paramValue = param != null ? param.toString() : "null";
                } catch (Exception e) {
                    paramValue = "Error converting parameter to string: " + e.getMessage();
                }
                errorMsg.append("Parameter ").append(i).append(": ").append(paramValue).append("\n");
            }

            // 添加线程池状态信息，帮助诊断是否与线程池相关
            if (threadPoolTaskExecutor != null) {
                ThreadPoolExecutor executor = threadPoolTaskExecutor.getThreadPoolExecutor();
                errorMsg.append("Thread pool status: [active=").append(executor.getActiveCount())
                        .append(", poolSize=").append(executor.getPoolSize())
                        .append(", corePoolSize=").append(executor.getCorePoolSize())
                        .append(", maxPoolSize=").append(executor.getMaximumPoolSize())
                        .append(", queueSize=").append(executor.getQueue().size())
                        .append("]\n");
            }

            errorMsg.append("--------------Maple Leaf FrameWork异步调用，异常捕获--------------");
            log.error(errorMsg.toString(), throwable);
        };
    }
}
