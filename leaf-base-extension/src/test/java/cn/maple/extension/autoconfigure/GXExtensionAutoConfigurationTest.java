package cn.maple.extension.autoconfigure;

import cn.maple.extension.GXExtensionExecutor;
import cn.maple.extension.GXExtensionRepository;
import cn.maple.extension.register.GXExtensionBootstrap;
import cn.maple.extension.register.GXExtensionRegister;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

class GXExtensionAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GXExtensionAutoConfiguration.class));

    @Test
    void autoConfigurationIsDiscoveredFromImports() {
        String imports = readAutoConfigurationImports();

        assertThat(imports).contains(GXExtensionAutoConfiguration.class.getName());
    }

    @Test
    void createsDefaultExtensionBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GXExtensionBootstrap.class);
            assertThat(context).hasSingleBean(GXExtensionRepository.class);
            assertThat(context).hasSingleBean(GXExtensionExecutor.class);
            assertThat(context).hasSingleBean(GXExtensionRegister.class);
        });
    }

    @Test
    void bootstrapBeanDoesNotDeclareDuplicateInitMethod() {
        contextRunner.run(context -> assertNull(context.getBeanFactory()
                .getBeanDefinition("extensionBootstrap")
                .getInitMethodName()));
    }

    @Test
    void customRepositoryIsUsedByDefaultRegisterAndExecutor() {
        contextRunner.withUserConfiguration(CustomRepositoryConfig.class)
                .run(context -> {
                    GXExtensionRepository repository = context.getBean(GXExtensionRepository.class);
                    GXExtensionRegister register = context.getBean(GXExtensionRegister.class);
                    GXExtensionExecutor executor = context.getBean(GXExtensionExecutor.class);

                    assertThat(repository).isInstanceOf(CustomExtensionRepository.class);
                    assertThat(ReflectionTestUtils.getField(register, "extensionRepository")).isSameAs(repository);
                    assertThat(ReflectionTestUtils.getField(executor, "extensionRepository")).isSameAs(repository);
                });
    }

    @Configuration
    static class CustomRepositoryConfig {
        @Bean
        GXExtensionRepository customExtensionRepository() {
            return new CustomExtensionRepository();
        }
    }

    static class CustomExtensionRepository extends GXExtensionRepository {
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
}
