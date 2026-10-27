package cn.maple.canal.config;

import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.properties.GXCanalProperties;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GXCanalRocketMQConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GXCanalPropertiesConfig.class, GXCanalConfig.class,
                    GXCanalRocketMQConfig.class, GXCanalRocketMQDependencyConfig.class, StatisticsConfig.class,
                    BindingSupport.class);

    @Test
    void shouldNotDeclareAmqpInfrastructureWhenRocketMqSelected() {
        contextRunner.withPropertyValues(
                "canal.mq-type=rocketmq",
                "canal.rocketmq.topic=canal-topic",
                "canal.rocketmq.consumer-group=canal-group").run(context -> {
            assertThat(context).hasNotFailed();
            assertEquals(0, context.getBeanNamesForType(Queue.class).length);
            assertEquals(0, context.getBeanNamesForType(Exchange.class).length);
            assertNotNull(context.getBean(GXCanalProperties.class));
        });
    }

    @Test
    void shouldFailFastWhenTopicMissing() {
        contextRunner.withPropertyValues("canal.mq-type=rocketmq", "canal.rocketmq.consumer-group=canal-group")
                .run(context -> {
                    assertNotNull(context.getStartupFailure());
                    assertThat(rootMessages(context.getStartupFailure())).contains("canal.rocketmq.topic");
                });
    }

    @Test
    void shouldFailFastWhenConsumerGroupMissing() {
        contextRunner.withPropertyValues("canal.mq-type=rocketmq", "canal.rocketmq.topic=canal-topic")
                .run(context -> {
                    assertNotNull(context.getStartupFailure());
                    assertThat(rootMessages(context.getStartupFailure())).contains("canal.rocketmq.consumer-group");
                });
    }

    @Test
    void shouldFailFastWhenRocketMqDependencyMissing() {
        contextRunner.withClassLoader(new FilteredClassLoader(RocketMQListener.class))
                .withPropertyValues("canal.mq-type=rocketmq",
                        "canal.rocketmq.topic=canal-topic",
                        "canal.rocketmq.consumer-group=canal-group")
                .run(context -> {
                    assertNotNull(context.getStartupFailure());
                    String messages = rootMessages(context.getStartupFailure());
                    assertThat(messages).contains("leaf-base-rocketmq");
                    assertThat(messages).contains("rabbitmq");
                });
    }

    @Test
    void shouldRejectUnknownMqType() {
        contextRunner.withPropertyValues("canal.mq-type=unknown-value").run(context -> {
            assertNotNull(context.getStartupFailure());
            assertThat(rootMessages(context.getStartupFailure())).contains("canal.mq-type");
        });
    }

    private String rootMessages(Throwable failure) {
        StringBuilder builder = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            builder.append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return builder.toString();
    }

    @Configuration(proxyBeanMethods = false)
    static class StatisticsConfig {
        @Bean
        GXCanalConsumeStatistics canalConsumeStatistics() {
            return new GXCanalConsumeStatistics();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties
    static class BindingSupport {
    }
}
