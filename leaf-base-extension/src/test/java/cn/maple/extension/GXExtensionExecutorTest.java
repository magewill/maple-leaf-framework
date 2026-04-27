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
    void executePrefersBizDefaultExtensionBeforeGlobalDefaultExtension() {
        register(GXBizScenario.newDefault(), new TestDefaultExtension("global-default"));
        register(GXBizScenario.valueOf("bizA"), new TestDefaultExtension("biz-default"));

        String result = extensionExecutor.execute(TestDefaultExtPoint.class,
                GXBizScenario.valueOf("bizA", "useCaseA", "scenarioA"),
                TestDefaultExtPoint::name);

        assertEquals("biz-default", result);
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
