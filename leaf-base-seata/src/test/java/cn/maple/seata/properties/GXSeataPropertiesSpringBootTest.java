package cn.maple.seata.properties;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.PropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class GXSeataPropertiesSpringBootTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class));

    @Test
    void localSeataPropertiesLoadsWhenNacosConfigManagerIsMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("com.alibaba.cloud.nacos.NacosConfigManager"))
                .withUserConfiguration(GXLocalSeataProperties.class, GXNacosSeataProperties.class)
                .withPropertyValues("spring.profiles.active=test")
                .run(context -> {
                    assertThat(context).hasSingleBean(GXLocalSeataProperties.class);
                    assertThat(context).doesNotHaveBean(GXNacosSeataProperties.class);
                });
    }

    @Test
    void nacosSeataPropertiesLoadsWhenNacosConfigManagerIsAvailable() {
        contextRunner
                .withUserConfiguration(GXLocalSeataProperties.class, GXNacosSeataProperties.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(GXLocalSeataProperties.class);
                    assertThat(context).hasSingleBean(GXNacosSeataProperties.class);
                });
    }

    @Test
    void localSeataPropertiesKeepsProfileSeataYamlAsRequiredSource() {
        PropertySource propertySource = GXLocalSeataProperties.class.getAnnotation(PropertySource.class);

        assertThat(propertySource).isNotNull();
        assertThat(propertySource.value()).containsExactly("classpath:/${spring.profiles.active}/seata.yml");
        assertThat(propertySource.ignoreResourceNotFound()).isFalse();
        assertThat(propertySource.encoding()).isEqualTo("utf-8");
    }

    @Test
    void nacosSeataPropertiesKeepsSeataYamlBindingContract() {
        NacosConfigurationProperties properties =
                GXNacosSeataProperties.class.getAnnotation(NacosConfigurationProperties.class);

        assertThat(properties).isNotNull();
        assertThat(properties.dataId()).isEqualTo("seata.yml");
        assertThat(properties.groupId()).isEqualTo("${spring.cloud.nacos.config.group:${nacos.config.group:}}");
        assertThat(properties.type()).isEqualTo(ConfigType.YAML);
        assertThat(properties.autoRefreshed()).isTrue();
        assertThat(properties.properties().serverAddr())
                .isEqualTo("${spring.cloud.nacos.config.server-addr:${nacos.config.server-addr:}}");
        assertThat(properties.properties().namespace())
                .isEqualTo("${spring.cloud.nacos.config.namespace:${nacos.config.namespace:}}");
        assertThat(properties.properties().username())
                .isEqualTo("${spring.cloud.nacos.username:${nacos.config.username:}}");
        assertThat(properties.properties().password())
                .isEqualTo("${spring.cloud.nacos.password:${nacos.config.password:}}");
    }
}
