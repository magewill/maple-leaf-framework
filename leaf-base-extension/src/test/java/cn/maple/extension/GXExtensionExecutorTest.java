package cn.maple.extension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GXExtensionExecutorTest {
    private GXExtensionRepository extensionRepository;
    private GXExtensionExecutor extensionExecutor;

    @BeforeEach
    void setUp() {
        extensionRepository = new GXExtensionRepository();
        extensionExecutor = new GXExtensionExecutor();
        ReflectionTestUtils.setField(extensionExecutor, "extensionRepository", extensionRepository);
    }

    @Test
    void executeFallsBackToGlobalDefaultExtension() {
        register(GXBizScenario.newDefault(), new TestDefaultExtension("global-default"));

        String result = extensionExecutor.execute(TestDefaultExtPoint.class,
                GXBizScenario.valueOf("bizA", "useCaseA", "scenarioA"),
                TestDefaultExtPoint::name);

        assertEquals("global-default", result);
    }

    @Test
    void executePrefersExactScenarioExtension() {
        register(GXBizScenario.newDefault(), new TestDefaultExtension("global-default"));
        register(GXBizScenario.valueOf("bizA", "useCaseA"), new TestDefaultExtension("default-scenario"));
        register(GXBizScenario.valueOf("bizA", "useCaseA", "scenarioA"), new TestDefaultExtension("exact"));

        String result = extensionExecutor.execute(TestDefaultExtPoint.class,
                GXBizScenario.valueOf("bizA", "useCaseA", "scenarioA"),
                TestDefaultExtPoint::name);

        assertEquals("exact", result);
    }

    @Test
    void executeFallsBackToDefaultScenarioBeforeOtherDefaults() {
        register(GXBizScenario.newDefault(), new TestDefaultExtension("global-default"));
        register(GXBizScenario.valueOf("bizA"), new TestDefaultExtension("biz-default"));
        register(GXBizScenario.valueOf("bizA", "useCaseA"), new TestDefaultExtension("default-scenario"));

        String result = extensionExecutor.execute(TestDefaultExtPoint.class,
                GXBizScenario.valueOf("bizA", "useCaseA", "scenarioA"),
                TestDefaultExtPoint::name);

        assertEquals("default-scenario", result);
    }

    @Test
    void executePrefersBizDefaultExtensionBeforeGlobalDefaultExtension() {
        register(GXBizScenario.newDefault(), new TestDefaultExtension("global-default"));
        register(GXBizScenario.valueOf("bizA"), new TestDefaultExtension("biz-default"));

        String result = extensionExecutor.execute(TestDefaultExtPoint.class,
                GXBizScenario.valueOf("bizA", "useCaseA", "scenarioA"),
                TestDefaultExtPoint::name);

        assertEquals("biz-default", result);
    }

    @Test
    void executeSupportsStringCoordinate() {
        GXBizScenario bizScenario = GXBizScenario.valueOf("bizA", "useCaseA", "scenarioA");
        register(bizScenario, new TestDefaultExtension("string-coordinate"));

        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(
                TestDefaultExtPoint.class.getName(), bizScenario.getUniqueIdentity());
        String result = extensionExecutor.execute(coordinate, (TestDefaultExtPoint extension) -> extension.name());

        assertEquals("string-coordinate", result);
    }

    @Test
    void executeVoidSupportsStringCoordinate() {
        GXBizScenario bizScenario = GXBizScenario.valueOf("bizA", "useCaseA", "scenarioA");
        register(bizScenario, new TestDefaultExtension("string-coordinate-void"));

        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(
                TestDefaultExtPoint.class.getName(), bizScenario.getUniqueIdentity());
        StringBuilder result = new StringBuilder();
        extensionExecutor.executeVoid(coordinate, (TestDefaultExtPoint extension) -> result.append(extension.name()));

        assertEquals("string-coordinate-void", result.toString());
    }

    private void register(GXBizScenario bizScenario, TestDefaultExtPoint extension) {
        extensionRepository.registerExtension(new GXExtensionCoordinate(TestDefaultExtPoint.class, bizScenario), extension);
    }

    private interface TestDefaultExtPoint extends GXExtensionPoint {
        String name();
    }

    private record TestDefaultExtension(String name) implements TestDefaultExtPoint {
    }
}
