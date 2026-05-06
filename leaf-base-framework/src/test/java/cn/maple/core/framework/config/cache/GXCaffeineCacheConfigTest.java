package cn.maple.core.framework.config.cache;

import cn.maple.core.framework.properties.GXCaffeineCacheManagerProperties;
import cn.maple.core.framework.properties.GXCaffeineCacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXCaffeineCacheConfigTest {
    @Test
    void caffeineCacheManagerRegistersDefaultCachesWhenConfigIsEmpty() {
        GXCaffeineCacheManagerProperties properties = new GXCaffeineCacheManagerProperties();
        GXCaffeineCacheConfig config = new GXCaffeineCacheConfig(properties);

        CaffeineCacheManager cacheManager = config.caffeineCacheManager();

        assertNotNull(cacheManager.getCache("FRAMEWORK-CACHE"));
        assertNotNull(cacheManager.getCache("__DEFAULT__"));
    }

    @Test
    void caffeineCacheManagerRegistersCustomCaches() {
        GXCaffeineCacheManagerProperties properties = new GXCaffeineCacheManagerProperties();
        GXCaffeineCacheProperties cacheProperties = new GXCaffeineCacheProperties();
        cacheProperties.setInitialCapacity(8);
        cacheProperties.setMaximumSize(16L);
        cacheProperties.setExpireAfterWrite(60);
        cacheProperties.setRecordStats(true);
        Map<String, GXCaffeineCacheProperties> cacheConfig = new LinkedHashMap<>();
        cacheConfig.put("custom", cacheProperties);
        properties.setConfig(cacheConfig);
        GXCaffeineCacheConfig config = new GXCaffeineCacheConfig(properties);

        CaffeineCacheManager cacheManager = config.caffeineCacheManager();
        Cache customCache = cacheManager.getCache("custom");

        assertNotNull(customCache);
        assertTrue(cacheManager.getCacheNames().contains("custom"));
    }

    @Test
    void caffeineCacheManagerRejectsUnsupportedRefreshAfterWrite() {
        GXCaffeineCacheManagerProperties properties = new GXCaffeineCacheManagerProperties();
        GXCaffeineCacheProperties cacheProperties = new GXCaffeineCacheProperties();
        cacheProperties.setRefreshAfterWrite(60);
        properties.setConfig(Map.of("custom", cacheProperties));
        GXCaffeineCacheConfig config = new GXCaffeineCacheConfig(properties);

        assertThrows(IllegalStateException.class, config::caffeineCacheManager);
    }

    @Test
    void caffeineCacheManagerRejectsInvalidCacheSettings() {
        GXCaffeineCacheProperties cacheProperties = new GXCaffeineCacheProperties();
        cacheProperties.setMaximumSize(-1L);
        GXCaffeineCacheManagerProperties properties = new GXCaffeineCacheManagerProperties();
        properties.setConfig(Map.of("custom", cacheProperties));

        assertThrows(IllegalStateException.class, () -> new GXCaffeineCacheConfig(properties).caffeineCacheManager());
    }
}
