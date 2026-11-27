package cn.maple.rabbitmq.config;

import cn.maple.rabbitmq.properties.GXRabbitMQProperties;
import cn.maple.rabbitmq.support.GXAmqpManagementClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.amqp.autoconfigure.AmqpConnectionDetails;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 连接细节与管理 API 的配置解析：历史 {@code spring.rabbitmq.addresses} 必须继续生效，
 * 模块自身的开关必须从 {@code maple.framework.rabbitmq.*} 读取（不能混进 {@code spring.rabbitmq} 前缀的绑定类）。
 */
class GXRabbitMQConfigTest {
    private final GXRabbitMQConfig config = new GXRabbitMQConfig();

    @Test
    void shouldUseFirstAddressFromLegacyAddressesProperty() {
        GXRabbitMQProperties properties = new GXRabbitMQProperties();
        properties.setAddresses("192.168.56.102:5672,192.168.56.103:5672");

        AmqpConnectionDetails details = config.gxAmqpConnectionDetails(properties, new MockEnvironment());

        assertThat(details.getAddress()).isEqualTo(new AmqpConnectionDetails.Address("192.168.56.102", 5672));
    }

    @Test
    void shouldFallBackToHostAndPortWhenAddressesMissing() {
        GXRabbitMQProperties properties = new GXRabbitMQProperties();
        properties.setHost("broker.internal");
        properties.setPort(5673);

        AmqpConnectionDetails details = config.gxAmqpConnectionDetails(properties, new MockEnvironment());

        assertThat(details.getAddress()).isEqualTo(new AmqpConnectionDetails.Address("broker.internal", 5673));
    }

    @Test
    void shouldPreferSpringAmqpHostOverLegacyProperties() {
        GXRabbitMQProperties properties = new GXRabbitMQProperties();
        properties.setAddresses("192.168.56.102:5672");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.amqp.host", "amqp.internal")
                .withProperty("spring.amqp.port", "5674");

        AmqpConnectionDetails details = config.gxAmqpConnectionDetails(properties, environment);

        assertThat(details.getAddress()).isEqualTo(new AmqpConnectionDetails.Address("amqp.internal", 5674));
    }

    @Test
    void shouldReadManagementUrlFromMaplePrefix() {
        GXRabbitMQProperties properties = new GXRabbitMQProperties();
        properties.setHost("broker.internal");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.rabbitmq.management.url", "http://mgmt.internal:15672");

        GXAmqpManagementClient client = config.gxAmqpManagementClient(properties, environment);

        assertThat(client.getBaseUrl()).isEqualTo("http://mgmt.internal:15672");
    }

    @Test
    void shouldDeriveManagementUrlFromResolvedHost() {
        GXRabbitMQProperties properties = new GXRabbitMQProperties();
        properties.setAddresses("192.168.56.102:5672");

        GXAmqpManagementClient client = config.gxAmqpManagementClient(properties, new MockEnvironment());

        assertThat(client.getBaseUrl()).isEqualTo("http://192.168.56.102:15672");
    }
}
