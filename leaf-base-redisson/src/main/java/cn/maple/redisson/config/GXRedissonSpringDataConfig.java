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
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redisson Spring Data配置类
 * <p>
 * 该配置类负责创建和管理Redisson客户端实例，用于Redis操作和缓存管理。
 * 通过条件注解确保只有在特定条件下才会创建相关Bean，避免资源浪费。
 * 提供标准Redisson客户端和Spring缓存管理器，支持多种Redis部署模式。
 * </p>
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>创建标准Redisson客户端，用于常规Redis操作</li>
 *   <li>配置Spring Cache集成，支持TTL和最大空闲时间设置</li>
 *   <li>处理连接信息的安全解码，包括地址、密码和用户名</li>
 *   <li>支持线程池参数优化，提高并发性能</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 1. 注入标准RedissonClient
 * @Autowired
 * private RedissonClient redissonClient;
 *
 * // 使用示例 - 操作Redis数据
 * RBucket&lt;String&gt; bucket = redissonClient.getBucket("myKey");
 * bucket.set("Hello, Redisson!");
 * String value = bucket.get();
 *
 * // 2. 使用Spring Cache注解
 * // 在配置类中启用缓存
 * @EnableCaching
 * public class AppConfig {
 *     // 配置已由GXRedissonSpringDataConfig提供
 * }
 *
 * // 在服务类中使用缓存注解
 * @Service
 * public class UserService {
 *     @Cacheable(value = "users", key = "#id")
 *     public User getUserById(Long id) {
 *         // 方法逻辑
 *     }
 * }
 * </pre>
 *
 * @author maple
 * @since 4.1.2
 */
@Configuration
@ConditionalOnClass(name = {"org.redisson.Redisson"})
public class GXRedissonSpringDataConfig {
    /**
     * 日志记录器
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(GXRedissonSpringDataConfig.class);

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
     * 使用JsonJacksonCodec作为默认编解码器，确保对象序列化的一致性和安全性。
     * 通过destroyMethod确保应用关闭时正确释放资源，避免连接泄漏。
     * </p>
     *
     * @param config Redisson配置对象
     * @return RedissonClient实例
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(Config config) {
        // 创建并配置编解码器
        Codec jsonJacksonCodec = new JsonJacksonCodec();
        config.setCodec(jsonJacksonCodec);

        // 创建Redisson客户端实例
        GXLoggerUtils.logInfo(LOGGER, "正在创建标准Redisson客户端实例...");
        RedissonClient client = Redisson.create(config);
        GXLoggerUtils.logInfo(LOGGER, "标准Redisson客户端实例创建成功");

        return client;
    }

    /**
     * 创建Redisson Spring缓存管理器
     * <p>
     * 集成Spring Cache抽象，提供基于Redis的缓存实现。
     * 根据配置决定是否使用特定的缓存配置，支持为不同缓存名称设置不同的TTL和最大空闲时间。
     * 缓存配置通过redisson-cache-config.yml文件进行管理，支持动态调整。
     * </p>
     *
     * @param redissonClient Redisson客户端实例
     * @return RedissonSpringCacheManager实例
     */
    @Bean("redissonSpringCacheManager")
    public RedissonSpringCacheManager redissonSpringCacheManager(RedissonClient redissonClient) {
        // 获取缓存配置，使用不可变Map防止外部修改
        final Map<String, CacheConfig> config = Map.copyOf(redissonCacheManagerConfig.getConfig());

        // 根据配置创建缓存管理器
        GXLoggerUtils.logInfo(LOGGER, "正在创建Redisson Spring缓存管理器，配置项数量: {}", config.size());

        if (config.isEmpty()) {
            return new RedissonSpringCacheManager(redissonClient);
        }
        return new RedissonSpringCacheManager(redissonClient, config);
    }

    /**
     * 创建标准Redisson配置
     * <p>
     * 处理连接信息，包括地址、密码和用户名的解码。
     * 使用线程安全的方式处理配置转换，避免并发问题。
     * 采用防御性复制确保配置不被外部修改，提高线程安全性。
     * 直接使用Redisson原生API创建配置，避免JSON序列化/反序列化的开销。
     * </p>
     *
     * @return Redisson配置对象
     */
    @Bean("config")
    public Config config() {
        // 获取原始配置并创建线程安全的副本
        final Map<String, GXRedissonConnectProperties> configMap = new ConcurrentHashMap<>();

        // 处理每个连接配置，解密敏感信息
        redissonConfig.getConfig().forEach((key, value) -> {
            if (Objects.isNull(value)) {
                GXLoggerUtils.logWarn(LOGGER, "配置键 {} 对应的连接属性为null，已跳过处理", key);
                return;
            }

            // 创建连接属性的副本，避免修改原始对象
            GXRedissonConnectProperties connProps = new GXRedissonConnectProperties();

            // 解密地址
            String address = GXCommonUtils.decodeConnectStr(value.getAddress(), String.class);
            if (CharSequenceUtil.isNotEmpty(address)) {
                connProps.setAddress(address);
            } else {
                GXLoggerUtils.logWarn(LOGGER, "配置键 {} 的地址为空或解密失败", key);
            }

            // 解密密码
            String password = GXCommonUtils.decodeConnectStr(value.getPassword(), String.class);
            connProps.setPassword(password); // 密码可以为空

            // 解密用户名
            String username = GXCommonUtils.decodeConnectStr(value.getUsername(), String.class);
            connProps.setUsername(username); // 用户名可以为空

            // 复制其他非敏感配置
            connProps.setDatabase(value.getDatabase());
            connProps.setConnectionMinimumIdleSize(value.getConnectionMinimumIdleSize());
            connProps.setSlaveConnectionMinimumIdleSize(value.getSlaveConnectionMinimumIdleSize());
            connProps.setThreads(value.getThreads());
            connProps.setNettyThreads(value.getNettyThreads());

            // 将处理后的配置添加到线程安全的Map中
            configMap.put(key, connProps);
        });

        // 使用Redisson原生API创建配置，避免JSON序列化/反序列化的开销
        Config config = new Config();

        // 根据配置类型设置不同的连接模式
        configMap.forEach((key, value) -> {
            String address = value.getAddress();
            if (CharSequenceUtil.isEmpty(address)) {
                return;
            }

            // 根据地址格式判断连接类型并配置
            if (address.startsWith("redis://") || address.startsWith("rediss://")) {
                // 单节点模式
                SingleServerConfig singleServerConfig = config.useSingleServer()
                        .setAddress(address)
                        .setDatabase(Objects.requireNonNullElse(value.getDatabase(), 0))
                        .setConnectionMinimumIdleSize(Objects.requireNonNullElse(value.getConnectionMinimumIdleSize(), 2));

                // 设置密码（如果有）
                if (CharSequenceUtil.isNotEmpty(value.getPassword())) {
                    singleServerConfig.setPassword(value.getPassword());
                }

                // 设置用户名（如果有）
                if (CharSequenceUtil.isNotEmpty(value.getUsername())) {
                    singleServerConfig.setUsername(value.getUsername());
                }
            } else if (address.contains(",") && (address.startsWith("redis://") || address.contains("redis://"))) {
                // 集群模式 - 地址包含逗号分隔的多个节点
                String[] clusterAddresses = address.split(",");
                ClusterServersConfig clusterServersConfig = config.useClusterServers();

                for (String clusterAddress : clusterAddresses) {
                    clusterServersConfig.addNodeAddress(clusterAddress.trim());
                }

                // 设置集群特有配置
                clusterServersConfig.setSlaveConnectionMinimumIdleSize(
                        Objects.requireNonNullElse(value.getSlaveConnectionMinimumIdleSize(), 2));

                // 设置密码（如果有）
                if (CharSequenceUtil.isNotEmpty(value.getPassword())) {
                    clusterServersConfig.setPassword(value.getPassword());
                }

                // 设置用户名（如果有）
                if (CharSequenceUtil.isNotEmpty(value.getUsername())) {
                    clusterServersConfig.setUsername(value.getUsername());
                }
            } else {
                // 其他连接模式可以在这里扩展
                GXLoggerUtils.logWarn(LOGGER, "不支持的Redis连接地址格式: {}", address);
            }
        });

        // 设置线程池配置
        int threads = configMap.values().stream()
                .map(GXRedissonConnectProperties::getThreads)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(2);
        config.setThreads(threads);

        int nettyThreads = configMap.values().stream()
                .map(GXRedissonConnectProperties::getNettyThreads)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(4);
        config.setNettyThreads(nettyThreads);

        GXLoggerUtils.logInfo(LOGGER, "Redisson配置创建成功，线程数: {}, Netty线程数: {}", threads, nettyThreads);

        return config;
    }
}
