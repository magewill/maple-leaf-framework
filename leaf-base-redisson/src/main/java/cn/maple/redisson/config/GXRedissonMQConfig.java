package cn.maple.redisson.config;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXLoggerUtils;
import cn.maple.redisson.properties.GXRedissonConnectProperties;
import cn.maple.redisson.properties.GXRedissonMQProperties;
import jakarta.annotation.Resource;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Objects;

/**
 * Redisson configuration for reliable-topic and delayed-queue operations.
 */
@Configuration
@ConditionalOnClass(name = {"org.redisson.Redisson"})
@ConditionalOnExpression("${maple.framework.mq.redisson.enable:false}")
public class GXRedissonMQConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXRedissonMQConfig.class);

    @Resource
    private GXRedissonMQProperties redissonMQConfig;

    @Bean("mqConfig")
    public Config mqConfig() {
        Map<String, GXRedissonConnectProperties> sourceConfig = redissonMQConfig.getConfig();
        if (sourceConfig == null || sourceConfig.isEmpty()) {
            throw new IllegalStateException("Redisson MQ config must not be empty");
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
            throw new IllegalStateException("Redisson MQ config does not contain a valid server address");
        }
        applyThreadConfig(config, sourceConfig);
        return config;
    }

    @Bean(name = "redissonMQClient", destroyMethod = "shutdown")
    public RedissonClient redissonMQClient(@Qualifier("mqConfig") Config mqConfig) {
        Codec jsonJacksonCodec = new JsonJacksonCodec();
        mqConfig.setCodec(jsonJacksonCodec);

        GXLoggerUtils.logInfo(LOGGER, "Creating Redisson MQ client...");
        RedissonClient client = Redisson.create(mqConfig);
        GXLoggerUtils.logInfo(LOGGER, "Redisson MQ client created");
        return client;
    }

    private static boolean applyConnectionConfig(Config config, String key, GXRedissonConnectProperties properties) {
        if (properties == null) {
            GXLoggerUtils.logWarn(LOGGER, "Redisson MQ config [{}] is null, skipped", key);
            return false;
        }

        String address = decode(properties.getAddress());
        if (CharSequenceUtil.isBlank(address)) {
            GXLoggerUtils.logWarn(LOGGER, "Redisson MQ config [{}] address is blank, skipped", key);
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
            applyAuth(config, properties);
            return true;
        }

        SingleServerConfig singleConfig = config.useSingleServer()
                .setAddress(addresses[0])
                .setDatabase(Objects.requireNonNullElse(properties.getDatabase(), 0))
                .setConnectionMinimumIdleSize(Objects.requireNonNullElse(properties.getConnectionMinimumIdleSize(), 2));
        applyAuth(config, properties);
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

    private static void applyAuth(Config config, GXRedissonConnectProperties properties) {
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
