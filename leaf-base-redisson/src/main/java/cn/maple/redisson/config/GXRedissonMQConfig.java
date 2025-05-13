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
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redisson消息队列配置类
 * <p>
 * 该配置类负责创建和管理专用于消息队列操作的Redisson客户端实例。
 * 通过条件注解确保只有在明确启用的情况下才会创建相关Bean，避免资源浪费。
 * 配置与标准Redisson配置隔离，确保消息队列操作不会影响其他Redis操作。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 1. 在配置文件中启用Redisson MQ
 * maple.framework.mq.redisson.enable=true
 *
 * // 2. 配置Redisson MQ连接信息
 * // 在对应环境的redisson-mq.yml中配置
 *
 * // 3. 注入并使用RedissonClient
 * @Autowired
 * @Qualifier("redissonMQClient")
 * private RedissonClient redissonMQClient;
 *
 * // 使用示例 - 发布订阅模式
 * RTopic topic = redissonMQClient.getTopic("myTopic");
 * topic.publish("Hello, Redisson MQ!");
 *
 * // 使用示例 - 队列模式
 * RQueue<String> queue = redissonMQClient.getQueue("myQueue");
 * queue.add("Task 1");
 * </pre>
 *
 * @author maple
 * @since 4.1.2
 */
@Configuration
@ConditionalOnClass(name = {"org.redisson.Redisson"})
@ConditionalOnExpression("${maple.framework.mq.redisson.enable:false}")
public class GXRedissonMQConfig {
    /**
     * 日志记录器
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(GXRedissonMQConfig.class);

    /**
     * Redisson消息队列配置属性
     * 专用于消息队列操作的配置，与标准配置隔离，避免相互影响
     */
    @Resource
    private GXRedissonMQProperties redissonMQConfig;

    /**
     * 创建消息队列专用Redisson配置
     * <p>
     * 处理MQ连接信息，包括地址、密码和用户名的解码
     * 使用线程安全的方式处理配置转换，避免并发问题
     * 采用防御性复制确保配置不被外部修改
     * </p>
     *
     * @return 消息队列专用Redisson配置对象
     */
    @Bean("mqConfig")
    public Config mqConfig() {
        // 获取原始配置并创建线程安全的副本
        final Map<String, GXRedissonConnectProperties> configMap = new ConcurrentHashMap<>();

        // 处理每个连接配置，解密敏感信息
        redissonMQConfig.getConfig().forEach((key, value) -> {
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

        return config;
    }

    /**
     * 创建消息队列专用Redisson客户端
     * <p>
     * 使用独立的客户端实例处理消息队列操作，避免与标准操作互相影响
     * 配置JsonJacksonCodec作为默认编解码器，提供高效的序列化/反序列化能力
     * 通过destroyMethod确保应用关闭时正确释放资源
     * </p>
     *
     * @param mqConfig 消息队列Redisson配置对象
     * @return 消息队列专用RedissonClient实例
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonMQClient(Config mqConfig) {
        // 创建并配置编解码器
        Codec jsonJacksonCodec = new JsonJacksonCodec();
        mqConfig.setCodec(jsonJacksonCodec);

        // 创建Redisson客户端实例
        GXLoggerUtils.logInfo(LOGGER, "正在创建Redisson MQ客户端实例...");
        var client = Redisson.create(mqConfig);
        GXLoggerUtils.logInfo(LOGGER, "Redisson MQ客户端实例创建成功");

        return client;
    }
}
