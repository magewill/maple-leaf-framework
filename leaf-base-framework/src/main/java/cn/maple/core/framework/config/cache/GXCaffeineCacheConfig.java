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

/**
 * Caffeine缓存配置类
 * <p>
 * 提供基于Caffeine的本地缓存管理器配置
 * Caffeine是一个高性能的Java缓存库，提供接近最佳的命中率
 * 该配置类是线程安全的，Caffeine本身保证了缓存操作的线程安全性
 * </p>
 *
 * @author maple
 */

@Component
@Slf4j
public class GXCaffeineCacheConfig {
    @Resource
    private GXCaffeineCacheManagerProperties caffeineCacheManagerProperties;

    /**
     * 配置Caffeine缓存管理器
     * <p>
     * 根据配置文件创建并配置CaffeineCacheManager
     * 支持自定义缓存配置和默认缓存配置
     * Caffeine缓存是线程安全的，适合高并发环境使用
     * </p>
     *
     * @return 配置好的CaffeineCacheManager实例
     */
    @Bean("caffeineCacheManager")
    @Primary
    public CaffeineCacheManager caffeineCacheManager() {
        log.info("初始化Caffeine缓存管理器");
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();

        // 从配置文件加载自定义缓存配置
        caffeineCacheManagerProperties.getConfig().forEach((name, caffeineCacheProperties) -> {
            try {
                // 获取缓存配置参数
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

                // 构建Caffeine缓存实例
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

                // 引用相关与统计信息
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

                // 注册自定义缓存
                caffeineCacheManager.registerCustomCache(name, caffeine.build());
                log.debug("注册自定义缓存: {}", name);
            } catch (Exception e) {
                log.error("注册缓存{}时发生异常: {}", name, e.getMessage(), e);
                throw new IllegalStateException("注册Caffeine缓存[" + name + "]失败, 请检查配置是否冲突", e);
            }
        });

        // 配置默认的缓存实例
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
                // 为默认缓存配置合理的参数
                // - 初始容量50，避免频繁扩容
                // - 访问后24小时过期，避免长期占用内存
                // - 最大容量10000，防止内存溢出
                // - 使用软引用，在内存压力大时允许GC回收
                // - 记录统计信息，便于监控和调优
                Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                        .initialCapacity(50)
                        .expireAfterAccess(86400, TimeUnit.SECONDS) // 24小时
                        .maximumSize(10000)
                        .softValues() // 内存敏感
                        .recordStats(); // 记录统计信息

                caffeineCacheManager.registerCustomCache(name, caffeine.build());
                log.debug("注册默认缓存: {}", name);
            } catch (Exception e) {
                log.error("注册默认缓存{}时发生异常: {}", name, e.getMessage(), e);
                throw new IllegalStateException("注册默认Caffeine缓存[" + name + "]失败", e);
            }
        });

        // 设置caffeineCacheManager.dynamic=false，禁止自动创建缓存
        // 这样可以避免缓存泄漏和内存溢出风险
        caffeineCacheManager.setCacheNames(Collections.emptyList());
        log.info("Caffeine缓存管理器初始化完成");
        return caffeineCacheManager;
    }
}
