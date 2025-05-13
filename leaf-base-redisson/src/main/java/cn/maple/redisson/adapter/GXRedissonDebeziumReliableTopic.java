package cn.maple.redisson.adapter;

import cn.hutool.core.lang.Assert;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.Getter;
import org.redisson.RedissonReliableTopic;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Debezium服务器Redis Stream消息处理适配器
 * <p>
 * 该适配器用于处理Debezium服务器生成的Redis Stream消息。由于Redisson的ReliableTopic在处理消息时
 * 使用固定格式，而Debezium服务器的Redis Stream消息格式不同，因此需要此适配器进行特殊处理。
 * </p>
 *
 * <p>Redisson的消息格式示例：</p>
 * <pre>
 * {
 *   "m": {
 *     "@class": "cn.hutool.core.lang.Dict",
 *     "age": 0,
 *     "name": "子曦"
 *   }
 * }
 * </pre>
 *
 * <p>Debezium的消息格式参考：
 * <a href="https://debezium.io/documentation/reference/2.3/operations/debezium-server.html#_redis_stream">Debezium Redis Stream消息格式</a>
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * {@code
 * @Override
 * public void registerRedissonListener() {
 *     // 创建适合Debezium消息格式的编解码器
 *     StringCodec stringCodec = new StringCodec();
 *
 *     // 创建主题实例
 *     GXRedissonDebeziumReliableTopic<Map<String, Object>> topic =
 *         new GXRedissonDebeziumReliableTopic<>(stringCodec, "debezium-topic");
 *
 *     // 添加消息监听器
 *     topic.addListener(Object.class, (channel, msg) -> {
 *         // 处理接收到的消息
 *         System.out.println("接收到消息: " + msg);
 *
 *         // 注意：数字类型的值可能被Base64编码，需要解码
 *         // 例如：NumberUtil.fromUnsignedByteArray(Base64Decoder.decode("AIlUQA==")) 解码成 9000000
 *     });
 * }
 * }
 * </pre>
 *
 * @param <T> 消息类型参数，指定监听和发布的消息类型
 * @author maple
 * @since 2023.1.0
 */
public class GXRedissonDebeziumReliableTopic<T> {
    /**
     * 日志记录器
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(GXRedissonDebeziumReliableTopic.class);

    /**
     * 底层Redisson可靠主题实例
     * 该字段是final的，确保线程安全性
     */
    private final RedissonReliableTopic reliableTopic;

    /**
     * 主题名称
     * -- GETTER --
     * 获取主题名称
     */
    @Getter
    private final String topicName;

    public GXRedissonDebeziumReliableTopic(String name) {
        this(GXSpringContextUtils.getBean(RedissonClient.class), new StringCodec(), name);
    }

    /**
     * 创建Debezium Redis Stream消息处理适配器
     *
     * @param codec 用于消息编解码的编解码器，通常使用StringCodec处理Debezium消息
     * @param name  主题名称，通常对应Debezium配置的输出主题
     * @throws IllegalArgumentException 如果参数无效或无法获取RedissonClient实例
     */
    public GXRedissonDebeziumReliableTopic(Codec codec, String name) {
        Assert.notNull(codec, "编解码器不能为空");
        Assert.notBlank(name, "主题名称不能为空");

        this.topicName = name;
        LOGGER.debug("正在创建Debezium Redis Stream消息适配器，主题: {}", name);

        // 从Spring上下文获取RedissonClient实例
        RedissonClient redissonClient = GXSpringContextUtils.getBean(RedissonClient.class);
        if (redissonClient == null) {
            String errorMsg = "无法获取RedissonClient实例，请确保已正确配置Redisson";
            LOGGER.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        // 创建可靠主题实例
        try {
            reliableTopic = (RedissonReliableTopic) redissonClient.getReliableTopic(name, codec);
            LOGGER.debug("成功创建Debezium Redis Stream消息适配器，主题: {}", name);
        } catch (Exception e) {
            LOGGER.error("创建Debezium Redis Stream消息适配器失败，主题: {}, 错误: {}", name, e.getMessage());
            throw new IllegalStateException("创建可靠主题实例失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建Debezium Redis Stream消息处理适配器，使用指定的RedissonClient实例
     *
     * @param redissonClient 指定的RedissonClient实例
     * @param codec          用于消息编解码的编解码器
     * @param name           主题名称
     * @throws IllegalArgumentException 如果任何参数为null或name为空
     */
    public GXRedissonDebeziumReliableTopic(RedissonClient redissonClient, Codec codec, String name) {
        Assert.notNull(redissonClient, "RedissonClient实例不能为空");
        Assert.notNull(codec, "编解码器不能为空");
        Assert.notBlank(name, "主题名称不能为空");

        this.topicName = name;
        LOGGER.debug("正在使用指定的RedissonClient创建Debezium Redis Stream消息适配器，主题: {}", name);

        // 创建可靠主题实例
        try {
            reliableTopic = (RedissonReliableTopic) redissonClient.getReliableTopic(name, codec);
            LOGGER.debug("成功创建Debezium Redis Stream消息适配器，主题: {}", name);
        } catch (Exception e) {
            LOGGER.error("创建Debezium Redis Stream消息适配器失败，主题: {}, 错误: {}", name, e.getMessage());
            throw new IllegalStateException("创建可靠主题实例失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将异步Future转换为Java标准的CompletableFuture
     *
     * @param future Redisson的RFuture对象
     * @param <T>    结果类型
     * @return 等价的CompletableFuture对象
     */
    public static <T> CompletableFuture<T> toCompletableFuture(RFuture<T> future) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        future.whenComplete((result, error) -> {
            if (error != null) {
                completableFuture.completeExceptionally(error);
            } else {
                completableFuture.complete(result);
            }
        });
        return completableFuture;
    }

    /**
     * 发布消息到主题
     * <p>
     * 此方法是同步的，会阻塞直到消息发布完成
     *
     * @param message 要发布的消息
     * @return 成功发布的消息数量（通常为1）
     * @throws IllegalArgumentException 如果消息为null
     */
    public long publish(T message) {
        Assert.notNull(message, "发布的消息不能为空");
        LOGGER.debug("正在发布消息到主题: {}", topicName);

        try {
            long result = reliableTopic.publish(message);
            LOGGER.debug("消息发布成功，主题: {}, 结果: {}", topicName, result);
            return result;
        } catch (Exception e) {
            LOGGER.error("消息发布失败，主题: {}, 错误: {}", topicName, e.getMessage());
            throw new RuntimeException("消息发布失败: " + e.getMessage(), e);
        }
    }

    /**
     * 添加消息监听器
     *
     * @param type     消息类型的Class对象
     * @param listener 消息监听器
     * @param <M>      消息类型
     * @return 监听器ID，可用于后续移除监听器
     * @throws IllegalArgumentException 如果type或listener为null
     */
    public <M> String addListener(Class<M> type, MessageListener<M> listener) {
        Assert.notNull(type, "消息类型不能为空");
        Assert.notNull(listener, "消息监听器不能为空");
        LOGGER.debug("正在添加消息监听器，主题: {}, 消息类型: {}", topicName, type.getName());

        try {
            String listenerId = reliableTopic.addListener(type, listener);
            LOGGER.debug("消息监听器添加成功，主题: {}, 监听器ID: {}", topicName, listenerId);
            return listenerId;
        } catch (Exception e) {
            LOGGER.error("添加消息监听器失败，主题: {}, 错误: {}", topicName, e.getMessage());
            throw new RuntimeException("添加消息监听器失败: " + e.getMessage(), e);
        }
    }

    /**
     * 移除指定的监听器
     *
     * @param listenerIds 要移除的监听器ID数组
     * @throws IllegalArgumentException 如果listenerIds为null
     */
    public void removeListener(String... listenerIds) {
        Assert.notNull(listenerIds, "监听器ID不能为空");
        LOGGER.debug("正在移除消息监听器，主题: {}, 监听器数量: {}", topicName, listenerIds.length);

        try {
            reliableTopic.removeListener(listenerIds);
            LOGGER.debug("消息监听器移除成功，主题: {}", topicName);
        } catch (Exception e) {
            LOGGER.error("移除消息监听器失败，主题: {}, 错误: {}", topicName, e.getMessage());
            throw new RuntimeException("移除消息监听器失败: " + e.getMessage(), e);
        }
    }

    /**
     * 移除所有监听器
     */
    public void removeAllListeners() {
        LOGGER.debug("正在移除所有消息监听器，主题: {}", topicName);
        try {
            reliableTopic.removeAllListeners();
            LOGGER.debug("所有消息监听器移除成功，主题: {}", topicName);
        } catch (Exception e) {
            LOGGER.error("移除所有消息监听器失败，主题: {}, 错误: {}", topicName, e.getMessage());
            throw new RuntimeException("移除所有消息监听器失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步移除所有监听器
     *
     * @return 表示操作完成的Future对象
     */
    public RFuture<Void> removeAllListenersAsync() {
        LOGGER.debug("正在异步移除所有消息监听器，主题: {}", topicName);
        return reliableTopic.removeAllListenersAsync();
    }

    /**
     * 获取主题中的消息数量
     *
     * @return 主题中的消息数量
     */
    public long size() {
        try {
            long size = reliableTopic.size();
            LOGGER.debug("获取主题消息数量，主题: {}, 数量: {}", topicName, size);
            return size;
        } catch (Exception e) {
            LOGGER.error("获取主题消息数量失败，主题: {}, 错误: {}", topicName, e.getMessage());
            throw new RuntimeException("获取主题消息数量失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步获取主题中的消息数量
     *
     * @return 表示操作完成的Future对象，包含主题中的消息数量
     */
    public RFuture<Long> sizeAsync() {
        LOGGER.debug("正在异步获取主题消息数量，主题: {}", topicName);
        return reliableTopic.sizeAsync();
    }

    /**
     * 获取当前监听器数量
     *
     * @return 当前监听器数量
     */
    public int countListeners() {
        try {
            int count = reliableTopic.countListeners();
            LOGGER.debug("获取监听器数量，主题: {}, 数量: {}", topicName, count);
            return count;
        } catch (Exception e) {
            LOGGER.error("获取监听器数量失败，主题: {}, 错误: {}", topicName, e.getMessage());
            throw new RuntimeException("获取监听器数量失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步发布消息到主题
     *
     * @param message 要发布的消息
     * @return 表示操作完成的Future对象，包含成功发布的消息数量
     * @throws IllegalArgumentException 如果消息为null
     */
    public RFuture<Long> publishAsync(T message) {
        Assert.notNull(message, "发布的消息不能为空");
        LOGGER.debug("正在异步发布消息到主题: {}", topicName);

        return reliableTopic.publishAsync(message);
    }

    /**
     * 异步添加消息监听器
     *
     * @param type     消息类型的Class对象
     * @param listener 消息监听器
     * @param <M>      消息类型
     * @return 表示操作完成的Future对象，包含监听器ID
     * @throws IllegalArgumentException 如果type或listener为null
     */
    public <M> RFuture<String> addListenerAsync(Class<M> type, MessageListener<M> listener) {
        Assert.notNull(type, "消息类型不能为空");
        Assert.notNull(listener, "消息监听器不能为空");
        LOGGER.debug("正在异步添加消息监听器，主题: {}, 消息类型: {}", topicName, type.getName());

        return reliableTopic.addListenerAsync(type, listener);
    }

    /**
     * 异步删除主题
     *
     * @return 表示操作完成的Future对象，如果成功则为true
     */
    public RFuture<Boolean> deleteAsync() {
        LOGGER.debug("正在异步删除主题: {}", topicName);
        return reliableTopic.deleteAsync();
    }

    /**
     * 异步获取主题在内存中的大小
     *
     * @return 表示操作完成的Future对象，包含主题在内存中的大小（字节）
     */
    public RFuture<Long> sizeInMemoryAsync() {
        LOGGER.debug("正在异步获取主题内存大小，主题: {}", topicName);
        return reliableTopic.sizeInMemoryAsync();
    }

    /**
     * 异步复制主题到指定数据库
     *
     * @param keys     要复制的键列表
     * @param database 目标数据库ID
     * @param replace  是否替换现有键
     * @return 表示操作完成的Future对象，如果成功则为true
     * @throws IllegalArgumentException 如果keys为null
     */
    public RFuture<Boolean> copyAsync(List<Object> keys, int database, boolean replace) {
        Assert.notNull(keys, "键列表不能为空");
        LOGGER.debug("正在异步复制主题，主题: {}, 目标数据库: {}, 替换现有键: {}", topicName, database, replace);

        return reliableTopic.copyAsync(keys, database, replace);
    }

    /**
     * 异步设置主题过期时间
     *
     * @param timeToLive 过期时间
     * @param timeUnit   时间单位
     * @param param      过期模式参数
     * @param keys       附加键
     * @return 表示操作完成的Future对象，如果成功则为true
     * @throws IllegalArgumentException 如果timeUnit为null
     */
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        Assert.notNull(timeUnit, "时间单位不能为空");
        LOGGER.debug("正在异步设置主题过期时间，主题: {}, 过期时间: {} {}", topicName, timeToLive, timeUnit);

        return reliableTopic.expireAsync(timeToLive, timeUnit, param, keys);
    }

    /**
     * 异步清除主题的过期设置
     *
     * @return 表示操作完成的Future对象，如果成功则为true
     */
    public RFuture<Boolean> clearExpireAsync() {
        LOGGER.debug("正在异步清除主题过期设置，主题: {}", topicName);
        return reliableTopic.clearExpireAsync();
    }

    /**
     * 异步移除指定的监听器
     *
     * @param listenerIds 要移除的监听器ID数组
     * @return 表示操作完成的Future对象
     * @throws IllegalArgumentException 如果listenerIds为null
     */
    public RFuture<Void> removeListenerAsync(String... listenerIds) {
        Assert.notNull(listenerIds, "监听器ID不能为空");
        LOGGER.debug("正在异步移除消息监听器，主题: {}, 监听器数量: {}", topicName, listenerIds.length);

        return reliableTopic.removeListenerAsync(listenerIds);
    }

    /**
     * 获取当前订阅者数量
     *
     * @return 当前订阅者数量
     */
    public int countSubscribers() {
        try {
            int count = reliableTopic.countSubscribers();
            LOGGER.debug("获取订阅者数量，主题: {}, 数量: {}", topicName, count);
            return count;
        } catch (Exception e) {
            LOGGER.error("获取订阅者数量失败，主题: {}, 错误: {}", topicName, e.getMessage());
            throw new RuntimeException("获取订阅者数量失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步获取当前订阅者数量
     *
     * @return 表示操作完成的Future对象，包含当前订阅者数量
     */
    public RFuture<Integer> countSubscribersAsync() {
        LOGGER.debug("正在异步获取订阅者数量，主题: {}", topicName);
        return reliableTopic.countSubscribersAsync();
    }
}
