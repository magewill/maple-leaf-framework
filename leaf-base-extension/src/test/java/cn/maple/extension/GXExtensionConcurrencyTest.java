package cn.maple.extension;

import cn.maple.extension.exception.GXExtensionException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXExtensionConcurrencyTest {
    private static final int THREADS = 8;
    private static final int REGISTER_ATTEMPTS = 10_000;
    private static final int RESOLVE_ATTEMPTS = 200_000;
    private static final String EXTENSION_NAME = "concurrent";
    private static final GXBizScenario SCENARIO = GXBizScenario.valueOf("biz", "useCase", "scenario");

    @Test
    void concurrentRegistrationSucceedsExactlyOnce() throws InterruptedException {
        GXExtensionRepository repository = new GXExtensionRepository();
        GXExtensionCoordinate coordinate = new GXExtensionCoordinate(ConcurrencyExtPoint.class, SCENARIO);
        ConcurrencyExtension extension = new ConcurrencyExtension(EXTENSION_NAME);
        AtomicInteger successes = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        for (int index = 0; index < THREADS; index++) {
            Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                    for (int attempt = 0; attempt < REGISTER_ATTEMPTS; attempt++) {
                        if (repository.registerExtensionIfAbsent(coordinate, extension).isEmpty()) {
                            successes.incrementAndGet();
                        }
                    }
                } catch (Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "concurrent registration did not finish in time");
        assertNull(failure.get(), () -> "concurrent registration failed: " + failure.get());
        assertEquals(1, successes.get());
        assertEquals(1, repository.size());
    }

    @Test
    void concurrentResolutionReturnsRegisteredExtensionOnly() throws InterruptedException {
        GXExtensionRepository repository = new GXExtensionRepository();
        GXExtensionExecutor executor = new GXExtensionExecutor();
        ReflectionTestUtils.setField(executor, "extensionRepository", repository);
        repository.registerExtension(new GXExtensionCoordinate(ConcurrencyExtPoint.class, SCENARIO),
                new ConcurrencyExtension(EXTENSION_NAME));
        GXExtensionCoordinate stringCoordinate = GXExtensionCoordinate.valueOf(
                ConcurrencyExtPoint.class.getName(), SCENARIO.getUniqueIdentity());
        AtomicInteger unexpected = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        for (int index = 0; index < THREADS; index++) {
            Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                    for (int attempt = 0; attempt < RESOLVE_ATTEMPTS; attempt++) {
                        String typedName = executor.execute(ConcurrencyExtPoint.class, SCENARIO,
                                ConcurrencyExtPoint::name);
                        if (!EXTENSION_NAME.equals(typedName)) {
                            unexpected.incrementAndGet();
                        }
                        String namedName = executor.execute(stringCoordinate,
                                (ConcurrencyExtPoint extension) -> extension.name());
                        if (!EXTENSION_NAME.equals(namedName)) {
                            unexpected.incrementAndGet();
                        }
                    }
                } catch (Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "concurrent resolution did not finish in time");
        assertNull(failure.get(), () -> "concurrent resolution failed: " + failure.get());
        assertEquals(0, unexpected.get());
        assertNotNull(executor.execute(ConcurrencyExtPoint.class, SCENARIO, ConcurrencyExtPoint::name));
    }

    @Test
    void resolutionDuringRegistrationNeverReturnsWrongExtension() throws InterruptedException {
        GXExtensionRepository repository = new GXExtensionRepository();
        GXExtensionExecutor executor = new GXExtensionExecutor();
        ReflectionTestUtils.setField(executor, "extensionRepository", repository);
        ConcurrencyExtension extension = new ConcurrencyExtension(EXTENSION_NAME);
        GXExtensionCoordinate coordinate = new GXExtensionCoordinate(ConcurrencyExtPoint.class, SCENARIO);
        AtomicInteger hits = new AtomicInteger();
        AtomicInteger misses = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS + 1);

        for (int index = 0; index < THREADS; index++) {
            Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                    for (int attempt = 0; attempt < RESOLVE_ATTEMPTS; attempt++) {
                        try {
                            String name = executor.execute(ConcurrencyExtPoint.class, SCENARIO,
                                    ConcurrencyExtPoint::name);
                            if (EXTENSION_NAME.equals(name)) {
                                hits.incrementAndGet();
                            } else {
                                unexpected.incrementAndGet();
                            }
                        } catch (GXExtensionException expectedMiss) {
                            misses.incrementAndGet();
                        }
                    }
                } catch (Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                } finally {
                    done.countDown();
                }
            });
        }

        Thread.ofVirtual().start(() -> {
            try {
                start.await();
                repository.registerExtension(coordinate, extension);
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            } finally {
                done.countDown();
            }
        });

        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "concurrent registration and resolution did not finish in time");
        assertNull(failure.get(), () -> "concurrent resolution failed: " + failure.get());
        assertEquals(0, unexpected.get());
        assertEquals(THREADS * RESOLVE_ATTEMPTS, hits.get() + misses.get());
        assertTrue(hits.get() > 0, "resolution never observed the registered extension");
        assertEquals(1, repository.size());
    }

    private interface ConcurrencyExtPoint extends GXExtensionPoint {
        String name();
    }

    private record ConcurrencyExtension(String name) implements ConcurrencyExtPoint {
    }
}
