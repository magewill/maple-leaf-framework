package cn.maple.core.datasource.service.impl;

import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerMetrics;
import cn.maple.core.datasource.service.GXMyBatisListenerMetricsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default metrics service backed by the process-wide listener counters.
 */
@Service
@ConditionalOnProperty(name = "maple.framework.enable.mybatis-listener", havingValue = "true", matchIfMissing = true)
public class GXMyBatisListenerMetricsServiceImpl implements GXMyBatisListenerMetricsService {
    @Override
    public GXMyBatisListenerMetrics.Snapshot snapshot() {
        return GXMyBatisListenerMetrics.snapshot();
    }
}
