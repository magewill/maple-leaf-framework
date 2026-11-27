package cn.maple.rabbitmq.config;

import cn.maple.rabbitmq.GXRabbitMQTestApplication;
import cn.maple.rabbitmq.listener.impl.GXDefaultRabbitMQQueueListenerImpl;
import cn.maple.rabbitmq.properties.GXRabbitMQProperties;
import cn.maple.rabbitmq.support.GXAmqpManagementClient;
import cn.maple.rabbitmq.support.GXAmqpMessagePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.client.AmqpClient;
import org.springframework.amqp.client.AmqpConnectionFactory;
import org.springframework.amqp.client.config.MethodAmqpMessageListenerContainerFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.AmqpConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AMQP 1.0 装配验证：不连接 Broker，只校验 Bean 图与关键参数。
 */
@SpringBootTest(classes = GXRabbitMQTestApplication.class,
        properties = {
                "spring.rabbitmq.host=localhost",
                "spring.rabbitmq.port=5672",
                "spring.rabbitmq.virtual-host=/maple",
                "spring.rabbitmq.username=guest",
                "spring.rabbitmq.password=guest",
                "spring.amqp.listener.auto-startup=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "maple.framework.mq.rabbitmq.enable=true",
                "maple.framework.mq.rabbitmq.default-queue-name=test.default.queue",
                "spring.rabbitmq.default-queue-name=test.default.queue"
        })
class GXRabbitMQConfigSpringBootTest {
    @Autowired
    private ApplicationContext context;

    @Autowired
    private GXRabbitMQProperties rabbitProperties;

    @Test
    void shouldExposeAmqp10Beans() {
        assertThat(context.getBean(AmqpConnectionFactory.class)).isNotNull();
        assertThat(context.getBean(AmqpClient.class)).isNotNull();
        assertThat(context.getBean(GXAmqpMessagePublisher.class)).isNotNull();
        assertThat(context.getBean(GXAmqpManagementClient.class)).isNotNull();
        assertThat(context.getBean(MethodAmqpMessageListenerContainerFactory.class)).isNotNull();
    }

    @Test
    void shouldUseJacksonConverter() {
        assertThat(context.getBean(MessageConverter.class)).isInstanceOf(JacksonJsonMessageConverter.class);
    }

    @Test
    void shouldDisableAutoAcceptOnListenerContainerFactory() {
        MethodAmqpMessageListenerContainerFactory factory =
                context.getBean(MethodAmqpMessageListenerContainerFactory.class);

        assertThat(ReflectionTestUtils.getField(factory, "autoAccept")).isEqualTo(false);
    }

    @Test
    void shouldResolveLegacyConnectionDetails() {
        AmqpConnectionDetails details = context.getBean(AmqpConnectionDetails.class);

        assertThat(details.getAddress().host()).isEqualTo("localhost");
        assertThat(details.getAddress().port()).isEqualTo(5672);
        assertThat(details.getUsername()).isEqualTo("guest");
        assertThat(details.getPassword()).isEqualTo("guest");
    }

    @Test
    void shouldPreferSpringAmqpPrefixWhenPresent() {
        assertThat(context.getBean(AmqpConnectionDetails.class).getAddress().host()).isEqualTo("localhost");
    }

    @Test
    void shouldRegisterDefaultListenerWhenEnabled() {
        assertThat(context.getBean(GXDefaultRabbitMQQueueListenerImpl.class)).isNotNull();
    }

    @Test
    void shouldExposeSinglePropertiesVariant() {
        assertThat(context.getBeansOfType(GXRabbitMQProperties.class)).hasSize(1);
    }

    @Test
    void shouldBindSpringRabbitProperties() {
        assertThat(rabbitProperties.getHost()).isEqualTo("localhost");
        assertThat(rabbitProperties.getPort()).isEqualTo(5672);
        assertThat(rabbitProperties.getVirtualHost()).isEqualTo("/maple");
        assertThat(rabbitProperties.getDefaultQueueName()).isEqualTo("test.default.queue");
    }

    @Test
    void shouldNotExposeClassicBeans() {
        assertThat(context.containsBean("rabbitTemplate")).isFalse();
        assertThat(context.containsBean("rabbitAdmin")).isFalse();
        assertThat(context.containsBean("asyncRabbitTemplate")).isFalse();
    }
}
