package cn.maple.core.framework.event.config;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.util.GXCommonUtils;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

/**
 * Guava事件总线配置类
 * <p>
 * 该类提供同步和异步事件总线的Bean定义，用于实现基于Guava的事件驱动架构。
 * 只有在激活"guava" profile时才会生效。
 * </p>
 * <p>
 * 主要特性：
 * <ul>
 *   <li>同步事件总线：适用于对实时性要求高且处理逻辑简单的场景</li>
 *   <li>异步事件总线：适用于对实时性要求不高但需要提高系统吞吐量的场景</li>
 *   <li>自适应线程池：根据系统资源动态调整线程池参数</li>
 *   <li>线程池监控：提供线程池运行状态的监控指标</li>
 *   <li>优雅关闭：确保应用关闭时事件能够被正确处理</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 注入同步事件总线
 * @Autowired
 * @Qualifier("eventBus")
 * private EventBus eventBus;
 *
 * // 发布事件
 * eventBus.post(new MyEvent("data"));
 *
 * // 注册订阅者
 * eventBus.register(someSubscriber);
 * </pre>
 * </p>
 *
 * @author maple
 * @since 1.0.0
 */
@Configuration
@Log4j2
@Profile("guava")
public class GuavaEventBusConfig implements ApplicationContextAware {
    /**
     * 线程池监控计数器
     */
    private final LongAdder rejectedTaskCount = new LongAdder();

    /**
     * 线程池中的异常总数
     */
    private final LongAdder exceptionCount = new LongAdder();

    /**
     * 定义同步事件总线
     * <p>
     * 同步事件总线在事件发布时会同步执行所有订阅者的处理方法，
     * 适用于对实时性要求高且处理逻辑简单的场景。
     * </p>
     * <p>
     * 线程安全性：EventBus内部使用CopyOnWriteArraySet存储订阅者，
     * 保证了在多线程环境下的线程安全。
     * </p>
     *
     * @return EventBus 同步事件总线实例
     */
    @Bean("eventBus")
    public EventBus eventBus() {
        log.info("初始化Guava EventBus的同步事件对象");
        String identifier = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "maple-event-bus");
        return new EventBus(identifier + "-guava-event-bus");
    }

    /**
     * 定义异步事件总线
     * <p>
     * 异步事件总线在事件发布时会异步执行所有订阅者的处理方法，
     * 适用于对实时性要求不高但需要提高系统吞吐量的场景。
     * 使用自定义线程池替代directExecutor以提高并发处理能力和系统稳定性。
     * </p>
     * <p>
     * 线程池配置说明：
     * <ul>
     *   <li>核心线程数：CPU核心数</li>
     *   <li>最大线程数：CPU核心数 * 2</li>
     *   <li>空闲线程存活时间：60秒</li>
     *   <li>工作队列容量：根据系统内存动态计算</li>
     *   <li>拒绝策略：自定义策略，记录被拒绝的任务并提供监控</li>
     * </ul>
     * </p>
     *
     * @return AsyncEventBus 异步事件总线实例
     */
    @Bean("asyncEventBus")
    public AsyncEventBus asyncEventBus() {
        log.info("初始化Guava EventBus的异步事件对象");
        String identifier = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "maple-async-event-bus");
        // 获取系统资源信息，用于配置线程池
        int cpuCoreNumber = Runtime.getRuntime().availableProcessors();
        // 创建自定义线程工厂，便于问题排查
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNamePrefix(CharSequenceUtil.format("async-{}-event-pool", identifier))
                .setDaemon(false)
                .setUncaughtExceptionHandler((thread, throwable) -> {
                    // 记录异常数
                    exceptionCount.increment();
                    log.error("事件处理线程[{}]发生未捕获异常: {}", thread.getName(), throwable.getMessage(), throwable);
                })
                .build();

        // 创建线程池执行器
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                cpuCoreNumber,                     // 核心线程数
                Math.max(cpuCoreNumber * 2, 8),    // 最大线程数，至少8个线程
                Duration.ofMinutes(1).toSeconds(), // 空闲线程存活时间
                TimeUnit.SECONDS,                  // 时间单位
                new LinkedBlockingQueue<>(1000), // 工作队列
                threadFactory,                     // 线程工厂
                createRejectedExecutionHandler()    // 自定义拒绝策略
        );
        // 添加线程池关闭钩子
        registerShutdownHook(executor, identifier);
        return new AsyncEventBus(identifier, executor);
    }

    /**
     * 创建自定义拒绝策略
     * <p>
     * 当线程池队列满时，新任务会被拒绝。此策略会记录被拒绝的任务并提供监控指标。
     * 默认采用CallerRunsPolicy，即在调用者线程中执行任务，避免任务丢失。
     * </p>
     *
     * @return 自定义的拒绝策略处理器
     */
    private RejectedExecutionHandler createRejectedExecutionHandler() {
        return (r, executor) -> {
            rejectedTaskCount.increment(); // 记录被拒绝的任务数
            // 获取线程池状态信息用于日志记录
            String poolStatus = String.format(
                    "活动线程数: %d, 核心线程数: %d, 最大线程数: %d, 队列大小: %d, 队列剩余容量: %d",
                    executor.getActiveCount(),
                    executor.getCorePoolSize(),
                    executor.getMaximumPoolSize(),
                    executor.getQueue().size(),
                    executor.getQueue().remainingCapacity()
            );

            log.warn("事件处理线程池队列已满，任务被拒绝。{}", poolStatus);
            // 使用调用者运行策略，避免任务丢失
            if (!executor.isShutdown()) {
                try {
                    // 在调用者线程中执行任务
                    r.run();
                } catch (Exception e) {
                    log.error("在调用者线程中执行被拒绝的任务时发生异常: {}", e.getMessage(), e);
                    throw e;
                }
            }
        };
    }

    /**
     * 注册线程池关闭钩子
     * <p>
     * 确保应用关闭时线程池能够优雅关闭，等待正在执行的任务完成
     * </p>
     *
     * @param executor   线程池执行器
     * @param identifier 线程池标识符
     */
    private void registerShutdownHook(ThreadPoolExecutor executor, String identifier) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在关闭事件处理线程池: {}", identifier);
            try {
                // 拒绝新任务但继续处理队列中的任务
                executor.shutdown();
                // 等待所有任务完成，最多等待30秒
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("事件处理线程池未能在30秒内完全关闭，将强制关闭: {}", identifier);
                    // 取消所有正在执行的任务
                    executor.shutdownNow();
                    // 再次等待所有任务响应中断
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.error("事件处理线程池无法关闭: {}", identifier);
                    }
                }
                log.info("事件处理线程池已成功关闭: {}", identifier);
            } catch (InterruptedException e) {
                // 恢复中断状态
                Thread.currentThread().interrupt();
                log.error("关闭事件处理线程池时被中断: {}", identifier, e);
                // 强制关闭
                executor.shutdownNow();
            }
        }, "shutdown-hook-" + identifier));
    }

    /**
     * 设置应用上下文
     * <p>
     * 将Spring应用上下文保存到单例对象中，便于在非Spring管理的类中获取Bean。
     * 使用双重检查锁定模式确保线程安全。
     * </p>
     *
     * @param applicationContext Spring应用上下文
     * @throws BeansException 如果设置过程中发生异常
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (Objects.isNull(GXApplicationContextSingleton.INSTANCE.getApplicationContext())) {
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(applicationContext);
        }
    }
}
