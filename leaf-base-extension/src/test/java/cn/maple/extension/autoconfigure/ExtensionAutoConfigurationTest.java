package cn.maple.extension.autoconfigure;

import cn.maple.extension.GXExtensionExecutor;
import cn.maple.extension.GXExtensionRepository;
import cn.maple.extension.register.GXExtensionBootstrap;
import cn.maple.extension.register.GXExtensionRegister;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExtensionAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ExtensionAutoConfiguration.class));

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
}
