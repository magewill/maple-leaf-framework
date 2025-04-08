package cn.maple.redisson.config;

import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.redisson.properties.GXRedissonCacheManagerProperties;
import cn.maple.redisson.properties.GXRedissonMQProperties;
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

@Configuration
@ConditionalOnClass(name = {"org.redisson.Redisson"})
public class GXRedissonSpringDataConfig {
    @Resource
    private GXRedissonProperties redissonConfig;

    @Resource
    private GXRedissonMQProperties redissonMQConfig;

    @Resource
    private GXRedissonCacheManagerProperties redissonCacheManagerConfig;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(Config config) {
        Codec jsonJacksonCodec = new JsonJacksonCodec();
        config.setCodec(jsonJacksonCodec);
        return Redisson.create(config);
    }

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonMQClient(Config mqConfig) {
        Codec jsonJacksonCodec = new JsonJacksonCodec();
        mqConfig.setCodec(jsonJacksonCodec);
        return Redisson.create(mqConfig);
    }

    @Bean("redissonSpringCacheManager")
    public RedissonSpringCacheManager redissonSpringCacheManager(RedissonClient redissonClient) {
        final Map<String, CacheConfig> config = redissonCacheManagerConfig.getConfig();
        if (config.isEmpty()) {
            return new RedissonSpringCacheManager(redissonClient);
        }
        return new RedissonSpringCacheManager(redissonClient, config);
    }

    @Bean("config")
    public Config config() {
        redissonConfig.getConfig().forEach((k, v) -> {
            v.setAddress(GXCommonUtils.decodeConnectStr(v.getAddress(), String.class));
            v.setPassword(GXCommonUtils.decodeConnectStr(v.getPassword(), String.class));
            v.setUsername(GXCommonUtils.decodeConnectStr(v.getUsername(), String.class));
        });
        Config config = JSONUtil.toBean(JSONUtil.toJsonStr(redissonConfig.getConfig()), Config.class);
        // 设置 Netty 线程数 推荐：CPU 核心数 * 2
        config.setNettyThreads(Runtime.getRuntime().availableProcessors() * 2);
        // 或者设置一个固定的数量
        // config.setNettyThreads(16);
        return config;
    }

    @Bean("mqConfig")
    public Config mqConfig() {
        redissonMQConfig.getConfig().forEach((k, v) -> {
            v.setAddress(GXCommonUtils.decodeConnectStr(v.getAddress(), String.class));
            v.setPassword(GXCommonUtils.decodeConnectStr(v.getPassword(), String.class));
            v.setUsername(GXCommonUtils.decodeConnectStr(v.getUsername(), String.class));
        });
        Config config = JSONUtil.toBean(JSONUtil.toJsonStr(redissonMQConfig.getConfig()), Config.class);
        // 设置 Netty 线程数 推荐：CPU 核心数 * 2
        config.setNettyThreads(Runtime.getRuntime().availableProcessors() * 2);
        // 或者设置一个固定的数量
        // config.setNettyThreads(16);
        return config;
    }
}
