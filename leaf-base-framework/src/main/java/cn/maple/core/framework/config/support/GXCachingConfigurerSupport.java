package cn.maple.core.framework.config.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnMissingBean(value = {CachingConfigurer.class})
public class GXCachingConfigurerSupport implements CachingConfigurer {
    private final CaffeineCacheManager caffeineCacheManager;
    private final CacheErrorHandler cacheErrorHandler = new LoggingCacheErrorHandler();

    public GXCachingConfigurerSupport(@Qualifier("caffeineCacheManager") CaffeineCacheManager caffeineCacheManager) {
        this.caffeineCacheManager = caffeineCacheManager;
    }

    @Override
    public CacheManager cacheManager() {
        return caffeineCacheManager;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return cacheErrorHandler;
    }

    static class LoggingCacheErrorHandler extends SimpleCacheErrorHandler {
        private static final Logger log = LoggerFactory.getLogger(LoggingCacheErrorHandler.class);

        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            log.error("Cache get failed: cacheName={}, key={}", getCacheName(cache), key, exception);
        }

        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
            log.error("Cache put failed: cacheName={}, key={}", getCacheName(cache), key, exception);
        }

        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            log.error("Cache evict failed: cacheName={}, key={}", getCacheName(cache), key, exception);
        }

        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            log.error("Cache clear failed: cacheName={}", getCacheName(cache), exception);
        }

        private String getCacheName(Cache cache) {
            return cache == null ? "unknown" : cache.getName();
        }
    }
}
