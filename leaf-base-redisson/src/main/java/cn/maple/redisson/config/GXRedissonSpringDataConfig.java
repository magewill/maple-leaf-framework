package cn.maple.redisson.config;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXLoggerUtils;
import cn.maple.redisson.properties.GXRedissonCacheManagerProperties;
import cn.maple.redisson.properties.GXRedissonConnectProperties;
import cn.maple.redisson.properties.GXRedissonProperties;
import jakarta.annotation.Resource;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.redisson.spring.cache.CacheConfig;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Objects;

/**
 * Standard Redisson and Spring Cache configuration.
 */
@Configuration
@ConditionalOnClass(name = {"org.redisson.Redisson"})
public class GXRedissonSpringDataConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXRedissonSpringDataConfig.class);

    @Resource
    private GXRedissonProperties redissonConfig;

    @Resource
    private GXRedissonCacheManagerProperties redissonCacheManagerConfig;

    @Bean(name = "redissonClient", destroyMethod = "shutdown")
    public RedissonClient redissonClient(@Qualifier("config") Config config) {
        Codec jsonJacksonCodec = new JsonJacksonCodec();
        config.setCodec(jsonJacksonCodec);

        GXLoggerUtils.logInfo(LOGGER, "Creating Redisson client...");
        RedissonClient client = Redisson.create(config);
        GXLoggerUtils.logInfo(LOGGER, "Redisson client created");
        return client;
    }

    @Bean("redissonSpringCacheManager")
    public RedissonSpringCacheManager redissonSpringCacheManager(@Qualifier("redissonClient") RedissonClient redissonClient) {
        Map<String, CacheConfig> cacheConfig = redissonCacheManagerConfig == null
                || redissonCacheManagerConfig.getConfig() == null
                ? Map.of()
                : Map.copyOf(redissonCacheManagerConfig.getConfig());
        return cacheConfig.isEmpty()
                ? new RedissonSpringCacheManager(redissonClient)
                : new RedissonSpringCacheManager(redissonClient, cacheConfig);
    }

    @Bean("config")
    public Config config() {
        Map<String, GXRedissonConnectProperties> sourceConfig = redissonConfig.getConfig();
        if (sourceConfig == null || sourceConfig.isEmpty()) {
            throw new IllegalStateException("Redisson config must not be empty");
        }

        Config config = new Config();
        boolean serverConfigured = false;
        for (Map.Entry<String, GXRedissonConnectProperties> entry : sourceConfig.entrySet()) {
            if (applyConnectionConfig(config, entry.getKey(), entry.getValue())) {
                serverConfigured = true;
                break;
            }
        }
        if (!serverConfigured) {
            throw new IllegalStateException("Redisson config does not contain a valid server address");
        }
        applyThreadConfig(config, sourceConfig);
        return config;
    }

    private static boolean applyConnectionConfig(Config config, String key, GXRedissonConnectProperties properties) {
        if (properties == null) {
            GXLoggerUtils.logWarn(LOGGER, "Redisson config [{}] is null, skipped", key);
            return false;
        }

        String address = decode(properties.getAddress());
        if (CharSequenceUtil.isBlank(address)) {
            GXLoggerUtils.logWarn(LOGGER, "Redisson config [{}] address is blank, skipped", key);
            return false;
        }

        String[] addresses = splitAddresses(address);
        if (isClusterConfig(key, addresses)) {
            ClusterServersConfig clusterConfig = config.useClusterServers();
            for (String nodeAddress : addresses) {
                clusterConfig.addNodeAddress(nodeAddress);
            }
            clusterConfig.setSlaveConnectionMinimumIdleSize(
                    Objects.requireNonNullElse(properties.getSlaveConnectionMinimumIdleSize(), 2));
            applyClusterAuth(clusterConfig, properties);
            return true;
        }

        SingleServerConfig singleConfig = config.useSingleServer()
                .setAddress(addresses[0])
                .setDatabase(Objects.requireNonNullElse(properties.getDatabase(), 0))
                .setConnectionMinimumIdleSize(Objects.requireNonNullElse(properties.getConnectionMinimumIdleSize(), 2));
        applySingleAuth(singleConfig, properties);
        return true;
    }

    private static void applyThreadConfig(Config config, Map<String, GXRedissonConnectProperties> sourceConfig) {
        int threads = sourceConfig.values().stream()
                .filter(Objects::nonNull)
                .map(GXRedissonConnectProperties::getThreads)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(2);
        int nettyThreads = sourceConfig.values().stream()
                .filter(Objects::nonNull)
                .map(GXRedissonConnectProperties::getNettyThreads)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(4);
        config.setThreads(threads);
        config.setNettyThreads(nettyThreads);
    }

    private static void applySingleAuth(SingleServerConfig config, GXRedissonConnectProperties properties) {
        String password = decode(properties.getPassword());
        if (CharSequenceUtil.isNotBlank(password)) {
            config.setPassword(password);
        }
        String username = decode(properties.getUsername());
        if (CharSequenceUtil.isNotBlank(username)) {
            config.setUsername(username);
        }
    }

    private static void applyClusterAuth(ClusterServersConfig config, GXRedissonConnectProperties properties) {
        String password = decode(properties.getPassword());
        if (CharSequenceUtil.isNotBlank(password)) {
            config.setPassword(password);
        }
        String username = decode(properties.getUsername());
        if (CharSequenceUtil.isNotBlank(username)) {
            config.setUsername(username);
        }
    }

    private static String[] splitAddresses(String address) {
        return java.util.Arrays.stream(address.split(","))
                .map(String::trim)
                .filter(CharSequenceUtil::isNotBlank)
                .toArray(String[]::new);
    }

    private static boolean isClusterConfig(String key, String[] addresses) {
        return addresses.length > 1 || CharSequenceUtil.containsIgnoreCase(key, "cluster");
    }

    private static String decode(String value) {
        return GXCommonUtils.decodeConnectStr(value, String.class);
    }
}
