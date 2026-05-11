package cn.maple.core.framework.event.config;

import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.util.GXCommonUtils;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

@Configuration
@Log4j2
@Profile("guava")
public class GuavaEventBusConfig implements ApplicationContextAware {
    private static final int ASYNC_EVENT_QUEUE_CAPACITY = 1000;

    private static final int ASYNC_EVENT_AWAIT_TERMINATION_SECONDS = 30;

    private static final int ASYNC_EVENT_KEEP_ALIVE_SECONDS = (int) Duration.ofMinutes(1).toSeconds();
    private final LongAdder rejectedTaskCount = new LongAdder();
    private final LongAdder exceptionCount = new LongAdder();
    private volatile Executor asyncEventBusExecutor;

    @Bean("eventBus")
    @ConditionalOnMissingBean(name = "eventBus")
    public EventBus eventBus() {
        log.info("初始化Guava EventBus的同步事件对象");
        String identifier = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "maple-event-bus");
        return new EventBus(identifier + "-guava-event-bus");
    }

    @Bean("asyncEventBus")
    @ConditionalOnMissingBean(name = "asyncEventBus")
    public AsyncEventBus asyncEventBus() {
        log.info("初始化Guava EventBus的异步事件对象");
        String identifier = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "maple-async-event-bus");
        Executor executor = createAsyncEventBusExecutor(identifier);
        asyncEventBusExecutor = executor;
        return new AsyncEventBus(executor, (exception, context) -> {
            exceptionCount.increment();
            log.error("Guava异步事件处理失败，订阅者: {}, 方法: {}, 事件: {}",
                    context.getSubscriber(),
                    context.getSubscriberMethod(),
                    context.getEvent(),
                    exception);
        });
    }

    private Executor createAsyncEventBusExecutor(String identifier) {
        Boolean virtualThreadsEnabled = GXCommonUtils.getEnvironmentValue(
                "maple.event.guava.virtual-threads.enabled",
                Boolean.class,
                true
        );
        if (Boolean.TRUE.equals(virtualThreadsEnabled)) {
            return createVirtualAsyncEventBusExecutor(identifier);
        }
        return createPlatformAsyncEventBusExecutor(identifier);
    }

    private SimpleAsyncTaskExecutor createVirtualAsyncEventBusExecutor(String identifier) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("async-" + identifier + "-event-vt-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(GXCommonUtils.getEnvironmentValue(
                "maple.event.guava.virtual-threads.concurrency-limit",
                Integer.class,
                Runtime.getRuntime().availableProcessors() * 2
        ));
        executor.setTaskTerminationTimeout(TimeUnit.SECONDS.toMillis(ASYNC_EVENT_AWAIT_TERMINATION_SECONDS));
        executor.setCancelRemainingTasksOnClose(true);
        executor.setRejectTasksWhenLimitReached(false);
        return executor;
    }

    private ThreadPoolTaskExecutor createPlatformAsyncEventBusExecutor(String identifier) {
        int cpuCoreNumber = Runtime.getRuntime().availableProcessors();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("async-" + identifier + "-event-pool-");
        executor.setCorePoolSize(cpuCoreNumber);
        executor.setMaxPoolSize(Math.max(cpuCoreNumber * 2, 8));
        executor.setQueueCapacity(ASYNC_EVENT_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(ASYNC_EVENT_KEEP_ALIVE_SECONDS);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(ASYNC_EVENT_AWAIT_TERMINATION_SECONDS);
        executor.setRejectedExecutionHandler(createRejectedExecutionHandler());
        executor.initialize();
        return executor;
    }

    private RejectedExecutionHandler createRejectedExecutionHandler() {
        return (runnable, executor) -> {
            rejectedTaskCount.increment();
            String poolStatus = String.format(
                    "活动线程数: %d, 核心线程数: %d, 最大线程数: %d, 队列大小: %d, 队列剩余容量: %d",
                    executor.getActiveCount(),
                    executor.getCorePoolSize(),
                    executor.getMaximumPoolSize(),
                    executor.getQueue().size(),
                    executor.getQueue().remainingCapacity()
            );

            log.warn("Guava异步事件平台线程池队列已满，任务被拒绝。{}", poolStatus);
            if (executor.isShutdown()) {
                throw new RejectedExecutionException("Guava异步事件平台线程池已关闭");
            }
            runnable.run();
        };
    }

    @PreDestroy
    public void destroy() {
        Executor executor = asyncEventBusExecutor;
        if (executor != null) {
            shutdownExecutor(executor);
        }
    }

    private void shutdownExecutor(Executor executor) {
        log.info("正在关闭Guava异步事件执行器");
        if (executor instanceof SimpleAsyncTaskExecutor simpleAsyncTaskExecutor) {
            simpleAsyncTaskExecutor.close();
            log.info("Guava异步事件虚拟线程执行器已成功关闭");
            return;
        }
        if (executor instanceof ThreadPoolTaskExecutor threadPoolTaskExecutor) {
            threadPoolTaskExecutor.shutdown();
            log.info("Guava异步事件平台线程池已成功关闭");
        }
    }

    Executor getAsyncEventBusExecutor() {
        return asyncEventBusExecutor;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (Objects.isNull(GXApplicationContextSingleton.INSTANCE.getApplicationContext())) {
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(applicationContext);
        }
    }
}
