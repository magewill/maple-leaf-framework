package cn.maple.core.datasource.cache;

import cn.hutool.core.annotation.AnnotationUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.redisson.services.GXRedissonCacheService;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.cache.Cache;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;

@Slf4j
public class GXMybatisPlusRedissonCache implements Cache {
    private static volatile GXRedissonCacheService redissonCacheService;

    private final String id;
    private final int flushIntervalMillis;

    public GXMybatisPlusRedissonCache(String id) {
        if (CharSequenceUtil.isBlank(id)) {
            throw new IllegalArgumentException("Cache instances require a non-blank ID");
        }
        this.id = id;
        this.flushIntervalMillis = resolveFlushIntervalMillis(id);
        log.debug("Initialized MyBatis Redisson cache: id={}, ttlMillis={}", id, flushIntervalMillis);
    }

    private static GXRedissonCacheService getRedissonCacheService() {
        GXRedissonCacheService cacheService = redissonCacheService;
        if (isUsable(cacheService)) {
            return cacheService;
        }

        synchronized (GXMybatisPlusRedissonCache.class) {
            cacheService = redissonCacheService;
            if (isUsable(cacheService)) {
                return cacheService;
            }
            try {
                cacheService = GXSpringContextUtils.getBean(GXRedissonCacheService.class);
            } catch (RuntimeException e) {
                log.debug("Failed to resolve GXRedissonCacheService for MyBatis cache", e);
                cacheService = null;
            }
            redissonCacheService = isUsable(cacheService) ? cacheService : null;
            return redissonCacheService;
        }
    }

    private static boolean isUsable(GXRedissonCacheService cacheService) {
        if (cacheService == null) {
            return false;
        }
        try {
            var client = cacheService.getRedissonClient();
            return client != null && !client.isShutdown() && !client.isShuttingDown();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int resolveFlushIntervalMillis(String id) {
        try {
            Class<?> mapperClass = Class.forName(id);
            CacheNamespace cacheNamespace = AnnotationUtil.getAnnotation(mapperClass, CacheNamespace.class);
            if (cacheNamespace == null || cacheNamespace.flushInterval() <= 0) {
                return 0;
            }
            return toCacheTtlMillis(cacheNamespace.flushInterval());
        } catch (ClassNotFoundException e) {
            log.debug("Cache id is not a loadable mapper class, use non-expiring cache entries: id={}", id);
            return 0;
        } catch (RuntimeException e) {
            log.warn("Failed to resolve MyBatis cache flushInterval, use non-expiring cache entries: id={}", id, e);
            return 0;
        }
    }

    private static int toCacheTtlMillis(long flushIntervalMillis) {
        if (flushIntervalMillis > Integer.MAX_VALUE) {
            log.warn("MyBatis cache flushInterval [{}] exceeds supported max [{}], cap to max",
                    flushIntervalMillis, Integer.MAX_VALUE);
            return Integer.MAX_VALUE;
        }
        return (int) flushIntervalMillis;
    }

    private static String generateCacheKey(Object key) {
        String keyText = Objects.toString(key, "null");
        if (keyText.length() <= 64) {
            return keyText;
        }
        return DigestUtils.md5DigestAsHex(keyText.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void putObject(Object key, Object value) {
        if (value == null) {
            return;
        }

        GXRedissonCacheService cacheService = getRedissonCacheService();
        if (cacheService == null) {
            return;
        }

        String storeKey = generateCacheKey(key);
        try {
            if (flushIntervalMillis > 0) {
                cacheService.setCache(id, storeKey, value, flushIntervalMillis, TimeUnit.MILLISECONDS);
            } else {
                cacheService.setCache(id, storeKey, value);
            }
            log.trace("MyBatis cache put: id={}, key={}", id, storeKey);
        } catch (RuntimeException e) {
            log.warn("Failed to put MyBatis cache entry: id={}, key={}", id, storeKey, e);
        }
    }

    @Override
    public Object getObject(Object key) {
        GXRedissonCacheService cacheService = getRedissonCacheService();
        if (cacheService == null) {
            return null;
        }

        String storeKey = generateCacheKey(key);
        try {
            Object value = cacheService.getCache(id, storeKey);
            log.trace("MyBatis cache get: id={}, key={}, hit={}", id, storeKey, value != null);
            return value;
        } catch (RuntimeException e) {
            log.warn("Failed to get MyBatis cache entry: id={}, key={}", id, storeKey, e);
            return null;
        }
    }

    @Override
    public Object removeObject(Object key) {
        GXRedissonCacheService cacheService = getRedissonCacheService();
        if (cacheService == null) {
            return null;
        }

        String storeKey = generateCacheKey(key);
        try {
            Object removed = cacheService.deleteCache(id, storeKey);
            log.trace("MyBatis cache remove: id={}, key={}, removed={}", id, storeKey, removed != null);
            return removed;
        } catch (RuntimeException e) {
            log.warn("Failed to remove MyBatis cache entry: id={}, key={}", id, storeKey, e);
            return null;
        }
    }

    @Override
    public void clear() {
        GXRedissonCacheService cacheService = getRedissonCacheService();
        if (cacheService == null) {
            return;
        }

        try {
            cacheService.clear(id);
            log.debug("MyBatis cache cleared: id={}", id);
        } catch (RuntimeException e) {
            log.warn("Failed to clear MyBatis cache: id={}", id, e);
        }
    }

    @Override
    public int getSize() {
        GXRedissonCacheService cacheService = getRedissonCacheService();
        if (cacheService == null) {
            return 0;
        }

        try {
            Integer size = cacheService.size(id);
            return size == null ? 0 : size;
        } catch (RuntimeException e) {
            log.warn("Failed to get MyBatis cache size: id={}", id, e);
            return 0;
        }
    }

    @Override
    public ReadWriteLock getReadWriteLock() {
        GXRedissonCacheService cacheService = getRedissonCacheService();
        if (cacheService == null) {
            throw new IllegalStateException("GXRedissonCacheService is unavailable for MyBatis cache lock: " + id);
        }
        try {
            return cacheService.getReadWriteLock("mybatis:cache:lock:" + generateCacheKey(id));
        } catch (RuntimeException e) {
            log.warn("Failed to get MyBatis cache lock: id={}", id, e);
            throw e;
        }
    }
}
