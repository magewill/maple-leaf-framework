package cn.maple.core.framework.config.support;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    @Resource(name = "caffeineCacheManager")
    private CaffeineCacheManager caffeineCacheManager;

    @Override
    public CacheManager cacheManager() {
        return caffeineCacheManager;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    static class LoggingCacheErrorHandler extends SimpleCacheErrorHandler {
        private static final Logger log = LoggerFactory.getLogger(LoggingCacheErrorHandler.class);

        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            log.error("缓存获取操作异常-->缓存名称:{},缓存键:{}", getCacheName(cache), key, exception);
        }

        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
            log.error("缓存写入操作异常-->缓存名称:{},缓存键:{}", getCacheName(cache), key, exception);
        }

        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            log.error("缓存驱逐操作异常-->缓存名称:{},缓存键:{}", getCacheName(cache), key, exception);
        }

        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            log.error("缓存清空操作异常-->缓存名称:{}", getCacheName(cache), exception);
        }

        private String getCacheName(Cache cache) {
            return cache == null ? "unknown" : cache.getName();
        }
    }
}
