package cn.maple.retry.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.stereotype.Component;

/**
 * Spring Retry框架的重试监听器
 * <p>
 * 该监听器用于监控重试过程中的各个事件，包括：
 * - 重试开始前
 * - 重试过程中发生错误
 * - 重试结束后
 * <p>
 * 通过日志记录重试过程中的关键信息，便于问题排查和性能监控
 *
 * @author maple
 * @since 1.0.0
 */
@Slf4j
@Component
public class GXRetryListener implements RetryListener {
    /**
     * 在重试操作开始前调用的方法
     * <p>
     * 此方法在重试流程开始前执行，可用于记录重试开始的信息，或进行必要的准备工作
     *
     * @param <T>      重试操作返回值的类型
     * @param <E>      重试操作中可能抛出的异常类型
     * @param context  重试上下文，包含重试的配置信息和状态
     * @param callback 重试操作的回调接口
     * @return 返回true表示允许进行重试，返回false表示不允许重试
     */
    @Override
    public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
        Object label = context.getAttribute(RetryContext.NAME);
        String operationName = label != null ? label.toString() : "未命名操作";

        // 记录重试开始的信息
        log.debug("开始重试操作: {}", operationName);
        return true;
    }

    /**
     * 在重试操作结束后调用的方法
     * <p>
     * 此方法在整个重试流程结束后执行，无论重试是否成功，都会调用此方法
     * 可用于记录重试结果、执行清理工作或发送通知
     *
     * @param <T>       重试操作返回值的类型
     * @param <E>       重试操作中可能抛出的异常类型
     * @param context   重试上下文，包含重试的相关信息
     * @param callback  重试操作的回调接口
     * @param throwable 重试过程中最后一次发生的异常，如果重试成功则为null
     */
    @Override
    public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        Object label = context.getAttribute(RetryContext.NAME);
        String operationName = label != null ? label.toString() : "未命名操作";
        int retryCount = context.getRetryCount();

        if (throwable != null) {
            // 重试最终失败
            log.error("重试操作最终失败: {}, 重试次数: {}, 异常类型: {}, 异常信息: {}",
                    operationName, retryCount, throwable.getClass().getSimpleName(), throwable.getMessage());
        } else {
            // 重试成功或无需重试
            if (retryCount > 0) {
                log.info("重试操作最终成功: {}, 重试次数: {}", operationName, retryCount);
            } else {
                log.debug("操作一次性成功，无需重试: {}", operationName);
            }
        }
    }

    /**
     * 当重试操作失败时调用的方法
     * <p>
     * 此方法在每次重试失败后调用，可用于记录每次失败的详细信息
     * 或者根据失败情况决定是否继续重试
     *
     * @param <T>       重试操作返回值的类型
     * @param <E>       重试操作中可能抛出的异常类型
     * @param context   重试上下文，包含重试的相关信息
     * @param callback  重试操作的回调接口
     * @param throwable 导致本次重试失败的异常
     */
    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        Object label = context.getAttribute(RetryContext.NAME);
        String operationName = label != null ? label.toString() : "未命名操作";
        int retryCount = context.getRetryCount();

        // 记录每次重试失败的信息
        log.warn("重试操作失败: {}, 第{}次尝试, 异常类型: {}, 异常信息: {}",
                operationName, retryCount, throwable.getClass().getSimpleName(), throwable.getMessage());

        // 如果是最后一次重试，记录堆栈信息以便调试
        if (context.getAttribute("maxAttempts") != null &&
                retryCount >= (int) context.getAttribute("maxAttempts") - 1) {
            log.error("最后一次重试失败，完整异常信息:", throwable);
        }
    }
}
