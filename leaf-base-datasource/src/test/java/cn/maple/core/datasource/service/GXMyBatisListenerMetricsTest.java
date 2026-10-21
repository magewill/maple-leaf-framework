package cn.maple.core.datasource.service;

import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerMetrics;
import cn.maple.core.datasource.service.impl.GXMyBatisListenerMetricsServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXMyBatisListenerMetricsTest {
    private final GXMyBatisListenerMetricsService metricsService = new GXMyBatisListenerMetricsServiceImpl();

    @Test
    void countersAreMonotonicAndDistinguishable() {
        GXMyBatisListenerMetrics.Snapshot before = metricsService.snapshot();

        GXMyBatisListenerMetrics.recordDelivered();
        GXMyBatisListenerMetrics.recordFailed();
        GXMyBatisListenerMetrics.recordRejected();
        GXMyBatisListenerMetrics.recordCancelled();
        GXMyBatisListenerMetrics.recordDegraded();
        GXMyBatisListenerMetrics.recordSkipped();

        GXMyBatisListenerMetrics.Snapshot after = metricsService.snapshot();
        assertTrue(after.delivered() > before.delivered());
        assertTrue(after.failed() > before.failed());
        assertTrue(after.rejected() > before.rejected());
        assertTrue(after.cancelled() > before.cancelled());
        assertTrue(after.degraded() > before.degraded());
        assertTrue(after.skipped() > before.skipped());
    }

    @Test
    void activeTaskCounterNeverGoesBelowZero() {
        GXMyBatisListenerMetrics.taskFinished();
        GXMyBatisListenerMetrics.taskFinished();

        GXMyBatisListenerMetrics.Snapshot snapshot = metricsService.snapshot();
        assertTrue(snapshot.activeTasks() >= 0);
        assertEquals(0, snapshot.activeTasks());
    }
}
