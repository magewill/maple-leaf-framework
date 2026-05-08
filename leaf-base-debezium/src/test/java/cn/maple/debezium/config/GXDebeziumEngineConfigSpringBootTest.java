package cn.maple.debezium.config;

import cn.maple.debezium.properties.local.GXLocalDebeziumProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class GXDebeziumEngineConfigSpringBootTest {
    @Test
    void debeziumEngineConfigIsNotLoadedWhenFeatureSwitchIsDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(GXDebeziumEngineConfig.class)
                .withPropertyValues("maple.framework.enable.debezium=false")
                .run(context -> assertThat(context).doesNotHaveBean(GXDebeziumEngineConfig.class));
    }

    @Test
    void localPropertiesBindConfigAfterSpringBootContextStarts() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader("com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"))
                .withUserConfiguration(GXLocalDebeziumProperties.class)
                .withPropertyValues(
                        "debezium.config.name=test-connector",
                        "debezium.config.connector.class=io.debezium.connector.mysql.MySqlConnector",
                        "debezium.config.database.hostname=localhost",
                        "debezium.config.database.port=3306",
                        "debezium.config.database.user=debezium",
                        "debezium.config.database.password=password",
                        "debezium.config.database.server.id=184054",
                        "debezium.config.topic.prefix=test-topic")
                .run(context -> {
                    assertThat(context).hasSingleBean(GXLocalDebeziumProperties.class);
                    GXLocalDebeziumProperties properties = context.getBean(GXLocalDebeziumProperties.class);
                    assertThat(properties.getConfig())
                            .containsEntry("name", "test-connector")
                            .containsEntry("topic.prefix", "test-topic");
                });
    }
}
