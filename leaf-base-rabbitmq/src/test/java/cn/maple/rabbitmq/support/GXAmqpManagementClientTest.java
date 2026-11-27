package cn.maple.rabbitmq.support;

import cn.maple.rabbitmq.support.GXAmqpManagementException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 非法基地址必须在请求构建阶段就映射为统一异常，不能让裸 {@code IllegalArgumentException} 冒泡。
 */
class GXAmqpManagementClientTest {
    @Test
    void shouldWrapIllegalBaseUrl() {
        GXAmqpManagementClient client = new GXAmqpManagementClient("http://bad host:15672", "/",
                "guest", "guest", GXRabbitMQJsonSupport.jsonMapper());

        assertThatThrownBy(() -> client.queueExists("order.queue"))
                .isInstanceOf(GXAmqpManagementException.class)
                .hasMessageContaining("request build failed")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldTrimTrailingSlashOfBaseUrl() {
        GXAmqpManagementClient client = new GXAmqpManagementClient("http://localhost:15672/", "/",
                "guest", "guest", GXRabbitMQJsonSupport.jsonMapper());

        assertThat(client.getBaseUrl()).isEqualTo("http://localhost:15672");
    }
}
