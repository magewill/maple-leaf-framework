package cn.maple.core.framework.config.support;

import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.stereotype.Component;

/**
 * 解决多CacheManager配置
 * CacheManager配置类
 *
 * @author britton
 */
@Component
@ConditionalOnMissingBean(value = {CachingConfigurer.class})
public class GXCachingConfigurerSupport implements CachingConfigurer {
    @Resource
    private CaffeineCacheManager caffeineCacheManager;

    /**
     * 重写这个方法，目的是用以提供默认的cacheManager
     *
     * @return CacheManager
     * @author britton@126.com
     */
    @Override
    public CacheManager cacheManager() {
        return caffeineCacheManager;
    }

    /**
     * 如果cache出错,会记录在日志里
     * 方便排查,比如反序列化异常
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    static class LoggingCacheErrorHandler extends SimpleCacheErrorHandler {
        private final Logger logger = LoggerFactory.getLogger(this.getClass());

        @Override
        public void handleCacheGetError(@NotNull RuntimeException exception, @NotNull Cache cache, @NotNull Object key) {
            logger.error(getError(cache, key), exception);
            super.handleCacheGetError(exception, cache, key);
        }

        @Override
        public void handleCachePutError(@NotNull RuntimeException exception, @NotNull Cache cache, @NotNull Object key, Object value) {
            logger.error(getError(cache, key), exception);
            super.handleCachePutError(exception, cache, key, value);
        }

        @Override
        public void handleCacheEvictError(@NotNull RuntimeException exception, @NotNull Cache cache, @NotNull Object key) {
            logger.error(getError(cache, key), exception);
            super.handleCacheEvictError(exception, cache, key);
        }

        @Override
        public void handleCacheClearError(@NotNull RuntimeException exception, @NotNull Cache cache) {
            final String errorMsg = String.format("cacheName:%s", cache.getName());
            logger.error(errorMsg, exception);
            super.handleCacheClearError(exception, cache);
        }

        /**
         * 格式化错误信息
         *
         * @param cache 缓存对象
         * @param key   缓存key
         * @return 错误信息
         */
        private String getError(Cache cache, Object key) {
            return String.format("cacheName:%s,cacheKey:%s", cache == null ? "unknown" : cache.getName(), key);
        }
    }
}
