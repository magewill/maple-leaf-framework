package cn.maple.extension;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXExtensionExecutorBenchmarkTest {
    private static final int WARMUP_ITERATIONS = 1_000_000;
    private static final int MEASURE_ITERATIONS = 1_000_000;
    private static final int ROUNDS = 7;
    private static final long MAX_NANOS_PER_CALL = 1_000L;
    private static final int MAX_CONTROL_FACTOR = 6;
    private static final int MAX_REPOSITORY_LOOKUPS = 4;
    private static final double MAX_SCALE_RATIO = 1.2;
    private static final int LARGE_SCALE = 100;
    private static final int CONTROL_KEYS = 8;
    private static final String EXTENSION_NAME = "benchmark";
    private static final GXBizScenario EXACT_SCENARIO = GXBizScenario.valueOf("bizA", "useCaseA", "scenarioA");
    private static final GXBizScenario FALLBACK_SCENARIO = GXBizScenario.valueOf("bizB", "useCaseB", "scenarioB");

    private volatile long sink;
    private volatile ConcurrentMap<GXExtensionCoordinate, GXExtensionPoint> controlMap = new ConcurrentHashMap<>();

    @Test
    void executorLookupStaysWithinControlAndAbsoluteGates() {
        GXExtensionRepository exactRepository = new GXExtensionRepository();
        exactRepository.registerExtension(new GXExtensionCoordinate(BenchmarkExtPoint.class, EXACT_SCENARIO),
                new BenchmarkExtension(EXTENSION_NAME));
        GXExtensionExecutor exactExecutor = executorWith(exactRepository);

        GXExtensionRepository fallbackRepository = new GXExtensionRepository();
        fallbackRepository.registerExtension(
                new GXExtensionCoordinate(BenchmarkExtPoint.class, GXBizScenario.newDefault()),
                new BenchmarkExtension(EXTENSION_NAME));
        GXExtensionExecutor fallbackExecutor = executorWith(fallbackRepository);

        double bareLookupNanos = medianBareLookupNanos();
        double exactNanos = medianExecutorNanos(exactExecutor, EXACT_SCENARIO);
        double fallbackNanos = medianExecutorNanos(fallbackExecutor, FALLBACK_SCENARIO);

        assertTrue(exactNanos <= MAX_CONTROL_FACTOR * bareLookupNanos,
                () -> "exact hit too slow: exact=" + exactNanos + "ns, bareLookup=" + bareLookupNanos + "ns");
        assertTrue(fallbackNanos <= MAX_CONTROL_FACTOR * MAX_REPOSITORY_LOOKUPS * bareLookupNanos,
                () -> "fallback too slow: fallback=" + fallbackNanos + "ns, bareLookup=" + bareLookupNanos
                        + "ns, budget=" + MAX_CONTROL_FACTOR * MAX_REPOSITORY_LOOKUPS);
        assertTrue(exactNanos <= MAX_NANOS_PER_CALL,
                () -> "exact hit exceeds absolute gate: exact=" + exactNanos + "ns");
        assertTrue(fallbackNanos <= MAX_NANOS_PER_CALL,
                () -> "fallback exceeds absolute gate: fallback=" + fallbackNanos + "ns");
    }

    @Test
    void exactHitTouchesRepositoryOnceAndFallbackAtMostFourTimes() {
        CountingRepository exactRepository = new CountingRepository();
        exactRepository.registerExtension(new GXExtensionCoordinate(BenchmarkExtPoint.class, EXACT_SCENARIO),
                new BenchmarkExtension(EXTENSION_NAME));
        executorWith(exactRepository).execute(BenchmarkExtPoint.class, EXACT_SCENARIO, BenchmarkExtPoint::name);
        assertEquals(1, exactRepository.lookups());

        CountingRepository fallbackRepository = new CountingRepository();
        fallbackRepository.registerExtension(
                new GXExtensionCoordinate(BenchmarkExtPoint.class, GXBizScenario.newDefault()),
                new BenchmarkExtension(EXTENSION_NAME));
        executorWith(fallbackRepository).execute(BenchmarkExtPoint.class, FALLBACK_SCENARIO, BenchmarkExtPoint::name);
        assertEquals(4, fallbackRepository.lookups());
    }

    @Test
    void lookupCostIsIndependentOfRegisteredScale() {
        GXExtensionExecutor smallExecutor = executorWith(repositoryOf(1));
        GXExtensionExecutor largeExecutor = executorWith(repositoryOf(LARGE_SCALE));

        executorNanosPerCall(smallExecutor, EXACT_SCENARIO, WARMUP_ITERATIONS);
        executorNanosPerCall(largeExecutor, EXACT_SCENARIO, WARMUP_ITERATIONS);

        double[] smallSamples = new double[ROUNDS];
        double[] largeSamples = new double[ROUNDS];
        for (int round = 0; round < ROUNDS; round++) {
            if ((round & 1) == 0) {
                smallSamples[round] = executorNanosPerCall(smallExecutor, EXACT_SCENARIO, MEASURE_ITERATIONS);
                largeSamples[round] = executorNanosPerCall(largeExecutor, EXACT_SCENARIO, MEASURE_ITERATIONS);
            } else {
                largeSamples[round] = executorNanosPerCall(largeExecutor, EXACT_SCENARIO, MEASURE_ITERATIONS);
                smallSamples[round] = executorNanosPerCall(smallExecutor, EXACT_SCENARIO, MEASURE_ITERATIONS);
            }
        }

        double smallNanos = median(smallSamples);
        double largeNanos = median(largeSamples);
        double ratio = largeNanos / smallNanos;
        assertTrue(ratio <= MAX_SCALE_RATIO,
                () -> "lookup cost grows with scale: small=" + smallNanos + "ns, large=" + largeNanos
                        + "ns, ratio=" + ratio);
    }

    private GXExtensionRepository repositoryOf(int registeredExtensions) {
        GXExtensionRepository repository = new GXExtensionRepository();
        repository.registerExtension(new GXExtensionCoordinate(BenchmarkExtPoint.class, EXACT_SCENARIO),
                new BenchmarkExtension(EXTENSION_NAME));
        for (int index = 1; index < registeredExtensions; index++) {
            GXBizScenario scenario = GXBizScenario.valueOf("scale" + index);
            repository.registerExtension(new GXExtensionCoordinate(BenchmarkExtPoint.class, scenario),
                    new BenchmarkExtension(EXTENSION_NAME));
        }
        return repository;
    }

    private GXExtensionExecutor executorWith(GXExtensionRepository repository) {
        GXExtensionExecutor executor = new GXExtensionExecutor();
        ReflectionTestUtils.setField(executor, "extensionRepository", repository);
        return executor;
    }

    /**
     * Measures a bare {@link ConcurrentHashMap#get} on the same map type and key type the executor
     * uses. Keys are built before the loop, so this is the lower bound of any repository lookup; the
     * key set is rotated to keep the JIT from hoisting the read out of the loop.
     * <p>
     * The probe keys are equal to, but not the same instances as, the stored keys. Reusing the stored
     * key instance makes {@code equals} a reference comparison and lets the JIT collapse the lookup to
     * about 3 ns, which no caller can achieve because every lookup builds its own key. Probe keys
     * therefore force a real hash plus string equality, the cheapest reproducible bare lookup.
     */
    private double medianBareLookupNanos() {
        ConcurrentMap<GXExtensionCoordinate, GXExtensionPoint> control = new ConcurrentHashMap<>();
        GXExtensionCoordinate[] keys = new GXExtensionCoordinate[CONTROL_KEYS];
        for (int index = 0; index < CONTROL_KEYS; index++) {
            String identity = GXBizScenario.valueOf("control" + index, "useCase", "scenario").getUniqueIdentity();
            control.put(new GXExtensionCoordinate(BenchmarkExtPoint.class.getName(), identity),
                    new BenchmarkExtension(EXTENSION_NAME));
            keys[index] = new GXExtensionCoordinate(new String(BenchmarkExtPoint.class.getName()),
                    new String(identity));
        }
        controlMap = control;

        bareLookupNanosPerCall(keys, WARMUP_ITERATIONS);

        double[] samples = new double[ROUNDS];
        for (int round = 0; round < ROUNDS; round++) {
            samples[round] = bareLookupNanosPerCall(keys, MEASURE_ITERATIONS);
        }
        return median(samples);
    }

    private double bareLookupNanosPerCall(GXExtensionCoordinate[] keys, int iterations) {
        int mask = keys.length - 1;
        long start = System.nanoTime();
        long total = 0;
        for (int index = 0; index < iterations; index++) {
            if (controlMap.get(keys[index & mask]) != null) {
                total++;
            }
        }
        sink = total;
        return (System.nanoTime() - start) / (double) iterations;
    }

    private double medianExecutorNanos(GXExtensionExecutor executor, GXBizScenario scenario) {
        executorNanosPerCall(executor, scenario, WARMUP_ITERATIONS);

        double[] samples = new double[ROUNDS];
        for (int round = 0; round < ROUNDS; round++) {
            samples[round] = executorNanosPerCall(executor, scenario, MEASURE_ITERATIONS);
        }
        return median(samples);
    }

    private double executorNanosPerCall(GXExtensionExecutor executor, GXBizScenario scenario, int iterations) {
        long start = System.nanoTime();
        long total = 0;
        for (int index = 0; index < iterations; index++) {
            if (executor.locateExtension(BenchmarkExtPoint.class, scenario) != null) {
                total++;
            }
        }
        sink = total;
        return (System.nanoTime() - start) / (double) iterations;
    }

    private double median(double[] samples) {
        double[] sorted = samples.clone();
        java.util.Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private interface BenchmarkExtPoint extends GXExtensionPoint {
        String name();
    }

    private record BenchmarkExtension(String name) implements BenchmarkExtPoint {
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
