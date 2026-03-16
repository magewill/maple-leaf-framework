package cn.maple.core.framework.config;

import cn.maple.core.framework.handler.GXAsyncExceptionHandler;
import lombok.extern.log4j.Log4j2;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Spring异步任务线程池配置 (基于 Spring Boot 3.2+ / 4.x 虚拟线程优化版)
 * <p>
 * 在 Spring Boot 配置了 spring.threads.virtual.enabled=true 的情况下，
 * Spring 会自动配置基于虚拟线程的 AsyncTaskExecutor。
 * 本配置类在此前提下主要负责注册异步异常处理器，
 * 移除了过去笨重的物理线程池，解决了拒绝策略的逻辑缺陷与早期Bean注入带来的空指针隐患。
 * </p>
 *
 * @author britton@126.com
 * @since 1.0.0
 */
@Log4j2
@Configuration
@EnableAsync
public class GXAsyncConfig implements AsyncConfigurer {

    private final ObjectProvider<GXAsyncExceptionHandler> exceptionHandlerProvider;

    public GXAsyncConfig(ObjectProvider<GXAsyncExceptionHandler> exceptionHandlerProvider) {
        this.exceptionHandlerProvider = exceptionHandlerProvider;
    }

    /**
     * 获取异步任务异常处理器
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // 返回一个包装的处理器，将 Bean 的实际获取延迟到异常真实发生时。
        // 这彻底避免了 AOP 极早期阶段尝试解析 Bean 可能导致的 null 注入问题。
        return (ex, method, params) -> {
            AsyncUncaughtExceptionHandler handler = exceptionHandlerProvider.getIfAvailable();
            if (handler != null) {
                handler.handleUncaughtException(ex, method, params);
            } else {
                log.error("Async execution error, and custom GXAsyncExceptionHandler is not available", ex);
            }
        };
    }
}