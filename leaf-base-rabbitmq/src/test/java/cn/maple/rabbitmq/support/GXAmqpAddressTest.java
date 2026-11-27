package cn.maple.rabbitmq.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 锁定 AMQP 1.0 v2 地址构造：默认交换机走 {@code /queues/}，其余走 {@code /exchanges/}，
 * 且路径片段按 RFC 3986 编码（空格编码为 {@code %20} 而非 {@code +}）。
 */
class GXAmqpAddressTest {
    @Test
    void shouldMapDefaultExchangeToQueueAddress() {
        assertThat(GXAmqpAddress.forPublish("", "order.queue")).isEqualTo("/queues/order.queue");
        assertThat(GXAmqpAddress.forPublish(null, "order.queue")).isEqualTo("/queues/order.queue");
    }

    @Test
    void shouldMapNamedExchangeToExchangeAddress() {
        assertThat(GXAmqpAddress.forPublish("order.exchange", "order.created"))
                .isEqualTo("/exchanges/order.exchange/order.created");
    }

    @Test
    void shouldOmitRoutingKeySegmentWhenBlank() {
        assertThat(GXAmqpAddress.forPublish("order.exchange", "")).isEqualTo("/exchanges/order.exchange");
        assertThat(GXAmqpAddress.forPublish("order.exchange", null)).isEqualTo("/exchanges/order.exchange");
    }

    @Test
    void shouldPercentEncodeReservedCharacters() {
        assertThat(GXAmqpAddress.forPublish("amq.direct", "my-routing_key/123"))
                .isEqualTo("/exchanges/amq.direct/my-routing_key%2F123");
        assertThat(GXAmqpAddress.forQueue("order queue")).isEqualTo("/queues/order%20queue");
        assertThat(GXAmqpAddress.forQueue("订单")).isEqualTo("/queues/%E8%AE%A2%E5%8D%95");
    }

    @Test
    void shouldKeepUnreservedCharacters() {
        assertThat(GXAmqpAddress.encode("aZ09-._~")).isEqualTo("aZ09-._~");
    }

    @Test
    void shouldRejectBlankQueueName() {
        assertThatThrownBy(() -> GXAmqpAddress.forQueue(null))
                .isInstanceOf(cn.maple.core.framework.exception.GXBusinessException.class)
                .hasMessageContaining("队列名称不能为空");
        assertThatThrownBy(() -> GXAmqpAddress.forQueue(" "))
                .isInstanceOf(cn.maple.core.framework.exception.GXBusinessException.class);
    }
}
