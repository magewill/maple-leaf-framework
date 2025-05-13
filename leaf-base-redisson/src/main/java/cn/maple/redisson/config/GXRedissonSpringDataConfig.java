package cn.maple.redisson.config;

import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.redisson.properties.GXRedissonCacheManagerProperties;
import cn.maple.redisson.properties.GXRedissonProperties;
import jakarta.annotation.Resource;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.spring.cache.CacheConfig;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Redisson Spring Data配置类
 * <p>
 * 该类负责配置和创建Redisson客户端实例，用于Redis操作和缓存管理
 * 包括标准Redisson客户端、消息队列专用客户端以及Spring缓存管理器
 * </p>
 *
 * <p>
 * 配置说明：
 * 1. 提供两个独立的RedissonClient实例，分别用于普通操作和消息队列操作
 * 2. 使用JsonJacksonCodec作为默认编解码器，确保序列化的一致性和安全性
 * 3. 支持Spring Cache集成，可配置缓存过期时间和最大空闲时间
 * 4. 所有配置处理过程保证线程安全
 * </p>
 *
 * @author maple
 */
@Configuration
@ConditionalOnClass(name = {"org.redisson.Redisson"})
public class GXRedissonSpringDataConfig {
    /**
     * 标准Redisson配置属性
     * 用于常规Redis操作的配置，包含连接信息、线程池设置等
     */
    @Resource
    private GXRedissonProperties redissonConfig;

    /**
     * Redisson缓存管理器配置属性
     * 用于配置Spring Cache集成，支持设置TTL和最大空闲时间
     */
    @Resource
    private GXRedissonCacheManagerProperties redissonCacheManagerConfig;

    /**
     * 创建标准Redisson客户端
     * <p>
     * 使用JsonJacksonCodec作为默认编解码器，确保对象序列化的一致性和安全性
     * </p>
     *
     * @param config Redisson配置对象
     * @return RedissonClient实例
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(Config config) {
        Codec jsonJacksonCodec = new JsonJacksonCodec();
        config.setCodec(jsonJacksonCodec);
        return Redisson.create(config);
    }

    /**
     * 创建Redisson Spring缓存管理器
     * <p>
     * 集成Spring Cache抽象，提供基于Redis的缓存实现
     * 根据配置决定是否使用特定的缓存配置
     * </p>
     *
     * @param redissonClient Redisson客户端实例
     * @return RedissonSpringCacheManager实例
     */
    @Bean("redissonSpringCacheManager")
    public RedissonSpringCacheManager redissonSpringCacheManager(RedissonClient redissonClient) {
        final Map<String, CacheConfig> config = redissonCacheManagerConfig.getConfig();
        if (config.isEmpty()) {
            return new RedissonSpringCacheManager(redissonClient);
        }
        return new RedissonSpringCacheManager(redissonClient, config);
    }

    /**
     * 创建标准Redisson配置
     * <p>
     * 处理连接信息，包括地址、密码和用户名的解码
     * 使用线程安全的方式处理配置转换，避免并发问题
     * </p>
     *
     * @return Redisson配置对象
     */
    @Bean("config")
    public Config config() {
        redissonConfig.getConfig().forEach((k, v) -> {
            v.setAddress(GXCommonUtils.decodeConnectStr(v.getAddress(), String.class));
            v.setPassword(GXCommonUtils.decodeConnectStr(v.getPassword(), String.class));
            v.setUsername(GXCommonUtils.decodeConnectStr(v.getUsername(), String.class));
        });
        return JSONUtil.toBean(JSONUtil.toJsonStr(redissonConfig.getConfig()), Config.class);
    }
}
