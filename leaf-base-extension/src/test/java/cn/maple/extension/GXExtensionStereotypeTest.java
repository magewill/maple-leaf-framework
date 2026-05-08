package cn.maple.extension;

import cn.maple.extension.autoconfigure.GXExtensionAutoConfiguration;
import cn.maple.extension.stereotype.StereotypeExtPoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class GXExtensionStereotypeTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GXExtensionAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void gxExtensionIsDetectedAsSpringComponent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(StereotypeExtPoint.class);

            GXExtensionExecutor executor = context.getBean(GXExtensionExecutor.class);
            String result = executor.execute(StereotypeExtPoint.class,
                    GXBizScenario.valueOf("stereotype"),
                    StereotypeExtPoint::name);

            assertThat(result).isEqualTo("single");
        });
    }

    @Configuration
    @ComponentScan(basePackageClasses = StereotypeExtPoint.class)
    static class TestConfig {
    }
}
