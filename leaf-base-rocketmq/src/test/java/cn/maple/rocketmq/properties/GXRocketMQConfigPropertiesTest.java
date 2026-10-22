package cn.maple.rocketmq.properties;

import cn.maple.rocketmq.properties.local.GXLocalRocketMQConfigProperties;
import cn.maple.rocketmq.properties.nacos.GXNacosRocketMQConfigProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class GXRocketMQConfigPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestApplication.class)
            .withPropertyValues(
                    "spring.cloud.nacos.config.import-check.enabled=false",
                    "spring.profiles.active=default");

    @Test
    void loadsLocalConfigSourceWhenExplicitlySelected() {
        contextRunner
                .withPropertyValues("rocketmq.config-source=local")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GXLocalRocketMQConfigProperties.class);
                    assertThat(context).doesNotHaveBean(GXNacosRocketMQConfigProperties.class);
                    assertThat(context.getEnvironment().getProperty("rocketmq.test-marker")).isEqualTo("local-source");
                });
    }

    @Test
    void loadsNacosConfigSourceWhenExplicitlySelected() {
        contextRunner
                .withPropertyValues("rocketmq.config-source=nacos", "rocketmq.name-server=127.0.0.1:9876")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GXNacosRocketMQConfigProperties.class);
                    assertThat(context).doesNotHaveBean(GXLocalRocketMQConfigProperties.class);
                });
    }

    @Test
    void failsFastWhenExplicitNacosSourceHasBlankNameServer() {
        contextRunner
                .withPropertyValues("rocketmq.config-source=nacos")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("rocketmq.name-server is blank");
                });
    }

    @Test
    void autoDetectsNacosWhenSwitchIsAbsent() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GXNacosRocketMQConfigProperties.class);
            assertThat(context).doesNotHaveBean(GXLocalRocketMQConfigProperties.class);
        });
    }

    @Test
    void rejectsUnsupportedConfigSource() {
        contextRunner
                .withPropertyValues("rocketmq.config-source=bogus")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("Unsupported rocketmq.config-source=bogus");
                });
    }

    @SpringBootConfiguration
    @Import({GXLocalRocketMQConfigProperties.class, GXNacosRocketMQConfigProperties.class,
            GXRocketMQConfigSourceValidator.class})
    static class TestApplication {
    }
}
