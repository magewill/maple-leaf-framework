package cn.maple.core.framework.config.cache;

import cn.hutool.core.collection.CollUtil;
import cn.maple.core.framework.properties.GXCaffeineCacheManagerProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
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
    public CaffeineCacheManager caffeineCacheManager() {
        log.info("初始化Caffeine缓存管理器");
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        
        // 从配置文件加载自定义缓存配置
        caffeineCacheManagerProperties.getConfig().forEach((name, caffeineCacheProperties) -> {
            try {
                // 获取缓存配置参数
                Integer initialCapacity = caffeineCacheProperties.getInitialCapacity();
                Integer expireAfterAccess = caffeineCacheProperties.getExpireAfterAccess();
                Long maximumSize = caffeineCacheProperties.getMaximumSize();
                boolean recordStats = caffeineCacheProperties.getRecordStats();
                boolean softValues = caffeineCacheProperties.getSoftValues();
                
                // 构建Caffeine缓存实例
                Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                        .initialCapacity(initialCapacity) // 初始容量
                        .expireAfterAccess(expireAfterAccess, TimeUnit.SECONDS) // 访问后过期时间
                        .maximumSize(maximumSize); // 最大缓存条目数
                
                // 是否记录缓存统计信息
                if (recordStats) {
                    caffeine.recordStats();
                }
                
                // 是否使用软引用存储值（有助于内存敏感场景的GC）
                if (softValues) {
                    caffeine.softValues();
                }
                
                // 注册自定义缓存
                caffeineCacheManager.registerCustomCache(name, caffeine.build());
                log.debug("注册自定义缓存: {}, 初始容量: {}, 过期时间: {}秒, 最大容量: {}", 
                        name, initialCapacity, expireAfterAccess, maximumSize);
            } catch (Exception e) {
                log.error("注册缓存{}时发生异常: {}", name, e.getMessage(), e);
            }
        });

        // 配置默认的缓存实例
        List<String> defaultCacheNames = CollUtil.newArrayList(
                "FRAMEWORK-CACHE", "__DEFAULT__", "UNIVERSAL-CACHE", 
                "BIZ-BACKEND-APP-CACHE", "BIZ-FRONTEND-APP-CACHE"
        );
        
        defaultCacheNames.forEach(name -> {
            try {
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
            }
        });
        
        // 设置caffeineCacheManager.dynamic=false，禁止自动创建缓存
        // 这样可以避免缓存泄漏和内存溢出风险
        caffeineCacheManager.setCacheNames(CollUtil.newArrayList("__IGNORE-CACHE__"));
        log.info("Caffeine缓存管理器初始化完成");
        return caffeineCacheManager;
    }
}
