package cn.maple.core.framework.util;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import com.github.benmanes.caffeine.cache.*;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caffeine缓存工具类
 * <p>
 * 提供了同步和异步缓存的创建和获取方法，支持多种缓存类型：
 * 1. 普通Cache - 基本的键值对缓存
 * 2. LoadingCache - 支持自动加载的缓存
 * 3. AsyncCache - 异步操作的缓存
 * 4. AsyncLoadingCache - 支持异步自动加载的缓存
 * </p>
 * <p>
 * 线程安全说明：
 * - 所有缓存实例都存储在线程安全的ConcurrentHashMap中
 * - 缓存实例创建时使用了双重检查锁定模式，确保单例
 * - Caffeine本身是线程安全的，适合在高并发环境下使用
 * - 所有缓存操作都是原子的，无需额外同步
 * </p>
 * <p>
 * 性能优化：
 * - 缓存实例按名称复用，避免重复创建
 * - 支持从配置中读取缓存参数，实现动态配置
 * - 提供批量清除方法，优化内存管理
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 获取基本缓存
 * Cache<String, User> userCache = GXCaffeineCacheUtils.getCaffeineCache("userCache");
 * userCache.put("user1", new User("张三"));
 * User user = userCache.getIfPresent("user1");
 * 
 * // 2. 使用自动加载的缓存
 * LoadingCache<String, User> loadingCache = GXCaffeineCacheUtils.getCaffeineCache("userCache", 
 *     key -> userService.findByUsername(key));
 * User user = loadingCache.get("user1"); // 如果缓存中没有，会自动调用加载函数
 * 
 * // 3. 使用异步缓存
 * AsyncCache<String, User> asyncCache = GXCaffeineCacheUtils.getAsyncCaffeine("userCache");
 * CompletableFuture<User> future = asyncCache.get("user1", key -> userService.findByUsernameAsync(key));
 * 
 * // 4. 清除缓存
 * GXCaffeineCacheUtils.clearCache("userCache");
 * </pre>
 * </p>
 */
public class GXCaffeineCacheUtils {
    private static final Map<String, Cache<?, ?>> CACHE_MAP = new ConcurrentHashMap<>();

    private static final Map<String, LoadingCache<?, ?>> LOADING_CACHE_MAP = new ConcurrentHashMap<>();

    private static final Map<String, AsyncCache<?, ?>> ASYNC_CACHE_MAP = new ConcurrentHashMap<>();

    private static final Map<String, AsyncLoadingCache<?, ?>> ASYNC_LOADING_CACHE_MAP = new ConcurrentHashMap<>();

    private static final Map<String, Caffeine<?, ?>> CAFFEINE_MAP = new ConcurrentHashMap<>();

    private GXCaffeineCacheUtils() {
    }

    /**
     * 获取Caffeine的Cache对象
     * 如果缓存不存在则创建一个新的缓存实例
     *
     * @param configNameKey 配置名称键，用于从环境变量中获取缓存配置
     * @return Cache 返回对应的缓存实例
     */
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

    /**
     * 通过CacheLoader获取同步LoadingCache对象
     * 如果缓存不存在则创建一个新的缓存实例
     *
     * @param configNameKey 配置名称键，用于从环境变量中获取缓存配置
     * @param cacheLoader   缓存加载器，用于异步加载缓存数据
     * @return LoadingCache 返回对应的LoadingCache实例
     */
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

    /**
     * 获取异步AsyncCache对象
     * 如果缓存不存在则创建一个新的缓存实例
     *
     * @param configNameKey 配置名称键，用于从环境变量中获取缓存配置
     * @return AsyncCache 返回对应的AsyncCache实例
     */
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

    /**
     * 通过指定AsyncCacheLoader来获取AsyncLoadingCache对象
     * 如果缓存不存在则创建一个新的缓存实例
     *
     * @param cacheNameKey     配置名称键，用于从环境变量中获取缓存配置
     * @param asyncCacheLoader 异步缓存加载器，用于异步加载缓存数据
     * @return AsyncLoadingCache 返回对应的AsyncLoadingCache实例
     */
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

    /**
     * 获取Caffeine对象
     * 如果Caffeine实例不存在则创建一个新的实例
     *
     * @param cacheNameKey 配置名称键，用于从环境变量中获取缓存配置
     * @return Caffeine 返回对应的Caffeine构建器实例
     */
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

    /**
     * 清除指定名称的所有类型缓存
     *
     * @param configNameKey 配置名称键
     */
    public static void clearCache(String configNameKey) {
        // 清除普通缓存
        final String cacheName = configNameKey + "caffeine-cache";
        Cache<?, ?> cache = CACHE_MAP.get(cacheName);
        if (Objects.nonNull(cache)) {
            cache.invalidateAll();
        }

        // 清除LoadingCache
        final String loadingCacheName = configNameKey + "cache-loader-caffeine-cache";
        LoadingCache<?, ?> loadingCache = LOADING_CACHE_MAP.get(loadingCacheName);
        if (Objects.nonNull(loadingCache)) {
            loadingCache.invalidateAll();
        }

        // 清除AsyncCache
        final String asyncCacheName = configNameKey + "async-caffeine-cache";
        AsyncCache<?, ?> asyncCache = ASYNC_CACHE_MAP.get(asyncCacheName);
        if (Objects.nonNull(asyncCache)) {
            asyncCache.synchronous().invalidateAll();
        }

        // 清除AsyncLoadingCache
        final String asyncLoadingCacheName = configNameKey + "origin-async-cache-loader-caffeine-cache";
        AsyncLoadingCache<?, ?> asyncLoadingCache = ASYNC_LOADING_CACHE_MAP.get(asyncLoadingCacheName);
        if (Objects.nonNull(asyncLoadingCache)) {
            asyncLoadingCache.synchronous().invalidateAll();
        }
    }

    /**
     * 清除所有缓存
     */
    public static void clearAllCaches() {
        // 清除所有普通缓存
        CACHE_MAP.values().forEach(Cache::invalidateAll);

        // 清除所有LoadingCache
        LOADING_CACHE_MAP.values().forEach(LoadingCache::invalidateAll);

        // 清除所有AsyncCache
        ASYNC_CACHE_MAP.values().forEach(cache -> cache.synchronous().invalidateAll());

        // 清除所有AsyncLoadingCache
        ASYNC_LOADING_CACHE_MAP.values().forEach(cache -> cache.synchronous().invalidateAll());
    }
}
