package cn.maple.core.framework.util;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import com.github.benmanes.caffeine.cache.*;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class GXCaffeineCacheUtils {
    private static final Map<String, Cache<?, ?>> CACHE_MAP = new ConcurrentHashMap<>();

    private static final Map<String, LoadingCache<?, ?>> LOADING_CACHE_MAP = new ConcurrentHashMap<>();

    private static final Map<String, AsyncCache<?, ?>> ASYNC_CACHE_MAP = new ConcurrentHashMap<>();

    private static final Map<String, AsyncLoadingCache<?, ?>> ASYNC_LOADING_CACHE_MAP = new ConcurrentHashMap<>();

    private static final Map<String, Caffeine<?, ?>> CAFFEINE_MAP = new ConcurrentHashMap<>();

    private GXCaffeineCacheUtils() {
    }

    public static <K, V> Cache<K, V> getCaffeineCache(String configNameKey) {
        final String cacheName = configNameKey + "caffeine-cache";
        Cache<?, ?> cache = CACHE_MAP.get(cacheName);
        if (Objects.isNull(cache)) {
            Caffeine<K, V> caffeine = getCaffeine(configNameKey);
            cache = caffeine.build();
            CACHE_MAP.put(cacheName, cache);
        }
        return Convert.convert(new TypeReference<>() {
        }, cache);
    }

    public static <K, V> LoadingCache<K, V> getCaffeineCache(String configNameKey, CacheLoader<K, V> cacheLoader) {
        final String cacheName = configNameKey + "cache-loader-caffeine-cache";
        LoadingCache<?, ?> cache = LOADING_CACHE_MAP.get(cacheName);
        if (Objects.isNull(cache)) {
            Caffeine<K, V> caffeine = getCaffeine(configNameKey);
            cache = caffeine.build(cacheLoader);
            LOADING_CACHE_MAP.put(cacheName, cache);
        }
        return Convert.convert(new TypeReference<>() {
        }, cache);
    }

    public static <K, V> AsyncCache<K, V> getAsyncCaffeine(String configNameKey) {
        final String cacheName = configNameKey + "async-caffeine-cache";
        AsyncCache<?, ?> cache = ASYNC_CACHE_MAP.get(cacheName);
        if (Objects.isNull(cache)) {
            Caffeine<K, V> caffeine = getCaffeine(configNameKey);
            cache = caffeine.buildAsync();
            ASYNC_CACHE_MAP.put(cacheName, cache);
        }
        return Convert.convert(new TypeReference<>() {
        }, cache);
    }

    public static <K, V> AsyncLoadingCache<K, V> getAsyncCaffeine(String cacheNameKey, AsyncCacheLoader<K, V> asyncCacheLoader) {
        final String cacheName = cacheNameKey + "origin-async-cache-loader-caffeine-cache";
        AsyncLoadingCache<?, ?> cache = ASYNC_LOADING_CACHE_MAP.get(cacheName);
        if (Objects.isNull(cache)) {
            Caffeine<K, V> caffeine = getCaffeine(cacheNameKey);
            cache = caffeine.buildAsync(asyncCacheLoader);
            ASYNC_LOADING_CACHE_MAP.put(cacheName, cache);
        }
        return Convert.convert(new TypeReference<>() {
        }, cache);
    }

    private static <K, V> Caffeine<K, V> getCaffeine(String cacheNameKey) {
        String spec = GXCommonUtils.getEnvironmentValue(cacheNameKey, String.class);
        if (CharSequenceUtil.isBlank(spec)) {
            spec = "maximumSize=1024";
        }
        final String cacheName = cacheNameKey + "caffeine-obj";
        Caffeine<?, ?> caffeine = CAFFEINE_MAP.get(cacheName);
        if (Objects.isNull(caffeine)) {
            caffeine = Caffeine.from(spec);
            CAFFEINE_MAP.put(cacheName, caffeine);
        }
        return Convert.convert(new TypeReference<>() {
        }, caffeine);
    }

    public static void clearCache(String configNameKey) {
        final String cacheName = configNameKey + "caffeine-cache";
        Cache<?, ?> cache = CACHE_MAP.get(cacheName);
        if (Objects.nonNull(cache)) {
            cache.invalidateAll();
        }

        final String loadingCacheName = configNameKey + "cache-loader-caffeine-cache";
        LoadingCache<?, ?> loadingCache = LOADING_CACHE_MAP.get(loadingCacheName);
        if (Objects.nonNull(loadingCache)) {
            loadingCache.invalidateAll();
        }

        final String asyncCacheName = configNameKey + "async-caffeine-cache";
        AsyncCache<?, ?> asyncCache = ASYNC_CACHE_MAP.get(asyncCacheName);
        if (Objects.nonNull(asyncCache)) {
            asyncCache.synchronous().invalidateAll();
        }

        final String asyncLoadingCacheName = configNameKey + "origin-async-cache-loader-caffeine-cache";
        AsyncLoadingCache<?, ?> asyncLoadingCache = ASYNC_LOADING_CACHE_MAP.get(asyncLoadingCacheName);
        if (Objects.nonNull(asyncLoadingCache)) {
            asyncLoadingCache.synchronous().invalidateAll();
        }
    }

    public static void clearAllCaches() {
        CACHE_MAP.values().forEach(Cache::invalidateAll);
        LOADING_CACHE_MAP.values().forEach(LoadingCache::invalidateAll);
        ASYNC_CACHE_MAP.values().forEach(cache -> cache.synchronous().invalidateAll());
        ASYNC_LOADING_CACHE_MAP.values().forEach(cache -> cache.synchronous().invalidateAll());
    }
}
