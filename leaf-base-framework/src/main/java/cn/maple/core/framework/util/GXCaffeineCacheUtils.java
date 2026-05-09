package cn.maple.core.framework.util;

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

    private static final Map<String, Object> CACHE_LOADER_MAP = new ConcurrentHashMap<>();

    private GXCaffeineCacheUtils() {
    }

    public static <K, V> Cache<K, V> getCaffeineCache(String configNameKey) {
        final String cacheName = buildCacheName(configNameKey, "caffeine-cache");
        Cache<?, ?> cache = CACHE_MAP.computeIfAbsent(cacheName, ignored -> getCaffeine(configNameKey).build());
        return cast(cache);
    }

    public static <K, V> LoadingCache<K, V> getCaffeineCache(String configNameKey, CacheLoader<K, V> cacheLoader) {
        requireLoader(cacheLoader);
        final String cacheName = buildCacheName(configNameKey, "cache-loader-caffeine-cache");
        LoadingCache<?, ?> cache = LOADING_CACHE_MAP.computeIfAbsent(cacheName, ignored -> {
            LoadingCache<K, V> loadingCache = getCaffeine(configNameKey).build(cacheLoader);
            registerLoader(cacheName, cacheLoader);
            return loadingCache;
        });
        ensureSameLoader(cacheName, cacheLoader);
        return cast(cache);
    }

    public static <K, V> AsyncCache<K, V> getAsyncCaffeine(String configNameKey) {
        final String cacheName = buildCacheName(configNameKey, "async-caffeine-cache");
        AsyncCache<?, ?> cache = ASYNC_CACHE_MAP.computeIfAbsent(cacheName, ignored -> getCaffeine(configNameKey).buildAsync());
        return cast(cache);
    }

    public static <K, V> AsyncLoadingCache<K, V> getAsyncCaffeine(String cacheNameKey, AsyncCacheLoader<K, V> asyncCacheLoader) {
        requireLoader(asyncCacheLoader);
        final String cacheName = buildCacheName(cacheNameKey, "origin-async-cache-loader-caffeine-cache");
        AsyncLoadingCache<?, ?> cache = ASYNC_LOADING_CACHE_MAP.computeIfAbsent(cacheName, ignored -> {
            AsyncLoadingCache<K, V> asyncLoadingCache = getCaffeine(cacheNameKey).buildAsync(asyncCacheLoader);
            registerLoader(cacheName, asyncCacheLoader);
            return asyncLoadingCache;
        });
        ensureSameLoader(cacheName, asyncCacheLoader);
        return cast(cache);
    }

    private static <K, V> Caffeine<K, V> getCaffeine(String cacheNameKey) {
        final String cacheName = buildCacheName(cacheNameKey, "caffeine-obj");
        Caffeine<?, ?> caffeine = CAFFEINE_MAP.computeIfAbsent(cacheName, ignored -> Caffeine.from(getSpec(cacheNameKey)));
        return cast(caffeine);
    }

    public static void clearCache(String configNameKey) {
        final String cacheName = buildCacheName(configNameKey, "caffeine-cache");
        Cache<?, ?> cache = CACHE_MAP.get(cacheName);
        if (Objects.nonNull(cache)) {
            cache.invalidateAll();
        }

        final String loadingCacheName = buildCacheName(configNameKey, "cache-loader-caffeine-cache");
        LoadingCache<?, ?> loadingCache = LOADING_CACHE_MAP.get(loadingCacheName);
        if (Objects.nonNull(loadingCache)) {
            loadingCache.invalidateAll();
        }

        final String asyncCacheName = buildCacheName(configNameKey, "async-caffeine-cache");
        AsyncCache<?, ?> asyncCache = ASYNC_CACHE_MAP.get(asyncCacheName);
        if (Objects.nonNull(asyncCache)) {
            asyncCache.synchronous().invalidateAll();
        }

        final String asyncLoadingCacheName = buildCacheName(configNameKey, "origin-async-cache-loader-caffeine-cache");
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

    public static void removeCache(String configNameKey) {
        final String cacheName = buildCacheName(configNameKey, "caffeine-cache");
        Cache<?, ?> cache = CACHE_MAP.remove(cacheName);
        if (Objects.nonNull(cache)) {
            cache.invalidateAll();
        }

        final String loadingCacheName = buildCacheName(configNameKey, "cache-loader-caffeine-cache");
        LoadingCache<?, ?> loadingCache = LOADING_CACHE_MAP.remove(loadingCacheName);
        if (Objects.nonNull(loadingCache)) {
            loadingCache.invalidateAll();
        }
        CACHE_LOADER_MAP.remove(loadingCacheName);

        final String asyncCacheName = buildCacheName(configNameKey, "async-caffeine-cache");
        AsyncCache<?, ?> asyncCache = ASYNC_CACHE_MAP.remove(asyncCacheName);
        if (Objects.nonNull(asyncCache)) {
            asyncCache.synchronous().invalidateAll();
        }

        final String asyncLoadingCacheName = buildCacheName(configNameKey, "origin-async-cache-loader-caffeine-cache");
        AsyncLoadingCache<?, ?> asyncLoadingCache = ASYNC_LOADING_CACHE_MAP.remove(asyncLoadingCacheName);
        if (Objects.nonNull(asyncLoadingCache)) {
            asyncLoadingCache.synchronous().invalidateAll();
        }
        CACHE_LOADER_MAP.remove(asyncLoadingCacheName);

        CAFFEINE_MAP.remove(buildCacheName(configNameKey, "caffeine-obj"));
    }

    public static void removeAllCaches() {
        clearAllCaches();
        CACHE_MAP.clear();
        LOADING_CACHE_MAP.clear();
        ASYNC_CACHE_MAP.clear();
        ASYNC_LOADING_CACHE_MAP.clear();
        CAFFEINE_MAP.clear();
        CACHE_LOADER_MAP.clear();
    }

    private static String getSpec(String cacheNameKey) {
        String spec = GXCommonUtils.getEnvironmentValue(cacheNameKey, String.class);
        return CharSequenceUtil.isBlank(spec) ? "maximumSize=1024" : spec;
    }

    private static String buildCacheName(String configNameKey, String suffix) {
        if (CharSequenceUtil.isBlank(configNameKey)) {
            throw new IllegalArgumentException("Cache config key must not be blank");
        }
        return configNameKey + ":" + suffix;
    }

    private static void requireLoader(Object cacheLoader) {
        if (cacheLoader == null) {
            throw new IllegalArgumentException("Cache loader must not be null");
        }
    }

    private static void registerLoader(String cacheName, Object cacheLoader) {
        Object previousLoader = CACHE_LOADER_MAP.putIfAbsent(cacheName, cacheLoader);
        if (previousLoader != null && previousLoader != cacheLoader) {
            throw new IllegalStateException("Cache already exists with a different loader: " + cacheName);
        }
    }

    private static void ensureSameLoader(String cacheName, Object cacheLoader) {
        Object previousLoader = CACHE_LOADER_MAP.get(cacheName);
        if (previousLoader != null && previousLoader != cacheLoader) {
            throw new IllegalStateException("Cache already exists with a different loader: " + cacheName);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
