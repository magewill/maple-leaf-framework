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

@Configuration
@Log4j2
@Profile("guava")
public class GuavaEventBusConfig implements ApplicationContextAware {
    private final LongAdder rejectedTaskCount = new LongAdder();

    private final LongAdder exceptionCount = new LongAdder();

    @Bean("eventBus")
    public EventBus eventBus() {
        log.info("初始化Guava EventBus的同步事件对象");
        String identifier = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "maple-event-bus");
        return new EventBus(identifier + "-guava-event-bus");
    }

    @Bean("asyncEventBus")
    public AsyncEventBus asyncEventBus() {
        log.info("初始化Guava EventBus的异步事件对象");
        String identifier = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "maple-async-event-bus");
        int cpuCoreNumber = Runtime.getRuntime().availableProcessors();
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNamePrefix(CharSequenceUtil.format("async-{}-event-pool", identifier))
                .setDaemon(false)
                .setUncaughtExceptionHandler((thread, throwable) -> {
                    // 记录异常数
                    exceptionCount.increment();
                    log.error("事件处理线程[{}]发生未捕获异常: {}", thread.getName(), throwable.getMessage(), throwable);
                })
                .build();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                cpuCoreNumber,
                Math.max(cpuCoreNumber * 2, 8),
                Duration.ofMinutes(1).toSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                threadFactory,
                createRejectedExecutionHandler()
        );
        registerShutdownHook(executor, identifier);
        return new AsyncEventBus(identifier, executor);
    }


    private RejectedExecutionHandler createRejectedExecutionHandler() {
        return (r, executor) -> {
            rejectedTaskCount.increment();
            String poolStatus = String.format(
                    "活动线程数: %d, 核心线程数: %d, 最大线程数: %d, 队列大小: %d, 队列剩余容量: %d",
                    executor.getActiveCount(),
                    executor.getCorePoolSize(),
                    executor.getMaximumPoolSize(),
                    executor.getQueue().size(),
                    executor.getQueue().remainingCapacity()
            );

            log.warn("事件处理线程池队列已满，任务被拒绝。{}", poolStatus);
            if (!executor.isShutdown()) {
                try {
                    r.run();
                } catch (Exception e) {
                    log.error("在调用者线程中执行被拒绝的任务时发生异常: {}", e.getMessage(), e);
                    throw e;
                }
            }
        };
    }

    private void registerShutdownHook(ThreadPoolExecutor executor, String identifier) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在关闭事件处理线程池: {}", identifier);
            try {
                executor.shutdown();
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("事件处理线程池未能在30秒内完全关闭，将强制关闭: {}", identifier);
                    executor.shutdownNow();
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.error("事件处理线程池无法关闭: {}", identifier);
                    }
                }
                log.info("事件处理线程池已成功关闭: {}", identifier);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("关闭事件处理线程池时被中断: {}", identifier, e);
                executor.shutdownNow();
            }
        }, "shutdown-hook-" + identifier));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (Objects.isNull(GXApplicationContextSingleton.INSTANCE.getApplicationContext())) {
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(applicationContext);
        }
    }
}
