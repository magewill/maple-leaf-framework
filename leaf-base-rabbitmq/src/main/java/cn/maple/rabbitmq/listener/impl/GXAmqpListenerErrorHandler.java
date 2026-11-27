package cn.maple.rabbitmq.listener.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.qpid.protonj2.client.Delivery;
import org.apache.qpid.protonj2.client.DeliveryState;
import org.apache.qpid.protonj2.client.exceptions.ClientException;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.client.listener.AmqpListenerErrorHandler;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.messaging.Message;

/**
 * 监听器错误处理器：把"消息体无法转换"的毒消息直接 reject，避免无限重投。
 *
 * <p>AMQP 1.0 的容器在监听器抛出异常时会 release（重新入队）。如果异常发生在消息转换阶段
 * （例如载荷不是合法 JSON 而转换器是 {@code JacksonJsonMessageConverter}），监听器方法根本没有机会
 * 执行，消息会被永久重投。本处理器把这类失败结算为 {@code rejected}，由部署方的死信配置接管。</p>
 *
 * <p>其他异常原样抛出，仍由容器按 {@code recovery-backoff} 处理。</p>
 */
@Slf4j
public class GXAmqpListenerErrorHandler implements AmqpListenerErrorHandler {
    @Override
    public @Nullable Object handleError(Delivery delivery, @Nullable Message<?> message,
                                        ListenerExecutionFailedException exception) throws Exception {
        if (isConversionFailure(exception)) {
            if (!delivery.settled()) {
                reject(delivery);
            }
            log.warn("RABBITMQ_LISTENER_REJECTED reason=unconvertible, errorType={}, error={}",
                    rootCause(exception).getClass().getName(), rootCause(exception).getMessage());
            return null;
        }
        throw exception;
    }

    private static void reject(Delivery delivery) {
        try {
            // ProtonJ 要求 condition 与 message 同时为空或同时存在，这里只表达"拒绝且不重投"。
            delivery.disposition(DeliveryState.rejected(null, null), true);
        } catch (ClientException e) {
            log.debug("RabbitMQ delivery reject failed: {}", e.getClass().getName());
        }
    }

    private static boolean isConversionFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof MessageConversionException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
