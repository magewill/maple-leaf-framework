package cn.maple.rabbitmq.listener;

import cn.maple.rabbitmq.listener.impl.GXDefaultRabbitMQQueueListenerImpl;
import cn.maple.rabbitmq.listener.impl.GXRabbitMQListenerStatistics;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认监听器的启停条件：默认关闭，显式 {@code enable=true} 才装配。
 */
class GXDefaultRabbitMQQueueListenerConditionTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(GXRabbitMQListenerStatistics.class, () -> Mockito.mock(GXRabbitMQListenerStatistics.class))
            .withUserConfiguration(GXDefaultRabbitMQQueueListenerImpl.class);

    @Test
    void shouldNotRegisterListenerByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(GXDefaultRabbitMQQueueListenerImpl.class));
    }

    @Test
    void shouldRegisterListenerWhenEnabled() {
        runner.withPropertyValues("maple.framework.mq.rabbitmq.enable=true")
                .run(context -> assertThat(context).hasSingleBean(GXDefaultRabbitMQQueueListenerImpl.class));
    }

    @Test
    void shouldNotRegisterListenerWhenExplicitlyDisabled() {
        runner.withPropertyValues("maple.framework.mq.rabbitmq.enable=false")
                .run(context -> assertThat(context).doesNotHaveBean(GXDefaultRabbitMQQueueListenerImpl.class));
    }
}
