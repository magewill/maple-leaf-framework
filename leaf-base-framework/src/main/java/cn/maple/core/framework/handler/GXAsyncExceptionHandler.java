package cn.maple.core.framework.handler;

import cn.maple.core.framework.service.GXBotNotificationExceptionService;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Component
public class GXAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
    private final ObjectProvider<ThreadPoolTaskExecutor> asyncExecutorProvider;

    public GXAsyncExceptionHandler(ObjectProvider<ThreadPoolTaskExecutor> asyncExecutorProvider) {
        this.asyncExecutorProvider = asyncExecutorProvider;
    }

    @Override
    public void handleUncaughtException(Throwable throwable, Method method, Object @Nullable [] params) {
        StringBuilder errorMsg = new StringBuilder(512)
                .append("--------------Maple Leaf Framework async exception--------------\n")
                .append("Exception message: ").append(throwable.getMessage()).append("\n")
                .append("Method name: ").append(method.getName()).append("\n")
                .append("Class name: ").append(method.getDeclaringClass().getName()).append("\n");

        appendParams(errorMsg, params);
        appendExecutorStatus(errorMsg);
        errorMsg.append("--------------Maple Leaf Framework async exception--------------");

        log.error(errorMsg.toString(), throwable);
        notifyException(throwable);
    }

    private void appendParams(StringBuilder errorMsg, Object @Nullable [] params) {
        if (params == null || params.length == 0) {
            return;
        }
        errorMsg.append("Method params:\n");
        for (int i = 0; i < params.length; i++) {
            errorMsg.append("  param[").append(i).append("]: ").append(safeToString(params[i])).append("\n");
        }
    }

    private String safeToString(@Nullable Object param) {
        try {
            return param == null ? "null" : param.toString();
        } catch (Exception e) {
            return "Failed to convert param to string: " + e.getMessage();
        }
    }

    private void appendExecutorStatus(StringBuilder errorMsg) {
        ThreadPoolTaskExecutor asyncExecutor = asyncExecutorProvider.getIfAvailable();
        if (asyncExecutor == null) {
            return;
        }
        try {
            ThreadPoolExecutor executor = asyncExecutor.getThreadPoolExecutor();
            errorMsg.append("Thread pool status [active=").append(executor.getActiveCount())
                    .append(", poolSize=").append(executor.getPoolSize())
                    .append(", corePoolSize=").append(executor.getCorePoolSize())
                    .append(", maxPoolSize=").append(executor.getMaximumPoolSize())
                    .append(", queueSize=").append(executor.getQueue().size())
                    .append("]\n");

            double systemLoad = GXCommonUtils.getSystemLoadAverage();
            if (systemLoad >= 0) {
                errorMsg.append("System load: ").append(String.format("%.2f", systemLoad)).append("\n");
            }
        } catch (Exception e) {
            log.warn("Failed to collect async executor status: {}", e.getMessage());
        }
    }

    private void notifyException(Throwable throwable) {
        try {
            GXBotNotificationExceptionService botNotificationService =
                    GXSpringContextUtils.getBean(GXBotNotificationExceptionService.class);
            if (Objects.nonNull(botNotificationService)) {
                botNotificationService.botNotificationException(throwable);
                log.info("Async exception notification sent");
            }
        } catch (Exception e) {
            log.warn("Failed to send async exception notification: {}", e.getMessage());
        }
    }
}
