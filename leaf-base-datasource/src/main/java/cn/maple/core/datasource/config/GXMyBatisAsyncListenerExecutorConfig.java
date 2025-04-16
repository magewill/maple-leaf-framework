package cn.maple.core.datasource.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * MyBatis异步监听器线程池配置
 * <p>
 * 本配置类为MyBatis的异步事件监听器提供专用的线程池配置。
 * 线程池参数经过优化，可以高效处理MyBatis的异步事件，同时保证内存安全和系统稳定性。
 * 主要特性：
 * 1. 资源自适应：根据系统CPU核心数自动调整线程池大小
 * 2. 智能拒绝策略：在高负载情况下保护系统稳定性
 * 3. 任务执行监控：监控长时间运行的任务并记录日志
 * 4. 优雅关闭：确保应用关闭时所有事件处理任务都能完成
 * 5. 异常安全处理：捕获并记录所有异步任务异常
 * </p>
 *
 * @author maple framework team
 */
@Log4j2
@Configuration
public class GXMyBatisAsyncListenerExecutorConfig {
    /**
     * 创建MyBatis事件异步处理线程池
     * <p>
     * 该线程池专门用于处理MyBatis的异步事件，如实体保存、更新、删除等操作的异步监听。
     * 线程池配置经过优化，能够在保证性能的同时确保内存安全和系统稳定。
     * </p>
     *
     * @return 配置好的异步任务执行器
     */
    @Bean("myBatisEventAsyncTaskExecutor")
    public AsyncTaskExecutor myBatisEventAsyncTaskExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        
        // 获取CPU核心数，用于动态调整线程池大小
        int cpuCores = Runtime.getRuntime().availableProcessors();
        
        // 设置核心线程数：对于IO密集型任务，核心线程数可以适当设置大一些
        // 但MyBatis事件通常不会有大量并发，所以设置为CPU核心数即可
        threadPoolTaskExecutor.setCorePoolSize(cpuCores);
        
        // 设置最大线程数：只有在队列满了之后才会申请超过核心线程数的线程
        // 设置为核心线程数的2倍，以应对突发事件
        threadPoolTaskExecutor.setMaxPoolSize(cpuCores * 2);
        
        // 设置队列容量：根据预期的事件量设置，避免过大造成内存压力
        threadPoolTaskExecutor.setQueueCapacity(1000);
        
        // 设置线程空闲存活时间（秒）：非核心线程在空闲时间达到后会被销毁
        // 设置为2分钟，平衡资源利用和响应速度
        threadPoolTaskExecutor.setKeepAliveSeconds(120);
        
        // 设置线程名称前缀：便于日志追踪和问题排查
        threadPoolTaskExecutor.setThreadNamePrefix("mybatis-event-async-task-thread-pool-");
        
        // 设置线程分组名称：便于管理和监控
        threadPoolTaskExecutor.setThreadGroupName("mybatis-event-async-task-thread-group");
        
        // 设置智能拒绝策略：在高负载情况下保护系统稳定性
        threadPoolTaskExecutor.setRejectedExecutionHandler((runnable, executor) -> {
            // 获取线程池状态信息
            int activeCount = executor.getActiveCount();
            int queueSize = executor.getQueue().size();
            
            // 记录详细的拒绝信息，便于问题排查
            log.error("MyBatis event task rejected: thread pool saturated. Stats: [activeThreads={}, queueSize={}]",
                    activeCount, queueSize);
            
            // 尝试使用调用者线程执行任务，避免任务丢失
            try {
                log.warn("Executing rejected MyBatis event task in caller thread as fallback strategy");
                new ThreadPoolExecutor.CallerRunsPolicy().rejectedExecution(runnable, executor);
            } catch (Exception e) {
                log.error("Failed to execute MyBatis event task in caller thread", e);
                throw new RejectedExecutionException("MyBatis event task execution failed");
            }
        });
        
        // 设置任务装饰器：添加任务执行监控和异常处理
        threadPoolTaskExecutor.setTaskDecorator(runnable -> {
            return () -> {
                // 记录任务开始时间，用于监控任务执行时长
                long startTime = System.currentTimeMillis();
                
                try {
                    // 执行原始任务
                    runnable.run();
                    
                    // 检查任务执行时间是否过长
                    long executionTime = System.currentTimeMillis() - startTime;
                    if (executionTime > TimeUnit.SECONDS.toMillis(30)) { // 30秒
                        log.warn("Long-running MyBatis event task detected: {} ms", executionTime);
                    }
                } catch (Exception e) {
                    // 记录详细的异常信息
                    log.error("MyBatis event task execution failed after {} ms",
                            (System.currentTimeMillis() - startTime), e);
                    throw e;
                }
            };
        });
        
        // 设置线程池关闭时等待所有任务完成再销毁其他Bean
        // 确保所有事件处理任务都能完成，避免数据不一致
        threadPoolTaskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 设置最多等待时间（秒）：避免关闭时间过长
        threadPoolTaskExecutor.setAwaitTerminationSeconds(60);
        
        // 初始化线程池
        threadPoolTaskExecutor.initialize();
        
        // 预热线程池：创建核心线程，避免首次任务执行延迟
        threadPoolTaskExecutor.getThreadPoolExecutor().prestartAllCoreThreads();
        
        // 打印线程池配置信息，便于调试
        log.info("MyBatis event async thread pool initialized with corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                cpuCores, cpuCores * 2, 1000);
        
        return threadPoolTaskExecutor;
    }
}