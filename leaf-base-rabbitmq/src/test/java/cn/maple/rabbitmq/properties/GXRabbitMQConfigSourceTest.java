package cn.maple.rabbitmq.properties;

import cn.maple.rabbitmq.properties.local.GXLocalRabbitMQProperties;
import cn.maple.rabbitmq.properties.nacos.GXNacosRabbitMQProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 双配置源互斥：任意取值下只有一个 Properties 变体能装配。
 */
class GXRabbitMQConfigSourceTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GXLocalRabbitMQProperties.class, GXNacosRabbitMQProperties.class);

    @Test
    void shouldPreferNacosWhenPropertyMissing() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(GXNacosRabbitMQProperties.class);
            assertThat(context).doesNotHaveBean(GXLocalRabbitMQProperties.class);
            assertThat(context.getBeansOfType(GXRabbitMQProperties.class)).hasSize(1);
        });
    }

    @Test
    void shouldUseLocalWhenExplicitlyConfigured() {
        runner.withPropertyValues("maple.framework.rabbitmq.config-source=local")
                .run(context -> {
                    assertThat(context).hasSingleBean(GXLocalRabbitMQProperties.class);
                    assertThat(context).doesNotHaveBean(GXNacosRabbitMQProperties.class);
                    assertThat(context.getBeansOfType(GXRabbitMQProperties.class)).hasSize(1);
                });
    }

    @Test
    void shouldUseNacosWhenExplicitlyConfigured() {
        runner.withPropertyValues("maple.framework.rabbitmq.config-source=nacos")
                .run(context -> {
                    assertThat(context).hasSingleBean(GXNacosRabbitMQProperties.class);
                    assertThat(context).doesNotHaveBean(GXLocalRabbitMQProperties.class);
                });
    }

    @Test
    void shouldNotRegisterAnyVariantForUnknownSource() {
        runner.withPropertyValues("maple.framework.rabbitmq.config-source=unknown")
                .run(context -> assertThat(context.getBeansOfType(GXRabbitMQProperties.class)).isEmpty());
    }
}
