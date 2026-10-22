package cn.maple.redisson.audit;

import cn.maple.redisson.config.GXRedissonSpringDataConfig;
import cn.maple.redisson.properties.GXRedissonConnectProperties;
import cn.maple.redisson.properties.GXRedissonProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.config.Config;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F-13: connection address parsing and cluster detection boundaries.
 */
class GXRedissonConnectConfigAuditTest {

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": separator-only address fails with an array index error")
    void separatorOnlyAddressFailsWithArrayIndexError() {
        GXRedissonSpringDataConfig springDataConfig = new GXRedissonSpringDataConfig();
        GXRedissonProperties properties = mock(GXRedissonProperties.class);
        GXRedissonConnectProperties connectProperties = new GXRedissonConnectProperties();
        connectProperties.setAddress(",");
        when(properties.getConfig()).thenReturn(Map.of("single", connectProperties));
        GXRedissonAuditTestSupport.setField(springDataConfig, "redissonConfig", properties);

        // CURRENT BEHAVIOR: splitAddresses drops the blank segments, and the empty
        // array is then indexed with [0], so the failure surfaces as an array index
        // error instead of a diagnosable configuration error.
        assertThrows(ArrayIndexOutOfBoundsException.class, springDataConfig::config);
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": cluster mode is chosen by the configuration key name")
    void clusterModeIsChosenByKeyName() {
        GXRedissonSpringDataConfig springDataConfig = new GXRedissonSpringDataConfig();
        GXRedissonProperties properties = mock(GXRedissonProperties.class);
        GXRedissonConnectProperties connectProperties = new GXRedissonConnectProperties();
        connectProperties.setAddress("redis://127.0.0.1:6379");
        when(properties.getConfig()).thenReturn(Map.of("redis-cluster", connectProperties));
        GXRedissonAuditTestSupport.setField(springDataConfig, "redissonConfig", properties);

        Config config = springDataConfig.config();

        // CURRENT BEHAVIOR: a single address is promoted to cluster mode purely
        // because the key contains "cluster".
        assertTrue(config.isClusterConfig());
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": single address with a neutral key stays single mode")
    void singleAddressWithNeutralKeyStaysSingleMode() {
        GXRedissonSpringDataConfig springDataConfig = new GXRedissonSpringDataConfig();
        GXRedissonProperties properties = mock(GXRedissonProperties.class);
        GXRedissonConnectProperties connectProperties = new GXRedissonConnectProperties();
        connectProperties.setAddress("redis://127.0.0.1:6379");
        when(properties.getConfig()).thenReturn(Map.of("single", connectProperties));
        GXRedissonAuditTestSupport.setField(springDataConfig, "redissonConfig", properties);

        Config config = springDataConfig.config();

        assertFalse(config.isClusterConfig());
        assertTrue(config.isSingleConfig());
    }
}
