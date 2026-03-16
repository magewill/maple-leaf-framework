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

/**
 * 解决多CacheManager配置的支持类
 * 该类实现了CachingConfigurer接口，用于自定义Spring缓存的配置
 * 主要功能：
 * 1. 提供默认的CaffeineCacheManager作为缓存管理器
 * 2. 提供自定义的错误处理器，增强日志记录能力
 * <p>
 * 线程安全说明：
 * - CaffeineCacheManager本身是线程安全的，内部使用ConcurrentHashMap存储缓存
 * - 本配置类在应用启动时初始化，之后仅提供只读操作，不存在并发修改问题
 *
 * @author britton
 */
@Configuration
@ConditionalOnMissingBean(value = {CachingConfigurer.class})
public class GXCachingConfigurerSupport implements CachingConfigurer {

    @Resource(name = "caffeineCacheManager")
    private CaffeineCacheManager caffeineCacheManager;

    /**
     * 重写这个方法，目的是用以提供默认的cacheManager
     * 该方法在Spring缓存注解(@Cacheable等)未指定cacheManager时被调用
     * 返回配置好的CaffeineCacheManager实例，该实例已在GXCaffeineCacheConfig中完成初始化
     *
     * @return CacheManager 返回配置好的CaffeineCacheManager实例
     * @author britton@126.com
     */
    @Override
    public CacheManager cacheManager() {
        return caffeineCacheManager;
    }

    /**
     * 自定义缓存错误处理器
     * 如果cache操作出错,会记录在日志里，方便排查问题
     * 常见错误包括：反序列化异常、缓存访问超时、内存不足等
     * <p>
     * 注意：该错误处理器不会阻止异常传播，不会导致业务流程中断
     *
     * @return CacheErrorHandler 自定义的日志记录错误处理器
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    /**
     * 自定义日志记录缓存错误处理器
     * 继承SimpleCacheErrorHandler，增强其日志记录能力
     * <p>
     * 线程安全说明：
     * - 该类的实例在多线程环境下被调用，但不维护任何状态，是线程安全的
     * - logger对象是线程安全的
     * <p>
     * 内存安全说明：
     * - 该类不会缓存任何对象，不会导致内存泄漏
     * - 错误日志中包含缓存键信息，但不会记录完整的缓存值，避免敏感信息泄露和内存占用
     */
    static class LoggingCacheErrorHandler extends SimpleCacheErrorHandler {
        private static final Logger log = LoggerFactory.getLogger(LoggingCacheErrorHandler.class);

        /**
         * 处理缓存获取操作中的异常
         * 记录详细的错误日志
         *
         * @param exception 缓存操作过程中抛出的运行时异常
         * @param cache     发生异常的缓存对象
         * @param key       缓存操作的键
         */
        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            log.error("缓存获取操作异常-->缓存名称:{},缓存键:{}", getCacheName(cache), key, exception);
        }

        /**
         * 处理缓存写入操作中的异常
         * 记录详细的错误日志
         * 注意：不记录value值以避免敏感信息泄露和过多内存占用
         *
         * @param exception 缓存操作过程中抛出的运行时异常
         * @param cache     发生异常的缓存对象
         * @param key       缓存操作的键
         * @param value     尝试写入的值（日志中不记录此值）
         */
        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
            log.error("缓存写入操作异常-->缓存名称:{},缓存键:{}", getCacheName(cache), key, exception);
        }

        /**
         * 处理缓存驱逐(删除)操作中的异常
         * 记录详细的错误日志
         *
         * @param exception 缓存操作过程中抛出的运行时异常
         * @param cache     发生异常的缓存对象
         * @param key       缓存操作的键
         */
        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            log.error("缓存驱逐操作异常-->缓存名称:{},缓存键:{}", getCacheName(cache), key, exception);
        }

        /**
         * 处理缓存清空操作中的异常
         * 记录详细的错误日志
         *
         * @param exception 缓存操作过程中抛出的运行时异常
         * @param cache     发生异常的缓存对象
         */
        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            log.error("缓存清空操作异常-->缓存名称:{}", getCacheName(cache), exception);
        }

        /**
         * 获取缓存名称
         *
         * @param cache 缓存对象
         * @return 缓存名称或者 unknown
         */
        private String getCacheName(Cache cache) {
            return cache == null ? "unknown" : cache.getName();
        }
    }
}
