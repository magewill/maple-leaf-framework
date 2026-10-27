package cn.maple.rabbitmq.properties;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GXRabbitMQEnvironmentPostProcessorTest {
    private static final String PROPERTY_SOURCE_NAME = "nacos-rabbit.yml";

    @Test
    void shouldSkipWhenServerAddressMissing() {
        ConfigService configService = mock(ConfigService.class);
        StandardEnvironment environment = environment(Map.of(
                "maple.framework.rabbitmq.config-source", "nacos"));

        new FakeProcessor(configService).postProcessEnvironment(environment, null);

        assertThat(environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)).isFalse();
        verifyNoInteractions(configService);
    }

    @Test
    void shouldLoadYamlFromNacos() throws Exception {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getConfig("rabbit.yml", "DEFAULT_GROUP", 2000L))
                .thenReturn("spring:\n  rabbitmq:\n    host: nacos-host\n    port: 5673\n");
        StandardEnvironment environment = environment(Map.of(
                "maple.framework.rabbitmq.config-source", "nacos",
                "spring.cloud.nacos.config.server-addr", "127.0.0.1:8848"));

        new FakeProcessor(configService).postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.rabbitmq.host")).isEqualTo("nacos-host");
        assertThat(environment.getProperty("spring.rabbitmq.port")).isEqualTo("5673");
        verify(configService).shutDown();
    }

    @Test
    void shouldKeepApplicationConfigPrecedence() throws Exception {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getConfig("rabbit.yml", "DEFAULT_GROUP", 2000L))
                .thenReturn("spring:\n  rabbitmq:\n    host: nacos-host\n");
        StandardEnvironment environment = environment(Map.of(
                "maple.framework.rabbitmq.config-source", "nacos",
                "spring.cloud.nacos.config.server-addr", "127.0.0.1:8848"));
        environment.getPropertySources().addFirst(
                new MapPropertySource("application-config", Map.of("spring.rabbitmq.host", "local-host")));

        new FakeProcessor(configService).postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.rabbitmq.host")).isEqualTo("local-host");
    }

    @Test
    void shouldNotFailWhenNacosIsUnavailable() throws Exception {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getConfig("rabbit.yml", "DEFAULT_GROUP", 2000L))
                .thenThrow(new NacosException(500, "server down"));
        StandardEnvironment environment = environment(Map.of(
                "maple.framework.rabbitmq.config-source", "nacos",
                "spring.cloud.nacos.config.server-addr", "127.0.0.1:8848"));

        assertThatCode(() -> new FakeProcessor(configService).postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();
        assertThat(environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)).isFalse();
    }

    @Test
    void shouldSkipWhenConfigSourceIsLocal() {
        ConfigService configService = mock(ConfigService.class);
        StandardEnvironment environment = environment(Map.of(
                "maple.framework.rabbitmq.config-source", "local",
                "spring.cloud.nacos.config.server-addr", "127.0.0.1:8848"));

        new FakeProcessor(configService).postProcessEnvironment(environment, null);

        verifyNoInteractions(configService);
    }

    private static StandardEnvironment environment(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-config", properties));
        return environment;
    }

    private static final class FakeProcessor extends GXRabbitMQEnvironmentPostProcessor {
        private final ConfigService configService;

        private FakeProcessor(ConfigService configService) {
            this.configService = configService;
        }

        @Override
        protected ConfigService createConfigService(Properties properties) {
            return configService;
        }
    }
}
