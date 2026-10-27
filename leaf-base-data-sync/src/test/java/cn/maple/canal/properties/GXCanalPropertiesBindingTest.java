package cn.maple.canal.properties;

import cn.maple.canal.config.GXCanalConfig;
import cn.maple.canal.config.GXCanalPropertiesConfig;
import cn.maple.canal.enums.GXCanalMqTypeEnums;
import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.properties.local.GXLocalCanalProperties;
import cn.maple.canal.properties.nacos.GXNacosCanalProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXCanalPropertiesBindingTest {
    private static final String NACOS_ANNOTATION =
            "com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GXCanalPropertiesConfig.class, BindingSupport.class);

    @Test
    void shouldUseNacosVariantWhenNacosIsOnClassPath() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GXCanalProperties.class);
            assertThat(context.getBean(GXCanalProperties.class)).isInstanceOf(GXNacosCanalProperties.class);
        });
    }

    @Test
    void shouldHonourExplicitLocalSource() {
        contextRunner.withPropertyValues("canal.config-source=local").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(GXCanalProperties.class)).isInstanceOf(GXLocalCanalProperties.class);
        });
    }

    @Test
    void shouldUseLocalVariantWhenNacosIsAbsent() {
        contextRunner.withClassLoader(new FilteredClassLoader(NACOS_ANNOTATION)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(GXCanalProperties.class)).isInstanceOf(GXLocalCanalProperties.class);
        });
    }

    @Test
    void shouldFailFastWhenNacosRequestedButUnavailable() {
        contextRunner.withClassLoader(new FilteredClassLoader(NACOS_ANNOTATION))
                .withPropertyValues("canal.config-source=nacos").run(context -> {
                    assertNotNull(context.getStartupFailure());
                    assertThat(context.getStartupFailure().getMessage()).contains("canal.config-source");
                });
    }

    @Test
    void shouldBindExistingAndNewKeys() {
        contextRunner.withPropertyValues(
                "canal.config-source=local",
                "canal.canal-queue-name=customQueue",
                "canal.exchange-name=custom.exchange",
                "canal.routing-key=custom.routing",
                "canal.concurrency-count=4",
                "canal.retry.max-attempts=5",
                "canal.dead-letter.queue=custom.dlq",
                "canal.mq-type=rabbitmq",
                "canal.handler-cache-size=128",
                "canal.handler-miss-log-level=warn").run(context -> {
            GXCanalProperties properties = context.getBean(GXCanalProperties.class);

            assertEquals("customQueue", properties.getCanalQueueName());
            assertEquals("custom.exchange", properties.getExchangeName());
            assertEquals("custom.routing", properties.getRoutingKey());
            assertEquals("4", properties.getConcurrencyCount());
            assertEquals(5, properties.getRetry().getMaxAttempts());
            assertEquals("custom.dlq", properties.getDeadLetter().getQueue());
            assertEquals(GXCanalMqTypeEnums.RABBITMQ, properties.getMqType());
            assertEquals(128, properties.getHandlerCacheSize());
            assertEquals("warn", properties.getHandlerMissLogLevel());
            assertThat(properties.isConsumerEnabled()).isTrue();
        });
    }

    @Test
    void shouldDefaultHandlerMissLogLevelToWarnOnce() {
        assertEquals(cn.maple.canal.constant.GXCanalConstant.LOG_LEVEL_WARN_ONCE,
                new GXCanalProperties().getHandlerMissLogLevel(),
                "the default must reference LOG_LEVEL_WARN_ONCE so value and fallback branch stay in sync");
    }

    @Test
    void shouldDeclareEveryMutableFieldVolatile() {
        List<Class<?>> propertyTypes = List.of(GXCanalProperties.class, GXCanalRetryProperties.class,
                GXCanalDeadLetterProperties.class, GXCanalRocketMQConsumerProperties.class);

        for (Class<?> propertyType : propertyTypes) {
            for (Field field : propertyType.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || field.isSynthetic()) {
                    continue;
                }
                assertTrue(Modifier.isVolatile(modifiers),
                        propertyType.getSimpleName() + "." + field.getName()
                                + " must be volatile so consumer threads observe a rebind");
            }
        }
    }

    @Test
    void shouldExposeRestartHintForAssemblyTimeKeys() {
        assertThat(cn.maple.canal.constant.GXCanalConstant.ASSEMBLY_RESTART_HINT)
                .contains("only take effect after restart");

        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldKeepAssemblyTimeKeysUntilRestart() {
        new ApplicationContextRunner()
                .withUserConfiguration(GXCanalPropertiesConfig.class, BindingSupport.class,
                        GXCanalConfig.class, StatisticsConfig.class)
                .withPropertyValues("canal.config-source=local", "canal.canal-queue-name=queue-a")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertEquals("queue-a", context.getBean("canalQueue", Queue.class).getName());

                    MutablePropertySources sources = context.getEnvironment().getPropertySources();
                    sources.addFirst(new MapPropertySource("refresh", Map.of("canal.canal-queue-name", "queue-b")));

                    assertEquals("queue-a", context.getBean("canalQueue", Queue.class).getName(),
                            "an assembly-time key must not be applied without a restart");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties
    static class BindingSupport {
    }

    @Configuration(proxyBeanMethods = false)
    static class StatisticsConfig {
        @Bean
        GXCanalConsumeStatistics canalConsumeStatistics() {
            return new GXCanalConsumeStatistics();
        }
    }
}
