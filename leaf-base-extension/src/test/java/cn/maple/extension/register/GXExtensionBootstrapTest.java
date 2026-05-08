package cn.maple.extension.register;

import cn.maple.extension.GXBizScenario;
import cn.maple.extension.GXExtension;
import cn.maple.extension.GXExtensionExecutor;
import cn.maple.extension.GXExtensionPoint;
import cn.maple.extension.autoconfigure.GXExtensionAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class GXExtensionBootstrapTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GXExtensionAutoConfiguration.class));

    @Test
    void duplicateExtensionCoordinateFailsDuringSpringBootStartup() {
        contextRunner.withUserConfiguration(DuplicateExtensionConfig.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void repeatableExtensionAnnotationsRegisterOncePerCoordinate() {
        contextRunner.withUserConfiguration(RepeatableExtensionConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    GXExtensionExecutor executor = context.getBean(GXExtensionExecutor.class);
                    assertThat(executor.execute(RepeatableStartupExtPoint.class,
                            GXBizScenario.valueOf("repeatA"),
                            RepeatableStartupExtPoint::name)).isEqualTo("repeatable");
                    assertThat(executor.execute(RepeatableStartupExtPoint.class,
                            GXBizScenario.valueOf("repeatB"),
                            RepeatableStartupExtPoint::name)).isEqualTo("repeatable");
                });
    }

    interface DuplicateStartupExtPoint extends GXExtensionPoint {
    }

    @GXExtension(bizId = "duplicateStartup")
    static class FirstDuplicateStartupExtension implements DuplicateStartupExtPoint {
    }

    @GXExtension(bizId = "duplicateStartup")
    static class SecondDuplicateStartupExtension implements DuplicateStartupExtPoint {
    }

    @Configuration
    @Import({FirstDuplicateStartupExtension.class, SecondDuplicateStartupExtension.class})
    static class DuplicateExtensionConfig {
    }

    interface RepeatableStartupExtPoint extends GXExtensionPoint {
        String name();
    }

    @GXExtension(bizId = "repeatA")
    @GXExtension(bizId = "repeatB")
    static class RepeatableStartupExtension implements RepeatableStartupExtPoint {
        @Override
        public String name() {
            return "repeatable";
        }
    }

    @Configuration
    @Import(RepeatableStartupExtension.class)
    static class RepeatableExtensionConfig {
    }
}
