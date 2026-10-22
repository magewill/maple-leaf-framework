package cn.maple.core.framework.audit;

import cn.maple.core.framework.config.cache.GXCaffeineCacheConfig;
import cn.maple.core.framework.config.support.GXCachingConfigurerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.SimpleKeyGenerator;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CURRENT BEHAVIOR - flip when fixed.
 *
 * <p>Characterizes the cache wiring: no {@code KeyGenerator} is provided, therefore the
 * default generator yields the same key for equal arguments of different methods, and
 * cache names that were not declared up-front are not created.</p>
 */
class GXCacheIsolationAuditTest {
    @Test
    void frameworkCachingConfigurerProvidesNoKeyGenerator() {
        GXCachingConfigurerSupport support = new GXCachingConfigurerSupport(cacheManager());

        assertNull(support.keyGenerator());
    }

    @Test
    void defaultKeyGeneratorProducesSameKeyForDifferentMethods() throws Exception {
        SimpleKeyGenerator generator = new SimpleKeyGenerator();
        Method alpha = CacheTarget.class.getDeclaredMethod("alpha", String.class);
        Method beta = CacheTarget.class.getDeclaredMethod("beta", String.class);

        Object alphaKey = generator.generate(new Object(), alpha, "shared-argument");
        Object betaKey = generator.generate(new Object(), beta, "shared-argument");

        assertEquals("shared-argument", alphaKey);
        assertEquals(alphaKey, betaKey);
    }

    @Test
    void undeclaredCacheNameIsNotCreated() {
        CacheManager manager = cacheManager();

        assertNull(manager.getCache("audit-unknown-cache"));
        assertNotNull(manager.getCache("FRAMEWORK-CACHE"));
    }

    private CaffeineCacheManager cacheManager() {
        return new GXCaffeineCacheConfig(null).caffeineCacheManager();
    }

    static class CacheTarget {
        public String alpha(String value) {
            return value;
        }

        public String beta(String value) {
            return value;
        }
    }
}
