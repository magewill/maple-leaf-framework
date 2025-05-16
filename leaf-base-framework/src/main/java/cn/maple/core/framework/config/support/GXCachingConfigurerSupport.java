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
@Component
@ConditionalOnMissingBean(value = {CachingConfigurer.class})
public class GXCachingConfigurerSupport implements CachingConfigurer {
    @Resource
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
     * 注意：该错误处理器不会阻止异常传播，仅增加日志记录
     * 在生产环境中，缓存操作异常不应导致业务流程中断
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
        private final Logger logger = LoggerFactory.getLogger(this.getClass());

        /**
         * 处理缓存获取操作中的异常
         * 记录详细的错误日志并调用父类方法继续异常传播
         *
         * @param exception 缓存操作过程中抛出的运行时异常
         * @param cache     发生异常的缓存对象
         * @param key       缓存操作的键
         */
        @Override
        public void handleCacheGetError(@NotNull RuntimeException exception, @NotNull Cache cache, @NotNull Object key) {
            logger.error(getError(cache, key), exception);
            super.handleCacheGetError(exception, cache, key);
        }

        /**
         * 处理缓存写入操作中的异常
         * 记录详细的错误日志并调用父类方法继续异常传播
         * 注意：不记录value值以避免敏感信息泄露和过多内存占用
         *
         * @param exception 缓存操作过程中抛出的运行时异常
         * @param cache     发生异常的缓存对象
         * @param key       缓存操作的键
         * @param value     尝试写入的值（日志中不记录此值）
         */
        @Override
        public void handleCachePutError(@NotNull RuntimeException exception, @NotNull Cache cache, @NotNull Object key, Object value) {
            logger.error(getError(cache, key), exception);
            super.handleCachePutError(exception, cache, key, value);
        }

        /**
         * 处理缓存驱逐(删除)操作中的异常
         * 记录详细的错误日志并调用父类方法继续异常传播
         *
         * @param exception 缓存操作过程中抛出的运行时异常
         * @param cache     发生异常的缓存对象
         * @param key       缓存操作的键
         */
        @Override
        public void handleCacheEvictError(@NotNull RuntimeException exception, @NotNull Cache cache, @NotNull Object key) {
            logger.error(getError(cache, key), exception);
            super.handleCacheEvictError(exception, cache, key);
        }

        /**
         * 处理缓存清空操作中的异常
         * 记录详细的错误日志并调用父类方法继续异常传播
         *
         * @param exception 缓存操作过程中抛出的运行时异常
         * @param cache     发生异常的缓存对象
         */
        @Override
        public void handleCacheClearError(@NotNull RuntimeException exception, @NotNull Cache cache) {
            final String cacheName = cache == null ? "unknown" : cache.getName();
            final String errorMsg = String.format("cacheName:%s", cacheName);
            logger.error(errorMsg, exception);
            super.handleCacheClearError(exception, cache);
        }

        /**
         * 格式化错误信息
         * 生成包含缓存名称和缓存键的错误信息字符串
         *
         * @param cache 缓存对象，可能为null
         * @param key   缓存key，不应为null，但方法对null进行了防御性处理
         * @return 格式化后的错误信息字符串
         */
        private String getError(Cache cache, Object key) {
            String cacheName = cache == null ? "unknown" : cache.getName();
            String keyStr = key == null ? "null" : key.toString();
            return String.format("缓存操作异常-->缓存名称:%s,缓存键:%s", cacheName, keyStr);
        }
    }
}
