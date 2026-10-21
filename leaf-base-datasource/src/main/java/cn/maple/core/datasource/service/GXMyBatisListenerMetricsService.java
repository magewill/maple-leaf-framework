package cn.maple.core.datasource.service;

import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerMetrics;

/**
 * Read-only access to MyBatis write-operation listener delivery metrics.
 */
public interface GXMyBatisListenerMetricsService {
    /**
     * @return an immutable snapshot of the current counters
     */
    GXMyBatisListenerMetrics.Snapshot snapshot();
}
