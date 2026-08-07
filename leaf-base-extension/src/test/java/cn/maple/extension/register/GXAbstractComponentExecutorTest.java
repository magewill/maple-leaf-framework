package cn.maple.extension.register;

import cn.maple.extension.GXBizScenario;
import cn.maple.extension.GXExtensionCoordinate;
import cn.maple.extension.GXExtensionPoint;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXAbstractComponentExecutorTest {
    private final TestExtension component = () -> "extension";
    private final GXBizScenario bizScenario = GXBizScenario.valueOf("biz");
    private final GXAbstractComponentExecutor executor = new TestExecutor(component);

    @Test
    void executeRejectsNullArguments() {
        Function<TestExtension, String> function = TestExtension::name;

        assertThrows(NullPointerException.class, () -> executor.execute(null, bizScenario, function));
        assertThrows(NullPointerException.class, () -> executor.execute(TestExtension.class, null, function));
        assertThrows(NullPointerException.class, () -> executor.execute(TestExtension.class, bizScenario, null));
    }

    @Test
    void executeWithCoordinateRejectsNullArguments() {
        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(TestExtension.class, bizScenario);
        Function<TestExtension, String> function = TestExtension::name;

        assertThrows(NullPointerException.class, () -> executor.execute(null, function));
        assertThrows(NullPointerException.class, () -> executor.execute(coordinate, null));
    }

    @Test
    void executeVoidRejectsNullArguments() {
        Consumer<TestExtension> consumer = extension -> {
        };

        assertThrows(NullPointerException.class, () -> executor.executeVoid(null, bizScenario, consumer));
        assertThrows(NullPointerException.class, () -> executor.executeVoid(TestExtension.class, null, consumer));
        assertThrows(NullPointerException.class, () -> executor.executeVoid(TestExtension.class, bizScenario, null));
    }

    @Test
    void executeVoidWithCoordinateRejectsNullArguments() {
        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(TestExtension.class, bizScenario);
        Consumer<TestExtension> consumer = extension -> {
        };

        assertThrows(NullPointerException.class, () -> executor.executeVoid(null, consumer));
        assertThrows(NullPointerException.class, () -> executor.executeVoid(coordinate, null));
    }

    @Test
    void executePropagatesInvocationException() {
        IllegalStateException failure = new IllegalStateException("failure");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> executor.execute(TestExtension.class, bizScenario, extension -> {
                    throw failure;
                }));

        assertSame(failure, thrown);
    }

    @Test
    void executeVoidPropagatesInvocationException() {
        IllegalStateException failure = new IllegalStateException("failure");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> executor.executeVoid(TestExtension.class, bizScenario, extension -> {
                    throw failure;
                }));

        assertSame(failure, thrown);
    }

    private interface TestExtension extends GXExtensionPoint {
        String name();
    }

    private static class TestExecutor extends GXAbstractComponentExecutor {
        private final TestExtension component;

        private TestExecutor(TestExtension component) {
            this.component = component;
        }

        @Override
        protected <C> C locateComponent(Class<C> targetClz, GXBizScenario context) {
            return targetClz.cast(component);
        }
    }
}
