package cn.maple.feign.config;

import cn.maple.feign.codec.GXFeignCustomErrorDecoder;
import cn.maple.feign.interceptor.GXFeignRequestInterceptor;
import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GXFeignConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GXFeignConfig.class));

    @Test
    void autoConfigurationIsDiscoveredFromImports() {
        String imports = readAutoConfigurationImports();

        assertThat(imports).contains(GXFeignConfig.class.getName());
    }

    @Test
    void createsDefaultFeignBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Logger.Level.class);
            assertThat(context).hasSingleBean(ErrorDecoder.class);
            assertThat(context).hasSingleBean(RequestInterceptor.class);
            assertThat(context).hasSingleBean(GXFeignRequestInterceptor.class);
            assertThat(context.getBean(Logger.Level.class)).isEqualTo(Logger.Level.BASIC);
            assertThat(context.getBean(ErrorDecoder.class)).isInstanceOf(GXFeignCustomErrorDecoder.class);
            assertThat(context.getBean(RequestInterceptor.class)).isInstanceOf(GXFeignRequestInterceptor.class);
        });
    }

    @Test
    void customRequestInterceptorDoesNotDisableFrameworkInterceptor() {
        contextRunner.withUserConfiguration(CustomFeignConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(Logger.Level.class);
                    assertThat(context).hasSingleBean(ErrorDecoder.class);
                    assertThat(context).hasSingleBean(GXFeignRequestInterceptor.class);
                    assertThat(context.getBeansOfType(RequestInterceptor.class)).hasSize(2);
                    assertThat(context.getBean(Logger.Level.class)).isEqualTo(Logger.Level.NONE);
                    assertThat(context.getBean(ErrorDecoder.class)).isSameAs(CustomFeignConfig.ERROR_DECODER);
                    assertThat(context.getBeansOfType(RequestInterceptor.class)).containsValue(CustomFeignConfig.REQUEST_INTERCEPTOR);
                });
    }

    @Test
    void customFrameworkInterceptorBacksOffDefault() {
        contextRunner.withUserConfiguration(CustomFrameworkInterceptorConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(GXFeignRequestInterceptor.class);
                    assertThat(context.getBean(GXFeignRequestInterceptor.class)).isSameAs(CustomFrameworkInterceptorConfig.REQUEST_INTERCEPTOR);
                });
    }

    private String readAutoConfigurationImports() {
        try (var inputStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Configuration
    static class CustomFeignConfig {
        private static final ErrorDecoder ERROR_DECODER = (methodKey, response) -> new IllegalStateException(methodKey);
        private static final RequestInterceptor REQUEST_INTERCEPTOR = requestTemplate -> requestTemplate.header("custom", "true");

        @Bean
        Logger.Level customFeignLoggerLevel() {
            return Logger.Level.NONE;
        }

        @Bean
        ErrorDecoder customErrorDecoder() {
            return ERROR_DECODER;
        }

        @Bean
        RequestInterceptor customRequestInterceptor() {
            return REQUEST_INTERCEPTOR;
        }
    }

    @Configuration
    static class CustomFrameworkInterceptorConfig {
        private static final GXFeignRequestInterceptor REQUEST_INTERCEPTOR = new GXFeignRequestInterceptor();

        @Bean
        GXFeignRequestInterceptor customFrameworkRequestInterceptor() {
            return REQUEST_INTERCEPTOR;
        }
    }
}
