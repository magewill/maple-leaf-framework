package cn.maple.extension;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXExtensionExecutorBenchmarkTest {
    private static final int WARMUP_ITERATIONS = 200_000;
    private static final int MEASURE_ITERATIONS = 500_000;
    private static final int ROUNDS = 5;
    private static final long MAX_NANOS_PER_CALL = 1_000L;
    private static final int MAX_CONTROL_FACTOR = 6;
    private static final double MAX_SCALE_RATIO = 1.2;
    private static final int LARGE_SCALE = 100;
    private static final int CONTROL_KEYS = 8;
    private static final String EXTENSION_NAME = "benchmark";
    private static final GXBizScenario EXACT_SCENARIO = GXBizScenario.valueOf("bizA", "useCaseA", "scenarioA");
    private static final GXBizScenario FALLBACK_SCENARIO = GXBizScenario.valueOf("bizB", "useCaseB", "scenarioB");

    private volatile long sink;

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

        long bareLookupNanos = medianBareLookupNanos();
        long exactNanos = medianExecutorNanos(exactExecutor, EXACT_SCENARIO);
        long fallbackNanos = medianExecutorNanos(fallbackExecutor, FALLBACK_SCENARIO);

        assertTrue(exactNanos <= MAX_CONTROL_FACTOR * bareLookupNanos,
                () -> "exact hit too slow: exact=" + exactNanos + "ns, bareLookup=" + bareLookupNanos + "ns");
        assertTrue(fallbackNanos <= MAX_CONTROL_FACTOR * bareLookupNanos,
                () -> "fallback too slow: fallback=" + fallbackNanos + "ns, bareLookup=" + bareLookupNanos + "ns");
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

        long smallNanos = medianExecutorNanos(smallExecutor, EXACT_SCENARIO);
        long largeNanos = medianExecutorNanos(largeExecutor, EXACT_SCENARIO);

        double ratio = (double) largeNanos / smallNanos;
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
     * Measures the cheapest lookup a caller of the string based repository can perform: build an
     * equivalent coordinate and read it from a {@link ConcurrentHashMap}. Building the key inside
     * the loop keeps the comparator honest - the executor cannot query the repository without
     * constructing a coordinate, so a key-only {@code get} would measure an operation it can never
     * perform. The key set is rotated to keep the JIT from hoisting the lookup out of the loop.
     */
    private long medianBareLookupNanos() {
        ConcurrentMap<GXExtensionCoordinate, GXExtensionPoint> control = new ConcurrentHashMap<>();
        String[] identities = new String[CONTROL_KEYS];
        for (int index = 0; index < CONTROL_KEYS; index++) {
            identities[index] = GXBizScenario.valueOf("control" + index, "useCase", "scenario").getUniqueIdentity();
            control.put(new GXExtensionCoordinate(BenchmarkExtPoint.class.getName(), identities[index]),
                    new BenchmarkExtension(EXTENSION_NAME));
        }

        bareLookupNanosPerCall(control, identities, WARMUP_ITERATIONS);

        long[] samples = new long[ROUNDS];
        for (int round = 0; round < ROUNDS; round++) {
            samples[round] = bareLookupNanosPerCall(control, identities, MEASURE_ITERATIONS);
        }
        return median(samples);
    }

    private long bareLookupNanosPerCall(ConcurrentMap<GXExtensionCoordinate, GXExtensionPoint> control,
                                        String[] identities, int iterations) {
        int mask = identities.length - 1;
        long start = System.nanoTime();
        long total = 0;
        for (int index = 0; index < iterations; index++) {
            GXExtensionCoordinate key = new GXExtensionCoordinate(BenchmarkExtPoint.class.getName(),
                    identities[index & mask]);
            if (control.get(key) != null) {
                total++;
            }
        }
        sink = total;
        return (System.nanoTime() - start) / iterations;
    }

    private long medianExecutorNanos(GXExtensionExecutor executor, GXBizScenario scenario) {
        executorNanosPerCall(executor, scenario, WARMUP_ITERATIONS);

        long[] samples = new long[ROUNDS];
        for (int round = 0; round < ROUNDS; round++) {
            samples[round] = executorNanosPerCall(executor, scenario, MEASURE_ITERATIONS);
        }
        return median(samples);
    }

    private long executorNanosPerCall(GXExtensionExecutor executor, GXBizScenario scenario, int iterations) {
        long start = System.nanoTime();
        long total = 0;
        for (int index = 0; index < iterations; index++) {
            if (executor.locateExtension(BenchmarkExtPoint.class, scenario) != null) {
                total++;
            }
        }
        sink = total;
        return (System.nanoTime() - start) / iterations;
    }

    private long median(long[] samples) {
        long[] sorted = samples.clone();
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
        public Optional<GXExtensionPoint> findExtension(String extensionPoint, String bizScenario) {
            lookups++;
            return super.findExtension(extensionPoint, bizScenario);
        }

        private int lookups() {
            return lookups;
        }
    }
}
