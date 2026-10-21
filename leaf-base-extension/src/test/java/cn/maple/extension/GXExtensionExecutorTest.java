package cn.maple.extension;

import cn.hutool.http.HttpStatus;
import cn.maple.extension.exception.GXExtensionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void stringCoordinateFallsBackToGlobalDefault() {
        register(GXBizScenario.newDefault(), new TestDefaultExtension("global-default"));

        String result = extensionExecutor.execute(stringCoordinate("bizA", "useCaseA", "scenarioA"),
                (TestDefaultExtPoint extension) -> extension.name());

        assertEquals("global-default", result);
    }

    @Test
    void stringCoordinateFallsBackToDefaultScenario() {
        register(GXBizScenario.newDefault(), new TestDefaultExtension("global-default"));
        register(GXBizScenario.valueOf("bizA", "useCaseA"), new TestDefaultExtension("default-scenario"));

        String result = extensionExecutor.execute(stringCoordinate("bizA", "useCaseA", "scenarioA"),
                (TestDefaultExtPoint extension) -> extension.name());

        assertEquals("default-scenario", result);
    }

    @Test
    void stringCoordinateFallsBackToDefaultUseCase() {
        register(GXBizScenario.newDefault(), new TestDefaultExtension("global-default"));
        register(GXBizScenario.valueOf("bizA"), new TestDefaultExtension("biz-default"));

        StringBuilder result = new StringBuilder();
        extensionExecutor.executeVoid(stringCoordinate("bizA", "useCaseA", "scenarioA"),
                (TestDefaultExtPoint extension) -> result.append(extension.name()));

        assertEquals("biz-default", result.toString());
    }

    @Test
    void stringCoordinatePrefersExactScenarioOverFallbacks() {
        register(GXBizScenario.newDefault(), new TestDefaultExtension("global-default"));
        register(GXBizScenario.valueOf("bizA", "useCaseA"), new TestDefaultExtension("default-scenario"));
        register(GXBizScenario.valueOf("bizA"), new TestDefaultExtension("biz-default"));
        register(GXBizScenario.valueOf("bizA", "useCaseA", "scenarioA"), new TestDefaultExtension("exact"));

        String result = extensionExecutor.execute(stringCoordinate("bizA", "useCaseA", "scenarioA"),
                (TestDefaultExtPoint extension) -> extension.name());

        assertEquals("exact", result);
    }

    @Test
    void defaultScenarioRequestResolvesGlobalDefaultWithSingleLookup() {
        CountingRepository countingRepository = new CountingRepository();
        countingRepository.registerExtension(
                new GXExtensionCoordinate(TestDefaultExtPoint.class, GXBizScenario.newDefault()),
                new TestDefaultExtension("global-default"));
        ReflectionTestUtils.setField(extensionExecutor, "extensionRepository", countingRepository);

        String result = extensionExecutor.execute(TestDefaultExtPoint.class, GXBizScenario.newDefault(),
                TestDefaultExtPoint::name);

        assertEquals("global-default", result);
        assertEquals(1, countingRepository.lookups());
    }

    @Test
    void missingExtensionFailsWithNotFoundCodeAndRequestContext() {
        GXBizScenario requested = GXBizScenario.valueOf("bizA", "useCaseA", "scenarioA");

        GXExtensionException failure = assertThrows(GXExtensionException.class,
                () -> extensionExecutor.execute(TestDefaultExtPoint.class, requested, TestDefaultExtPoint::name));

        assertEquals(HttpStatus.HTTP_NOT_FOUND, failure.getCode());
        assertTrue(failure.getMessage().contains(TestDefaultExtPoint.class.getName()));
        assertTrue(failure.getMessage().contains(requested.getUniqueIdentity()));
        assertTrue(failure.getMessage().chars().allMatch(character -> character < 128));
    }

    @Test
    void missingExtensionFailsWithNotFoundCodeFromStringEntry() {
        GXExtensionCoordinate coordinate = stringCoordinate("bizA", "useCaseA", "scenarioA");

        GXExtensionException failure = assertThrows(GXExtensionException.class,
                () -> extensionExecutor.execute(coordinate, (TestDefaultExtPoint extension) -> extension.name()));

        assertEquals(HttpStatus.HTTP_NOT_FOUND, failure.getCode());
        assertTrue(failure.getMessage().contains(TestDefaultExtPoint.class.getName()));
        assertTrue(failure.getMessage().contains("bizA.useCaseA.scenarioA"));
    }

    @Test
    void nullArgumentsFailBeforeTouchingRepository() {
        CountingRepository countingRepository = new CountingRepository();
        ReflectionTestUtils.setField(extensionExecutor, "extensionRepository", countingRepository);
        GXBizScenario bizScenario = GXBizScenario.valueOf("bizA", "useCaseA", "scenarioA");

        assertThrows(NullPointerException.class,
                () -> extensionExecutor.execute(null, bizScenario, TestDefaultExtPoint::name));
        assertThrows(NullPointerException.class,
                () -> extensionExecutor.execute(TestDefaultExtPoint.class, null, TestDefaultExtPoint::name));
        assertThrows(NullPointerException.class,
                () -> extensionExecutor.executeVoid(TestDefaultExtPoint.class, null, extension -> {
                }));
        assertThrows(NullPointerException.class,
                () -> extensionExecutor.locateExtension(TestDefaultExtPoint.class, null));

        assertEquals(0, countingRepository.lookups());
    }

    private GXExtensionCoordinate stringCoordinate(String bizId, String useCase, String scenario) {
        return GXExtensionCoordinate.valueOf(TestDefaultExtPoint.class.getName(),
                GXBizScenario.valueOf(bizId, useCase, scenario).getUniqueIdentity());
    }

    private void register(GXBizScenario bizScenario, TestDefaultExtPoint extension) {
        extensionRepository.registerExtension(new GXExtensionCoordinate(TestDefaultExtPoint.class, bizScenario), extension);
    }

    private interface TestDefaultExtPoint extends GXExtensionPoint {
        String name();
    }

    private record TestDefaultExtension(String name) implements TestDefaultExtPoint {
    }

    private static class CountingRepository extends GXExtensionRepository {
        private int lookups;

        @Override
        GXExtensionPoint findExtensionOrNull(String extensionPoint, String bizScenario) {
            lookups++;
            return super.findExtensionOrNull(extensionPoint, bizScenario);
        }

        private int lookups() {
            return lookups;
        }
    }
}
