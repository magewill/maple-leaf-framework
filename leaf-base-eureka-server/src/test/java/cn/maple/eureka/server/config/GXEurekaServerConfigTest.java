package cn.maple.eureka.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.core.annotation.AnnotationUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GXEurekaServerConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GXEurekaServerConfig.class));

    @Test
    void autoConfigurationImportContainsEurekaServerConfig() throws IOException {
        try (var inputStream = getClass().getClassLoader()
                .getResourceAsStream("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(inputStream).isNotNull();
            var imports = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(imports).contains(GXEurekaServerConfig.class.getName());
        }
    }

    @Test
    void eurekaServerConfigIsAutoConfigurationAndEnablesEurekaServer() {
        assertThat(AnnotationUtils.findAnnotation(GXEurekaServerConfig.class, AutoConfiguration.class)).isNotNull();
        assertThat(AnnotationUtils.findAnnotation(GXEurekaServerConfig.class, EnableEurekaServer.class)).isNotNull();
    }

    @Test
    void eurekaServerConfigRequiresExplicitSwitch() {
        var conditionalOnProperty = AnnotationUtils.findAnnotation(GXEurekaServerConfig.class, ConditionalOnProperty.class);

        assertThat(conditionalOnProperty).isNotNull();
        assertThat(conditionalOnProperty.name()).containsExactly("maple.framework.enable.eureka-server");
        assertThat(conditionalOnProperty.havingValue()).isEqualTo("true");
        assertThat(conditionalOnProperty.matchIfMissing()).isFalse();
    }

    @Test
    void eurekaServerConfigRequiresServletWebApplication() {
        var conditionalOnWebApplication = AnnotationUtils.findAnnotation(GXEurekaServerConfig.class,
                ConditionalOnWebApplication.class);

        assertThat(conditionalOnWebApplication).isNotNull();
        assertThat(conditionalOnWebApplication.type()).isEqualTo(ConditionalOnWebApplication.Type.SERVLET);
    }

    @Test
    void switchDisabledByDefaultInNonServletContext() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(GXEurekaServerConfig.class);
        });
    }

    @Test
    void missingEurekaServerClassSkipsGracefully() {
        contextRunner
                .withPropertyValues("maple.framework.enable.eureka-server=true")
                .withClassLoader(new FilteredClassLoader(EnableEurekaServer.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(GXEurekaServerConfig.class);
                });
    }
}
