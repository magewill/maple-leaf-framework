package cn.maple.redisson.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.redisson.api.RReliableTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redisson消息队列工具类
 * <p>
 * 该工具类提供了基于Redisson的可靠主题(ReliableTopic)操作，包括：
 * - 同步和异步发布消息
 * - 获取特定编码的主题实例
 * - 支持Debezium集成的主题操作
 * </p>
 * <p>
 * 安全性说明：
 * 1. 所有方法都进行了空值检查，避免空指针异常
 * 2. 异步操作提供了异常处理机制，确保异常信息能够被正确捕获和处理
 * 3. 工具类使用私有构造函数防止实例化，所有方法都是静态的
 * 4. 所有方法都是线程安全的，适合在多线程环境下使用
 * </p>
 */
public class GXRedissonMQUtils {
    /**
     * 日志记录器
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(GXRedissonMQUtils.class);

    /**
     * 缓存 ReliableTopic 实例
     */
    private static final ConcurrentHashMap<String, RReliableTopic> TOPIC_CACHE = new ConcurrentHashMap<>();

    /**
     * 私有构造函数，防止实例化
     * 工具类应使用静态方法，不应被实例化
     */
    private GXRedissonMQUtils() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 同步发布Redis的可靠主题消息
     * <p>
     * 将消息发布到指定的Redisson可靠主题中，该操作是同步的，会阻塞直到消息发布完成。
     * 该方法会进行参数验证，确保主题名和消息不为空，并处理可能的异常。
     * </p>
     *
     * @param topicName 主题名字，不能为null或空
     * @param message   发布的消息，不能为null
     * @return 目前主题中的消息数量
     */
    public static long publish(String topicName, Object message) {
        validatePublishParameters(topicName, message);

        try {
            RReliableTopic reliableTopic = getReliableTopic(topicName);
            long subscribersReceived = reliableTopic.publish(message);
            if (subscribersReceived < 0) {
                LOGGER.warn("发布消息到主题[{}]失败，当前消息数: {}", topicName, subscribersReceived);
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("发布消息到主题[{}]成功，当前消息数: {}", topicName, subscribersReceived);
            }
            return subscribersReceived;
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("发布消息到主题[{}]时发生异常: {}", topicName, e.getMessage(), e);
            throw new GXBusinessException("发布消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步发布Redis的可靠主题消息（真正异步版本）
     * <p>
     * 将消息异步发布到指定的Redisson可靠主题中，立即返回Future对象，不阻塞调用线程。
     * </p>
     *
     * @param topicName 主题名字，不能为null或空
     * @param message   发布的消息，不能为null
     * @return CompletableFuture，包含发布后的消息数量
     */
    public static CompletableFuture<Long> publishAsync(String topicName, Object message) {
        validatePublishParameters(topicName, message);

        try {
            RReliableTopic reliableTopic = getReliableTopic(topicName);
            return reliableTopic
                    .publishAsync(message)
                    .toCompletableFuture()
                    .whenComplete((count, ex) -> {
                        if (ex != null) {
                            LOGGER.error("异步发布消息到主题[{}]失败", topicName, ex);
                        } else if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("异步发布消息到主题[{}]成功，接收者数量: {}", topicName, count);
                        }
                    });
        } catch (GXBusinessException e) {
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            LOGGER.error("异步发布消息到主题[{}]时发生异常: {}", topicName, e.getMessage(), e);
            return CompletableFuture.failedFuture(new GXBusinessException("异步发布消息失败: " + e.getMessage(), e));
        }
    }

    /**
     * 获取可靠主题实例（带缓存）
     */
    public static RReliableTopic getReliableTopic(String topicName) {
        if (CharSequenceUtil.isBlank(topicName)) {
            throw new IllegalArgumentException("主题名称不能为空");
        }
        return TOPIC_CACHE.computeIfAbsent(topicName, name -> {
            RedissonClient redissonClient = getRedissonMQClient();
            return redissonClient.getReliableTopic(name);
        });
    }

    /**
     * 订阅可靠主题消息
     * <p>
     * 注册一个消息监听器来处理主题中的消息。
     * 可靠主题会确保消息不会丢失，即使消费者暂时离线。
     * </p>
     *
     * @param topicName    主题名字，不能为null或空
     * @param messageClass 消息类型的Class对象
     * @param listener     消息监听器，不能为null
     * @param <T>          消息类型
     * @return 监听器ID，用于后续取消订阅
     * @throws IllegalArgumentException 如果参数不合法
     * @throws GXBusinessException      如果订阅过程中发生异常
     */
    public static <T> String subscribe(String topicName, Class<T> messageClass, MessageListener<T> listener) {
        if (CharSequenceUtil.isBlank(topicName) || listener == null || messageClass == null) {
            throw new IllegalArgumentException("订阅参数不完整");
        }

        try {
            RReliableTopic reliableTopic = getReliableTopic(topicName);

            String listenerId = reliableTopic.addListener(messageClass, (channel, msg) -> {
                try {
                    listener.onMessage(channel, msg);
                } catch (Exception e) {
                    LOGGER.error("处理主题[{}]消息时发生异常: {}", topicName, e.getMessage(), e);
                    // 不要抛出异常，否则可能导致 Redisson 监听线程中断或打印过多内部错误
                }
            });

            LOGGER.info("成功订阅主题[{}]，监听器ID: {}", topicName, listenerId);
            return listenerId;
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("订阅主题[{}]时发生异常: {}", topicName, e.getMessage(), e);
            throw new GXBusinessException("订阅主题失败: " + e.getMessage(), e);
        }
    }

    /**
     * 取消订阅
     *
     * @param topicName   主题名字
     * @param listenerIds 监听器ID（可以是多个）
     */
    public static void unsubscribe(String topicName, String... listenerIds) {
        if (CharSequenceUtil.isBlank(topicName)) {
            throw new IllegalArgumentException("主题名不能为空");
        }
        if (CollUtil.isEmpty(Arrays.asList(listenerIds))) {
            throw new IllegalArgumentException("监听器ID不能为空");
        }

        try {
            RReliableTopic reliableTopic = getReliableTopic(topicName);
            for (String listenerId : listenerIds) {
                reliableTopic.removeListener(listenerId);
            }

            LOGGER.info("成功取消订阅主题[{}]，监听器ID: {}", topicName, listenerIds);
        } catch (Exception e) {
            LOGGER.error("取消订阅主题[{}]时发生异常: {}", topicName, e.getMessage(), e);
            throw new GXBusinessException("取消订阅失败: " + e.getMessage(), e);
        }
    }

    /**
     * 彻底移除某个订阅者（慎用）
     * <p>
     * 移除后，Redis 中为该订阅者保存的消息偏移量会被删除。
     * </p>
     */
    public static void removeSubscriber(String topicName, String... listenerIds) {
        if (CollUtil.isEmpty(Arrays.asList(listenerIds))) return;
        try {
            RReliableTopic reliableTopic = getReliableTopic(topicName);
            reliableTopic.removeListener(listenerIds);
            LOGGER.info("已移除订阅者 [{}] 来自主题 [{}]", listenerIds, topicName);
        } catch (Exception e) {
            LOGGER.error("移除订阅者异常: {}", e.getMessage());
        }
    }

    /**
     * 批量发布消息
     *
     * @param topicName 主题名
     * @param messages  消息列表
     * @return 成功发布的消息数量
     */
    public static int publishBatch(String topicName, List<Object> messages) {
        if (CharSequenceUtil.isBlank(topicName)) {
            throw new IllegalArgumentException("主题名不能为空");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("消息列表不能为空");
        }

        try {
            RReliableTopic reliableTopic = getReliableTopic(topicName);
            int successCount = 0;
            // RReliableTopic 目前没有原生的 publishAll，循环发布是标准做法
            for (Object message : messages) {
                try {
                    reliableTopic.publish(message);
                    successCount++;
                } catch (Exception e) {
                    LOGGER.error("批量发布中单条失败，主题: {}", topicName, e);
                }
            }
            LOGGER.info("批量发布消息到主题[{}]完成，成功: {}, 总数: {}", topicName, successCount, messages.size());
            return successCount;
        } catch (Exception e) {
            LOGGER.error("批量发布消息到主题[{}]时发生异常: {}", topicName, e.getMessage(), e);
            throw new GXBusinessException("批量发布消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取主题的订阅者数量
     */
    public static int countSubscribers(String topicName) {
        if (CharSequenceUtil.isBlank(topicName)) {
            throw new IllegalArgumentException("主题名不能为空");
        }

        try {
            RReliableTopic reliableTopic = getReliableTopic(topicName);
            return reliableTopic.countSubscribers();
        } catch (Exception e) {
            LOGGER.error("获取主题[{}]订阅者数量时发生异常: {}", topicName, e.getMessage(), e);
            throw new GXBusinessException("获取订阅者数量失败: " + e.getMessage(), e);
        }
    }

    /**
     * 参数验证
     *
     * @param topicName 主题名
     * @param message   消息内容
     */
    private static void validatePublishParameters(String topicName, Object message) {
        if (CharSequenceUtil.isBlank(topicName)) {
            throw new IllegalArgumentException("主题名不能为空");
        }
        if (message == null) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
    }

    /**
     * 获取 RedissonClient 实例
     */
    private static RedissonClient getRedissonMQClient() {
        RedissonClient redissonMQClient = GXSpringContextUtils.getBean("redissonMQClient", RedissonClient.class);
        if (redissonMQClient == null) {
            throw new GXBusinessException("无法获取redissonMQClient实例，请确保已正确配置！！");
        }
        return redissonMQClient;
    }
}
