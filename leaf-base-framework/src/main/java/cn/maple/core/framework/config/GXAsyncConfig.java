package cn.maple.core.framework.config;

import cn.maple.core.framework.handler.GXAsyncExceptionHandler;
import lombok.extern.log4j.Log4j2;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

@Log4j2
@Configuration
@EnableAsync
public class GXAsyncConfig implements AsyncConfigurer {
    private final ObjectProvider<GXAsyncExceptionHandler> exceptionHandlerProvider;

    public GXAsyncConfig(ObjectProvider<GXAsyncExceptionHandler> exceptionHandlerProvider) {
        this.exceptionHandlerProvider = exceptionHandlerProvider;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
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