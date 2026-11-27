package cn.maple.rabbitmq.callback;

import cn.maple.rabbitmq.dto.inner.GXCorrelationData;
import org.jspecify.annotations.Nullable;

/**
 * 发送结算回调。
 *
 * <p>{@code ack=true} 仅表示 Broker 返回 {@code accepted}；{@code rejected}
 * （交换机不存在、队列拒绝等）、{@code released}（无队列可路由）以及发送异常
 * 一律以 {@code ack=false} 回调。不可路由时还会额外触发 {@link GXReturnsCallback}，
 * 对应经典协议的 {@code basic.return}。</p>
 */
@FunctionalInterface
public interface GXConfirmCallback {
    void confirm(@Nullable GXCorrelationData correlationData, boolean ack, @Nullable String cause);
}
