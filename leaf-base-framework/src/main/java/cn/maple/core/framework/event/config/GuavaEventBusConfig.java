package cn.maple.core.framework.event.config;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ExecutorBuilder;
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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Guava事件总线配置类
 * 提供同步和异步事件总线的Bean定义
 * 只有在激活"guava"profile时才会生效
 *
 * @author maple
 */
@Configuration
@Log4j2
@Profile("guava")
public class GuavaEventBusConfig implements ApplicationContextAware {
    /**
     * 定义同步事件总线
     * 同步事件总线在事件发布时会同步执行所有订阅者的处理方法
     * 适用于对实时性要求高且处理逻辑简单的场景
     *
     * @return EventBus 同步事件总线
     */
    @Bean("eventBus")
    public EventBus eventBus() {
        log.info("初始化Guava EventBus的同步事件对象");
        String identifier = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "maple-event-bus");
        return new EventBus(identifier + "-guava-event-bus");
    }

    /**
     * 定义异步事件总线
     * 异步事件总线在事件发布时会异步执行所有订阅者的处理方法
     * 适用于对实时性要求不高但需要提高系统吞吐量的场景
     * 使用自定义线程池替代directExecutor以提高并发处理能力和系统稳定性
     *
     * @return AsyncEventBus 异步事件总线
     */
    @Bean("asyncEventBus")
    public AsyncEventBus asyncEventBus() {
        log.info("初始化Guava EventBus的异步事件对象");
        String identifier = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "maple-async-event-bus");

        // 获取CPU核心数，用于配置线程池大小
        int cpuCoreNumber = Runtime.getRuntime().availableProcessors();

        // 创建自定义线程工厂，便于问题排查
        ThreadFactory threadFactory = ThreadFactoryBuilder.create()
                .setNamePrefix(CharSequenceUtil.format("{}-{}-{}", "async", identifier, "event-pool"))
                .setDaemon(true) // 设置为守护线程，不阻止JVM退出
                .build();

        // 创建线程池执行器，替代directExecutor以提高并发处理能力
        ThreadPoolExecutor executor = ExecutorBuilder.create()
                .setCorePoolSize(cpuCoreNumber) // 核心线程数设置为CPU核心数
                .setMaxPoolSize(cpuCoreNumber * 2) // 最大线程数设置为CPU核心数的2倍
                .setKeepAliveTime(60, TimeUnit.SECONDS) // 空闲线程存活时间
                .setWorkQueue(new LinkedBlockingQueue<>(1000)) // 工作队列容量
                .setThreadFactory(threadFactory) // 使用自定义线程工厂
                .setHandler(new ThreadPoolExecutor.CallerRunsPolicy()) // 拒绝策略：调用者运行
                .build();

        return new AsyncEventBus(identifier, executor);
    }

    /**
     * 设置应用上下文
     * 将Spring应用上下文保存到单例对象中，便于在非Spring管理的类中获取Bean
     *
     * @param applicationContext Spring应用上下文
     * @throws BeansException 如果设置过程中发生异常
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        GXApplicationContextSingleton.INSTANCE.setApplicationContext(applicationContext);
    }
}
