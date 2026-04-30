package cn.maple.core.framework.config.cache;

import cn.hutool.core.collection.CollUtil;
import cn.maple.core.framework.properties.GXCaffeineCacheManagerProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class GXCaffeineCacheConfig {
    @Resource
    private GXCaffeineCacheManagerProperties caffeineCacheManagerProperties;

    @Bean("caffeineCacheManager")
    @Primary
    public CaffeineCacheManager caffeineCacheManager() {
        log.info("初始化Caffeine缓存管理器");
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();

        caffeineCacheManagerProperties.getConfig().forEach((name, caffeineCacheProperties) -> {
            try {
                Integer initialCapacity = caffeineCacheProperties.getInitialCapacity();
                Integer expireAfterAccess = caffeineCacheProperties.getExpireAfterAccess();
                Integer expireAfterWrite = caffeineCacheProperties.getExpireAfterWrite();
                Integer refreshAfterWrite = caffeineCacheProperties.getRefreshAfterWrite();
                Long maximumSize = caffeineCacheProperties.getMaximumSize();
                Long maximumWeight = caffeineCacheProperties.getMaximumWeight();
                Boolean recordStats = caffeineCacheProperties.getRecordStats();
                Boolean softValues = caffeineCacheProperties.getSoftValues();
                Boolean weakKeys = caffeineCacheProperties.getWeakKeys();
                Boolean weakValues = caffeineCacheProperties.getWeakValues();

                Caffeine<Object, Object> caffeine = Caffeine.newBuilder();

                if (initialCapacity != null) {
                    caffeine.initialCapacity(initialCapacity);
                }
                if (expireAfterAccess != null) {
                    caffeine.expireAfterAccess(expireAfterAccess, TimeUnit.SECONDS);
                }
                if (expireAfterWrite != null) {
                    caffeine.expireAfterWrite(expireAfterWrite, TimeUnit.SECONDS);
                }
                if (refreshAfterWrite != null) {
                    caffeine.refreshAfterWrite(refreshAfterWrite, TimeUnit.SECONDS);
                }
                if (maximumSize != null) {
                    caffeine.maximumSize(maximumSize);
                }
                if (maximumWeight != null) {
                    throw new IllegalArgumentException("maximumWeight配置需要配合Weigher使用，当前暂不支持，请使用maximumSize");
                }

                if (Boolean.TRUE.equals(recordStats)) {
                    caffeine.recordStats();
                }
                if (Boolean.TRUE.equals(softValues)) {
                    caffeine.softValues();
                }
                if (Boolean.TRUE.equals(weakKeys)) {
                    caffeine.weakKeys();
                }
                if (Boolean.TRUE.equals(weakValues)) {
                    caffeine.weakValues();
                }

                caffeineCacheManager.registerCustomCache(name, caffeine.build());
                log.debug("注册自定义缓存: {}", name);
            } catch (Exception e) {
                log.error("注册缓存{}时发生异常: {}", name, e.getMessage(), e);
                throw new IllegalStateException("注册Caffeine缓存[" + name + "]失败, 请检查配置是否冲突", e);
            }
        });

        List<String> defaultCacheNames = CollUtil.newArrayList(
                "FRAMEWORK-CACHE", "__DEFAULT__", "UNIVERSAL-CACHE",
                "BIZ-BACKEND-APP-CACHE", "BIZ-FRONTEND-APP-CACHE"
        );

        defaultCacheNames.forEach(name -> {
            try {
                if (caffeineCacheManager.getCacheNames().contains(name)) {
                    log.debug("缓存[{}]已在配置文件中自定义，跳过默认配置", name);
                    return;
                }
                Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                        .initialCapacity(50)
                        .expireAfterAccess(86400, TimeUnit.SECONDS) // 24小时
                        .maximumSize(10000)
                        .softValues()
                        .recordStats();

                caffeineCacheManager.registerCustomCache(name, caffeine.build());
                log.debug("注册默认缓存: {}", name);
            } catch (Exception e) {
                log.error("注册默认缓存{}时发生异常: {}", name, e.getMessage(), e);
                throw new IllegalStateException("注册默认Caffeine缓存[" + name + "]失败", e);
            }
        });

        caffeineCacheManager.setCacheNames(Collections.emptyList());
        log.info("Caffeine缓存管理器初始化完成");
        return caffeineCacheManager;
    }
}
