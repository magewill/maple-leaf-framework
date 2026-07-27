package cn.maple.core.framework.handler;

import cn.maple.core.framework.service.GXBotNotificationExceptionService;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Component
public class GXAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
    private static final ClassValue<Optional<Method>> DATA_METHOD_CACHE = new ClassValue<>() {
        @Override
        @NullMarked
        protected Optional<Method> computeValue(Class<?> type) {
            return findDataMethod(type);
        }
    };

    private final ObjectProvider<ThreadPoolTaskExecutor> asyncExecutorProvider;

    public GXAsyncExceptionHandler(ObjectProvider<ThreadPoolTaskExecutor> asyncExecutorProvider) {
        this.asyncExecutorProvider = asyncExecutorProvider;
    }

    private static Optional<Method> findDataMethod(Class<?> type) {
        Class<?> current = type;
        while (current != null && Throwable.class.isAssignableFrom(current)) {
            try {
                Method method = current.getDeclaredMethod("getData");
                if (method.getParameterCount() == 0 && method.getReturnType() != Void.TYPE) {
                    method.setAccessible(true);
                    return Optional.of(method);
                }
                current = current.getSuperclass();
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return Optional.empty();
    }

    @Override
    @NullMarked
    public void handleUncaughtException(Throwable throwable, Method method, @Nullable Object... params) {
        StringBuilder errorMsg = new StringBuilder(512)
                .append("--------------Maple Leaf Framework async exception--------------\n")
                .append("Exception type: ").append(throwable.getClass().getName()).append("\n")
                .append("Method name: ").append(method.getName()).append("\n")
                .append("Class name: ").append(method.getDeclaringClass().getName()).append("\n");

        appendParams(errorMsg, throwable, params);
        appendExecutorStatus(errorMsg);
        errorMsg.append("--------------Maple Leaf Framework async exception--------------");

        log.error(errorMsg.toString(), throwable);
        notifyException(throwable);
    }

    private void appendParams(StringBuilder errorMsg, Throwable throwable, @Nullable Object... params) {
        appendExceptionData(errorMsg, throwable);
        if (params == null || params.length == 0) {
            return;
        }
        errorMsg.append("Method params:\n");
        for (int i = 0; i < params.length; i++) {
            errorMsg.append("  param[").append(i).append("] type: ")
                    .append(describeValue(params[i])).append("\n");
        }
    }

    private void appendExceptionData(StringBuilder errorMsg, Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            Object data = getExceptionData(current);
            if (hasDataValue(data)) {
                errorMsg.append("Exception data summary: ").append(describeValue(data)).append("\n");
                return;
            }
            current = current.getCause();
        }
    }

    private @Nullable Object getExceptionData(Throwable throwable) {
        return DATA_METHOD_CACHE.get(throwable.getClass())
                .map(method -> invokeDataMethod(method, throwable))
                .orElse(null);
    }

    private @Nullable Object invokeDataMethod(Method method, Throwable throwable) {
        try {
            return method.invoke(throwable);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    private boolean hasDataValue(@Nullable Object data) {
        return switch (data) {
            case null -> false;
            case Map<?, ?> map -> !map.isEmpty();
            case Collection<?> collection -> !collection.isEmpty();
            case CharSequence charSequence -> !charSequence.isEmpty();
            default -> true;
        };
    }

    private String describeValue(@Nullable Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            return value.getClass().getName() + "(size=" + map.size() + ")";
        }
        if (value instanceof Collection<?> collection) {
            return value.getClass().getName() + "(size=" + collection.size() + ")";
        }
        return value.getClass().getName();
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
