package cn.maple.core.framework.config.cache;

import cn.maple.core.framework.properties.GXCaffeineCacheManagerProperties;
import cn.maple.core.framework.properties.GXCaffeineCacheProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class GXCaffeineCacheConfig {
    private static final List<String> DEFAULT_CACHE_NAMES = List.of(
            "FRAMEWORK-CACHE", "__DEFAULT__", "UNIVERSAL-CACHE",
            "BIZ-BACKEND-APP-CACHE", "BIZ-FRONTEND-APP-CACHE"
    );

    private final GXCaffeineCacheManagerProperties caffeineCacheManagerProperties;

    public GXCaffeineCacheConfig(GXCaffeineCacheManagerProperties caffeineCacheManagerProperties) {
        this.caffeineCacheManagerProperties = caffeineCacheManagerProperties;
    }

    @Bean("caffeineCacheManager")
    @Primary
    public CaffeineCacheManager caffeineCacheManager() {
        log.info("Initializing Caffeine cache manager");
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();

        Map<String, GXCaffeineCacheProperties> cacheConfig = caffeineCacheManagerProperties == null
                ? Collections.emptyMap()
                : caffeineCacheManagerProperties.getConfig();

        if (cacheConfig != null) {
            cacheConfig.forEach((name, caffeineCacheProperties) ->
                    registerCustomCache(caffeineCacheManager, name, caffeineCacheProperties));
        }

        DEFAULT_CACHE_NAMES.forEach(name -> registerDefaultCacheIfAbsent(caffeineCacheManager, name));

        caffeineCacheManager.setCacheNames(Collections.emptyList());
        log.info("Caffeine cache manager initialized");
        return caffeineCacheManager;
    }

    private void registerCustomCache(CaffeineCacheManager caffeineCacheManager, String name, GXCaffeineCacheProperties properties) {
        try {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Cache name must not be blank");
            }
            if (properties == null) {
                throw new IllegalArgumentException("Cache properties must not be null");
            }

            Caffeine<Object, Object> caffeine = buildCaffeine(name, properties);
            caffeineCacheManager.registerCustomCache(name, caffeine.build());
            log.debug("Registered custom cache: {}", name);
        } catch (Exception e) {
            log.error("Failed to register Caffeine cache: name={}, error={}", name, e.getMessage(), e);
            throw new IllegalStateException("Failed to register Caffeine cache: " + name, e);
        }
    }

    private Caffeine<Object, Object> buildCaffeine(String name, GXCaffeineCacheProperties properties) {
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder();

        Integer initialCapacity = properties.getInitialCapacity();
        if (initialCapacity != null) {
            if (initialCapacity < 0) {
                throw new IllegalArgumentException("initialCapacity must not be negative: " + name);
            }
            caffeine.initialCapacity(initialCapacity);
        }

        applyExpiry(name, "expireAfterAccess", properties.getExpireAfterAccess(),
                seconds -> caffeine.expireAfterAccess(seconds, TimeUnit.SECONDS));
        applyExpiry(name, "expireAfterWrite", properties.getExpireAfterWrite(),
                seconds -> caffeine.expireAfterWrite(seconds, TimeUnit.SECONDS));

        if (properties.getRefreshAfterWrite() != null) {
            throw new IllegalArgumentException("refreshAfterWrite requires a LoadingCache and is not supported by this CacheManager: " + name);
        }

        Long maximumSize = properties.getMaximumSize();
        if (maximumSize != null) {
            if (maximumSize < 0) {
                throw new IllegalArgumentException("maximumSize must not be negative: " + name);
            }
            caffeine.maximumSize(maximumSize);
        }
        if (properties.getMaximumWeight() != null) {
            throw new IllegalArgumentException("maximumWeight requires a weigher and is not supported; use maximumSize: " + name);
        }

        if (Boolean.TRUE.equals(properties.getRecordStats())) {
            caffeine.recordStats();
        }
        if (Boolean.TRUE.equals(properties.getSoftValues()) && Boolean.TRUE.equals(properties.getWeakValues())) {
            throw new IllegalArgumentException("softValues and weakValues are mutually exclusive: " + name);
        }
        if (Boolean.TRUE.equals(properties.getSoftValues())) {
            caffeine.softValues();
        }
        if (Boolean.TRUE.equals(properties.getWeakKeys())) {
            caffeine.weakKeys();
        }
        if (Boolean.TRUE.equals(properties.getWeakValues())) {
            caffeine.weakValues();
        }
        return caffeine;
    }

    private void registerDefaultCacheIfAbsent(CaffeineCacheManager caffeineCacheManager, String name) {
        try {
            if (caffeineCacheManager.getCacheNames().contains(name)) {
                log.debug("Default cache already configured: {}", name);
                return;
            }
            Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                    .initialCapacity(50)
                    .expireAfterAccess(86400, TimeUnit.SECONDS)
                    .maximumSize(10000)
                    .softValues()
                    .recordStats();

            caffeineCacheManager.registerCustomCache(name, caffeine.build());
            log.debug("Registered default cache: {}", name);
        } catch (Exception e) {
            log.error("Failed to register default Caffeine cache: name={}, error={}", name, e.getMessage(), e);
            throw new IllegalStateException("Failed to register default Caffeine cache: " + name, e);
        }
    }

    private void applyExpiry(String name, String propertyName, Integer seconds, ExpiryConfigurer configurer) {
        if (seconds == null) {
            return;
        }
        if (seconds < 0) {
            throw new IllegalArgumentException(propertyName + " must not be negative: " + name);
        }
        configurer.apply(seconds);
    }

    @FunctionalInterface
    private interface ExpiryConfigurer {
        void apply(long seconds);
    }
}
