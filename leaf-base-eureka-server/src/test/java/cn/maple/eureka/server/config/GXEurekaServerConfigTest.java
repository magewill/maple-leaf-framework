package cn.maple.eureka.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.core.annotation.AnnotationUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class GXEurekaServerConfigTest {
    @Test
    void autoConfigurationImportContainsEurekaServerConfig() throws IOException {
        try (var inputStream = getClass().getClassLoader()
                .getResourceAsStream("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(inputStream).isNotNull();
            String imports = new String(inputStream.readAllBytes());
            assertThat(imports).contains(GXEurekaServerConfig.class.getName());
        }
    }

    @Test
    void eurekaServerConfigIsAutoConfigurationAndEnablesEurekaServer() {
        assertThat(AnnotationUtils.findAnnotation(GXEurekaServerConfig.class, AutoConfiguration.class)).isNotNull();
        assertThat(AnnotationUtils.findAnnotation(GXEurekaServerConfig.class, EnableEurekaServer.class)).isNotNull();
    }
}
