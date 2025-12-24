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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Redisson消息队列工具类
 * <p>
 * 该工具类提供了基于Redisson的可靠主题(ReliableTopic)操作，包括：
 * - 同步和异步发布消息
 * - 订阅消息（避免重复消费的关键：使用单例模式管理Topic实例）
 * - 批量发布消息
 * - 订阅者管理
 * </p>
 * <p>
 * ⚠️ 重要提示 - RReliableTopic的消费机制：
 * <p>
 * 1. RReliableTopic会为每个客户端实例自动分配唯一的订阅者ID（由Redisson内部管理）
 * 2. 消费位置是根据 "客户端实例 + Topic名称" 来记录的
 * 3. 避免重复消费的关键：确保每个应用实例对同一个Topic只创建一次监听器
 * <p>
 * 实现方式：
 * - 本工具类使用单例模式缓存Topic实例（TOPIC_CACHE）
 * - 同一个Topic在应用生命周期内只会创建一次监听器
 * - 应用重启后，Redisson会使用相同的客户端ID继续从上次位置消费
 * <p>
 * 注意事项：
 * - 不要在运行时重复调用subscribe()方法
 * - 建议在@PostConstruct中初始化所有订阅
 * - 确保RedissonClient配置了固定的客户端ID（通过Config.setConnectionPoolSize等）
 * </p>
 */
public class GXRedissonMQUtils {
    /**
     * 日志记录器
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(GXRedissonMQUtils.class);

    /**
     * 缓存 ReliableTopic 实例（单例模式，避免重复订阅）
     */
    private static final ConcurrentHashMap<String, RReliableTopic> TOPIC_CACHE = new ConcurrentHashMap<>();

    /**
     * 缓存监听器ID，用于后续取消订阅
     */
    private static final ConcurrentHashMap<String, String> LISTENER_ID_CACHE = new ConcurrentHashMap<>();

    /**
     * 私有构造函数，防止实例化
     */
    private GXRedissonMQUtils() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 同步发布Redis的可靠主题消息
     *
     * @param topicName 主题名字，不能为null或空
     * @param message   发布的消息，不能为null
     * @return 接收消息的订阅者数量
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
                LOGGER.debug("发布消息到主题[{}]成功，订阅者数: {}", topicName, subscribersReceived);
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
     * 异步发布Redis的可靠主题消息
     *
     * @param topicName 主题名字，不能为null或空
     * @param message   发布的消息，不能为null
     * @return CompletableFuture，包含接收消息的订阅者数量
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
     * 订阅可靠主题消息（单例模式，避免重复消费）
     * <p>
     * ⚠️ 重要说明：
     * RReliableTopic 无法阻止消息被标记为已交付。
     * 即使监听器抛出异常，消息也会被视为已交付，不会重复消费。
     *
     * @param topicName    主题名字，不能为null或空
     * @param messageClass 消息类型的Class对象
     * @param listener     消息监听器，不能为null
     * @param <T>          消息类型
     * @return 监听器ID（由Redisson自动生成）
     */
    public static <T> String subscribe(String topicName, Class<T> messageClass, MessageListener<T> listener) {
        if (CharSequenceUtil.isBlank(topicName) || listener == null || messageClass == null) {
            throw new IllegalArgumentException("订阅参数不完整 (topicName, messageClass, listener)");
        }

        // 使用 topicName 和 messageClass 作为唯一标识，确保同一个Topic的同一种消息类型只被一个监听器处理
        String cacheKey = topicName + ":" + messageClass.getName();
        // 使用 computeIfAbsent 保证原子性操作，避免并发问题
        String listenerId = LISTENER_ID_CACHE.computeIfAbsent(cacheKey, key -> {
            try {
                RReliableTopic reliableTopic = getReliableTopic(topicName);
                String newListenerId = reliableTopic.addListener(messageClass, (channel, msg) -> {
                    try {
                        listener.onMessage(channel, msg);
                    } catch (Exception e) {
                        LOGGER.error("处理主题[{}]消息时发生业务异常: {}", topicName, e.getMessage(), e);
                        // 注意：即使这里抛出异常，消息也会被标记为已交付，不会自动重试。
                        // 需要在这里加入重试逻辑或发送到死信队列。
                    }
                });
                LOGGER.info("成功订阅主题[{}]，消息类型[{}]，监听器ID: {}", topicName, messageClass.getSimpleName(), newListenerId);
                return newListenerId;
            } catch (Exception e) {
                LOGGER.error("订阅主题[{}]时发生异常: {}", topicName, e.getMessage(), e);
                throw new GXBusinessException("订阅主题失败: " + e.getMessage(), e);
            }
        });

        // 如果键已存在，computeIfAbsent 会返回旧值，此时记录一个警告日志
        if (!listenerId.equals(LISTENER_ID_CACHE.get(cacheKey))) {
            LOGGER.warn("主题[{}]的消息类型[{}]已被订阅，监听器ID: {}。本次订阅请求被忽略，以防止重复消费。", topicName, messageClass.getSimpleName(), listenerId);
        }

        return listenerId;
    }

    /**
     * 强制订阅（即使已订阅过也会重新订阅）
     * <p>
     * ⚠️ 慎用：这会导致同一个Topic有多个监听器，可能会重复消费消息！
     * 仅在特殊场景下使用，比如需要多个不同的处理逻辑。
     * </p>
     */
    public static <T> String forceSubscribe(String topicName, Class<T> messageClass, MessageListener<T> listener) {
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
                }
            });

            LOGGER.warn("⚠️ 强制订阅主题[{}]，监听器ID: {}，可能导致重复消费！", topicName, listenerId);
            return listenerId;
        } catch (Exception e) {
            LOGGER.error("强制订阅主题[{}]时发生异常: {}", topicName, e.getMessage(), e);
            throw new GXBusinessException("强制订阅主题失败: " + e.getMessage(), e);
        }
    }

    /**
     * 取消订阅（移除监听器）
     * <p>
     * 注意：取消订阅后，Redisson仍会记录消费位置。
     * 如果重新订阅，会从上次的位置继续消费。
     * </p>
     *
     * @param topicName   主题名字
     * @param listenerIds 监听器ID（可以是多个）
     */
    public static void unsubscribe(String topicName, String... listenerIds) {
        if (CharSequenceUtil.isBlank(topicName)) {
            throw new IllegalArgumentException("主题名不能为空");
        }
        if (listenerIds == null || listenerIds.length == 0) {
            LOGGER.warn("监听器ID为空，无需取消订阅");
            return;
        }

        try {
            RReliableTopic reliableTopic = getReliableTopic(topicName);
            reliableTopic.removeListener(listenerIds);

            // 从缓存中移除
            List<String> idList = Arrays.asList(listenerIds);
            LISTENER_ID_CACHE.entrySet().removeIf(entry -> idList.contains(entry.getValue()));

            LOGGER.info("成功取消订阅主题[{}]，监听器ID: {}", topicName, Arrays.toString(listenerIds));
        } catch (Exception e) {
            LOGGER.error("取消订阅主题[{}]时发生异常: {}", topicName, e.getMessage(), e);
            throw new GXBusinessException("取消订阅失败: " + e.getMessage(), e);
        }
    }

    /**
     * 彻底移除订阅者（删除消费位置，慎用！）
     * <p>
     * ⚠️ 警告：此操作会删除Redis中保存的订阅者消费位置。
     * 如果重新订阅，将从最新消息开始消费（历史消息会被跳过）。
     * </p>
     *
     * @param topicName   主题名字
     * @param listenerIds 监听器ID（可以是多个）
     */
    public static void removeSubscriber(String topicName, String... listenerIds) {
        if (CharSequenceUtil.isBlank(topicName)) {
            throw new IllegalArgumentException("主题名不能为空");
        }
        if (CollUtil.isEmpty(Arrays.asList(listenerIds))) {
            LOGGER.warn("订阅者ID列表为空，无需移除");
            return;
        }

        try {
            RReliableTopic reliableTopic = getReliableTopic(topicName);
            reliableTopic.removeListener(listenerIds);

            // 从缓存中移除
            for (String listenerId : listenerIds) {
                LISTENER_ID_CACHE.entrySet().removeIf(entry -> entry.getValue().equals(listenerId));
            }

            LOGGER.warn("⚠️ 已彻底移除订阅者 {} 来自主题 [{}]，消费位置已删除！",
                    Arrays.toString(listenerIds), topicName);
        } catch (Exception e) {
            LOGGER.error("移除订阅者异常: {}", e.getMessage(), e);
            throw new GXBusinessException("移除订阅者失败: " + e.getMessage(), e);
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
     *
     * @param topicName 主题名
     * @return 订阅者数量
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
     * 获取本地缓存的所有监听器信息
     * （这是当前应用实例订阅的监听器）
     *
     * @return Map<主题:消息类型, 监听器ID>
     */
    public static Map<String, String> getAllLocalListeners() {
        return new HashMap<>(LISTENER_ID_CACHE);
    }

    /**
     * 获取指定主题在本地的所有监听器
     *
     * @param topicName 主题名
     * @return Map<消息类型, 监听器ID>
     */
    public static Map<String, String> getLocalListenersByTopic(String topicName) {
        if (CharSequenceUtil.isBlank(topicName)) {
            throw new IllegalArgumentException("主题名不能为空");
        }

        Map<String, String> result = new HashMap<>();
        String prefix = topicName + ":";

        LISTENER_ID_CACHE.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                // 提取消息类型（去掉前缀 "topicName:"）
                String messageClass = key.substring(prefix.length());
                result.put(messageClass, value);
            }
        });

        return result;
    }

    /**
     * 检查监听器是否存在
     *
     * @param topicName    主题名
     * @param messageClass 消息类型
     * @return true表示监听器存在
     */
    public static boolean hasListener(String topicName, Class<?> messageClass) {
        String cacheKey = topicName + ":" + messageClass.getName();
        return LISTENER_ID_CACHE.containsKey(cacheKey);
    }

    /**
     * 获取所有已订阅的主题名称列表
     *
     * @return 主题名称列表
     */
    public static List<String> getAllSubscribedTopics() {
        return LISTENER_ID_CACHE.keySet().stream()
                .map(key -> key.substring(0, key.lastIndexOf(":")))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 获取已缓存的监听器ID
     *
     * @param topicName    主题名
     * @param messageClass 消息类型
     * @return 监听器ID，如果未订阅返回null
     */
    public static String getCachedListenerId(String topicName, Class<?> messageClass) {
        String cacheKey = topicName + ":" + messageClass.getName();
        return LISTENER_ID_CACHE.get(cacheKey);
    }

    /**
     * 检查主题是否已订阅
     *
     * @param topicName    主题名
     * @param messageClass 消息类型
     * @return true表示已订阅
     */
    public static boolean isSubscribed(String topicName, Class<?> messageClass) {
        return getCachedListenerId(topicName, messageClass) != null;
    }

    /**
     * 获取可靠主题实例（带缓存）
     *
     * @param topicName 主题名
     * @return RReliableTopic实例
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
     * 清除主题缓存（一般不需要调用）
     *
     * @param topicName 主题名，如果为null则清除所有缓存
     */
    public static void clearTopicCache(String topicName) {
        if (CharSequenceUtil.isBlank(topicName)) {
            TOPIC_CACHE.clear();
            LISTENER_ID_CACHE.clear();
            LOGGER.info("已清除所有主题缓存");
        } else {
            TOPIC_CACHE.remove(topicName);
            LISTENER_ID_CACHE.entrySet().removeIf(entry -> entry.getKey().startsWith(topicName + ":"));
            LOGGER.info("已清除主题[{}]的缓存", topicName);
        }
    }

    /**
     * 参数验证
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
            throw new GXBusinessException("无法获取redissonMQClient实例，请确保已正确配置！");
        }
        return redissonMQClient;
    }
}
