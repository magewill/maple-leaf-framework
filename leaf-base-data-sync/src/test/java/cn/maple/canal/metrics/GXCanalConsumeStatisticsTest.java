package cn.maple.canal.metrics;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXCanalConsumeStatisticsTest {
    @Test
    void shouldCountAllOutcomes() {
        GXCanalConsumeStatistics statistics = new GXCanalConsumeStatistics();

        statistics.recordSuccess();
        statistics.recordSkipped();
        statistics.recordRetryableFailure();
        statistics.recordNonRetryableFailure();
        statistics.recordHandlerMiss();
        statistics.recordDeadLetter();
        statistics.recordBatchEvents(3);

        assertEquals(1, statistics.successCount());
        assertEquals(1, statistics.skippedCount());
        assertEquals(1, statistics.retryableFailureCount());
        assertEquals(1, statistics.nonRetryableFailureCount());
        assertEquals(1, statistics.handlerMissCount());
        assertEquals(1, statistics.deadLetterCount());
        assertEquals(3, statistics.batchEventsCount());
    }

    @Test
    void shouldIgnoreNonPositiveBatchSize() {
        GXCanalConsumeStatistics statistics = new GXCanalConsumeStatistics();

        statistics.recordBatchEvents(0);
        statistics.recordBatchEvents(-5);

        assertEquals(0, statistics.batchEventsCount());
    }

    @Test
    void shouldResetCounters() {
        GXCanalConsumeStatistics statistics = new GXCanalConsumeStatistics();
        statistics.recordSuccess();
        statistics.recordDeadLetter();

        statistics.reset();

        assertEquals(0, statistics.successCount());
        assertEquals(0, statistics.deadLetterCount());
    }

    @org.junit.jupiter.api.RepeatedTest(10)
    void shouldBeThreadSafeUnderConcurrentIncrements() throws Exception {
        GXCanalConsumeStatistics statistics = new GXCanalConsumeStatistics();
        int threads = 32;
        int perThread = 1000;
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int index = 0; index < threads; index++) {
                executor.execute(() -> {
                    try {
                        startGate.await();
                        for (int counter = 0; counter < perThread; counter++) {
                            statistics.recordSuccess();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            startGate.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals((long) threads * perThread, statistics.successCount());
    }
}
