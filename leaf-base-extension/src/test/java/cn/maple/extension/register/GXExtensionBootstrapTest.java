package cn.maple.extension.register;

import cn.maple.extension.GXBizScenario;
import cn.maple.extension.GXExtension;
import cn.maple.extension.GXExtensionExecutor;
import cn.maple.extension.GXExtensionPoint;
import cn.maple.extension.GXExtensions;
import cn.maple.extension.autoconfigure.GXExtensionAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void mixedAnnotationsRegisterEveryDeclaredCoordinate() {
        contextRunner.withUserConfiguration(MixedDeclarationConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    GXExtensionExecutor executor = context.getBean(GXExtensionExecutor.class);
                    assertThat(executor.execute(MixedStartupExtPoint.class,
                            GXBizScenario.valueOf("startupMixedA"), MixedStartupExtPoint::name)).isEqualTo("mixed");
                    assertThat(executor.execute(MixedStartupExtPoint.class,
                            GXBizScenario.valueOf("startupMixedB"), MixedStartupExtPoint::name)).isEqualTo("mixed");
                });
    }

    @Test
    void cartesianOnlyAnnotationRegistersEveryCoordinate() {
        contextRunner.withUserConfiguration(CartesianOnlyConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    GXExtensionExecutor executor = context.getBean(GXExtensionExecutor.class);
                    assertThat(executor.execute(CartesianStartupExtPoint.class,
                            GXBizScenario.valueOf("startupCartA"), CartesianStartupExtPoint::name)).isEqualTo("cartesian");
                    assertThat(executor.execute(CartesianStartupExtPoint.class,
                            GXBizScenario.valueOf("startupCartB"), CartesianStartupExtPoint::name)).isEqualTo("cartesian");
                });
    }

    @Test
    void lazyExtensionBeanIsRegisteredAndExecutable() {
        contextRunner.withUserConfiguration(LazyExtensionConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeanFactory().getBeanDefinition("lazyStartupExtension").isLazyInit()).isTrue();
                    GXExtensionExecutor executor = context.getBean(GXExtensionExecutor.class);
                    assertThat(executor.execute(LazyStartupExtPoint.class,
                            GXBizScenario.valueOf("lazyStartup"), LazyStartupExtPoint::name)).isEqualTo("lazy");
                });
    }

    @Test
    void conflictReportKeepsExistingAndDuplicateRolesAcrossRestarts() {
        String firstStartup = startupFailureMessage();
        String secondStartup = startupFailureMessage();

        assertThat(firstStartup).isEqualTo(secondStartup);
        assertThat(firstStartup).contains("existing=" + FirstDuplicateStartupExtension.class.getName());
        assertThat(firstStartup).contains("duplicate=" + SecondDuplicateStartupExtension.class.getName());
    }

    private String startupFailureMessage() {
        AtomicReference<String> capture = new AtomicReference<>();
        contextRunner.withUserConfiguration(DuplicateExtensionConfig.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();
                    capture.set(rootCauseMessage(Objects.requireNonNull(failure)));
                });
        return Objects.requireNonNull(capture.get());
    }

    private String rootCauseMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }

    interface LazyStartupExtPoint extends GXExtensionPoint {
        String name();
    }

    @GXExtension(bizId = "lazyStartup")
    static class LazyStartupExtension implements LazyStartupExtPoint {
        @Override
        public String name() {
            return "lazy";
        }
    }

    @Configuration
    static class LazyExtensionConfig {
        @Bean
        @Lazy
        LazyStartupExtension lazyStartupExtension() {
            return new LazyStartupExtension();
        }
    }

    interface MixedStartupExtPoint extends GXExtensionPoint {
        String name();
    }

    @GXExtension(bizId = "startupMixedA")
    @GXExtensions({@GXExtension(bizId = "startupMixedB")})
    static class MixedStartupExtension implements MixedStartupExtPoint {
        @Override
        public String name() {
            return "mixed";
        }
    }

    @Configuration
    @Import(MixedStartupExtension.class)
    static class MixedDeclarationConfig {
    }

    interface CartesianStartupExtPoint extends GXExtensionPoint {
        String name();
    }

    @GXExtensions(bizId = {"startupCartA", "startupCartB"})
    static class CartesianStartupExtension implements CartesianStartupExtPoint {
        @Override
        public String name() {
            return "cartesian";
        }
    }

    @Configuration
    @Import(CartesianStartupExtension.class)
    static class CartesianOnlyConfig {
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
