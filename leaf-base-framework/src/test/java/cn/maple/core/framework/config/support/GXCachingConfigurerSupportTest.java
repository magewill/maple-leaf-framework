package cn.maple.core.framework.config.support;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;

class GXCachingConfigurerSupportTest {
    @Test
    void cacheManagerReturnsConfiguredCaffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        GXCachingConfigurerSupport configurer = new GXCachingConfigurerSupport(cacheManager);

        CacheManager result = configurer.cacheManager();

        assertSame(cacheManager, result);
    }

    @Test
    void errorHandlerIsReusedAndSwallowsCacheErrors() {
        GXCachingConfigurerSupport configurer = new GXCachingConfigurerSupport(new CaffeineCacheManager());

        CacheErrorHandler errorHandler = configurer.errorHandler();

        assertSame(errorHandler, configurer.errorHandler());
        assertDoesNotThrow(() -> errorHandler.handleCacheGetError(new RuntimeException("get"), null, "key"));
        assertDoesNotThrow(() -> errorHandler.handleCachePutError(new RuntimeException("put"), null, "key", "value"));
        assertDoesNotThrow(() -> errorHandler.handleCacheEvictError(new RuntimeException("evict"), null, "key"));
        assertDoesNotThrow(() -> errorHandler.handleCacheClearError(new RuntimeException("clear"), null));
    }
}
