package cn.maple.redisson.util;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Redisson延迟队列工具类
 * <p>
 * 该工具类提供了基于Redisson的延迟队列操作，包括：
 * - 同步和异步发送延迟消息
 * - 获取延迟队列实例
 * - 消息格式转换
 * </p>
 * <p>
 * 安全性说明：
 * 1. 所有方法都进行了参数验证，避免空指针异常和非法参数
 * 2. 异常处理机制确保所有异常都被正确捕获和记录
 * 3. 工具类使用私有构造函数防止实例化，所有方法都是静态的
 * 4. 所有方法都是线程安全的，适合在多线程环境下使用
 * 5. 消息转换过程确保了不同类型消息的一致性处理
 * </p>
 */
public class GXRedissonDelayMQUtils {
    /**
     * 日志记录器
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(GXRedissonDelayMQUtils.class);

    /**
     * 缓存 RDelayedQueue 实例，避免重复创建转换任务
     */
    @SuppressWarnings("deprecation")
    private static final ConcurrentHashMap<String, RDelayedQueue<String>> DELAYED_QUEUE_CACHE = new ConcurrentHashMap<>();

    /**
     * 私有构造函数，防止实例化
     * 工具类应使用静态方法，不应被实例化
     */
    private GXRedissonDelayMQUtils() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 发送同步延迟消息
     * <p>
     * 将消息发送到指定的延迟队列中，该操作是同步的。
     * 消息会在指定的延迟时间后被队列消费者处理。
     * 该方法会进行参数验证，确保队列名、消息和时间单位不为空。
     * </p>
     *
     * @param queueName 队列名字，不能为null或空
     * @param message   发送的消息，不能为null
     * @param delayTime 延迟时间，必须大于0
     * @param timeUnit  延迟时间单位，不能为null
     * @throws IllegalArgumentException 如果参数不合法
     */
    @SuppressWarnings("deprecation")
    public static void sendDelayMessage(String queueName, Object message, long delayTime, TimeUnit timeUnit) {
        // 参数验证
        validateParameters(queueName, message, delayTime, timeUnit);

        RDelayedQueue<String> delayedQueue = null;
        try {
            String msg = convertMessageToString(message);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("发送延迟队列消息: queue={}, delay={}ms", queueName, timeUnit.toMillis(delayTime));
            }
            delayedQueue = getDelayedQueue(queueName);
            delayedQueue.offer(msg, delayTime, timeUnit);
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("发送延迟消息到队列[{}]时发生异常: {}", queueName, e.getMessage(), e);
            throw new GXBusinessException("发送延迟消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送异步延迟消息
     * <p>
     * 将消息异步发送到指定的延迟队列中。
     * 消息会在指定的延迟时间后被队列消费者处理。
     * 该方法会进行参数验证，确保队列名、消息和时间单位不为空。
     * </p>
     * <p>
     * 注意：当前实现与同步方法相同，但保留此方法以便未来实现真正的异步发送。
     * </p>
     *
     * @param queueName 队列名字，不能为null或空
     * @param message   发送的消息，不能为null
     * @param delayTime 延迟时间，必须大于0
     * @param timeUnit  延迟时间单位，不能为null
     * @throws IllegalArgumentException 如果参数不合法
     */
    @SuppressWarnings("deprecation")
    public static CompletableFuture<Void> sendAsyncDelayMessage(String queueName, Object message, long delayTime, TimeUnit timeUnit) {
        validateParameters(queueName, message, delayTime, timeUnit);
        try {
            String msg = convertMessageToString(message);
            RDelayedQueue<String> delayedQueue = getDelayedQueue(queueName);
            // Redisson 4.0.0 RFuture 直接继承了 CompletionStage
            RFuture<Void> redissonFuture = delayedQueue.offerAsync(msg, delayTime, timeUnit);
            // 将 Redisson 的 RFuture 转换为 CompletableFuture
            return redissonFuture.toCompletableFuture();
        } catch (Exception e) {
            LOGGER.error("异步发送延迟消息异常: {}", e.getMessage(), e);
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * 获取延迟队列对象
     * <p>
     * 根据队列名获取Redisson延迟队列实例。
     * 该方法会验证队列名的有效性，并确保能够获取到RedissonClient实例。
     * </p>
     *
     * @param queueName 队列名字，不能为null或空
     * @return 延迟队列实例
     * @throws IllegalArgumentException 如果队列名为空
     */
    @SuppressWarnings("deprecation")
    public static RDelayedQueue<String> getDelayedQueue(String queueName) {
        if (CharSequenceUtil.isBlank(queueName)) {
            throw new IllegalArgumentException("队列名不能为空");
        }
        return DELAYED_QUEUE_CACHE.computeIfAbsent(queueName, name -> {
            // use the RReliableQueue object with delay feature.
            //return redissonMQClient.getReliableQueue(queueName);
            RedissonClient redissonMQClient = getRedissonMQClient();
            // 目标队列（消息到期后进入的队列）
            RBlockingQueue<String> destinationQueue = redissonMQClient.getBlockingQueue(queueName);
            // 延迟队列（负责计时的队列）
            return redissonMQClient.getDelayedQueue(destinationQueue);
        });
    }

    /**
     * 将传递进来的的对象转换成字符串
     * <p>
     * 根据对象类型进行适当的转换：
     * - 如果是基本类型或String类型，直接转换为字符串
     * - 如果是复杂对象，则转换为JSON字符串
     * </p>
     *
     * @param message 待转换的对象，不能为null
     * @return 转换后的字符串
     */
    private static String convertMessageToString(Object message) {
        if (message == null) {
            throw new IllegalArgumentException("待转换的消息不能为null");
        }
        if (message instanceof String) {
            return (String) message;
        }
        try {
            if (!ClassUtil.isBasicType(message.getClass())) {
                return JSONUtil.toJsonStr(message);
            }
            return Convert.convert(String.class, message);
        } catch (Exception e) {
            LOGGER.error("消息转换为字符串时发生异常: {}", e.getMessage(), e);
            throw new GXBusinessException("消息格式转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从延迟队列中删除指定的消息
     *
     * @param queueName 队列名
     * @param message   消息内容
     */
    @SuppressWarnings("deprecation")
    public static boolean removeDelayMessage(String queueName, Object message) {
        if (CharSequenceUtil.isBlank(queueName)) {
            throw new IllegalArgumentException("队列名不能为空");
        }
        if (message == null) {
            throw new IllegalArgumentException("消息内容不能为null");
        }

        String msg = convertMessageToString(message);
        RDelayedQueue<String> delayedQueue = getDelayedQueue(queueName);
        boolean removed = delayedQueue.remove(msg);
        if (removed) {
            LOGGER.info("成功从延迟队列[{}]中删除消息", queueName);
        } else {
            LOGGER.warn("消息在延迟队列[{}]中不存在或已被消费", queueName);
        }
        return removed;
    }

    /**
     * 批量删除延迟队列中的消息
     */
    @SuppressWarnings("deprecation")
    public static int removeDelayMessages(String queueName, List<Object> messages) {
        if (CharSequenceUtil.isBlank(queueName)) {
            throw new IllegalArgumentException("队列名不能为空");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("消息列表不能为空");
        }
        List<String> msgList = messages.stream()
                .map(GXRedissonDelayMQUtils::convertMessageToString)
                .toList();
        RDelayedQueue<String> delayedQueue = getDelayedQueue(queueName);
        // 优化：Redisson 的 removeAll 效率更高（如果支持），或者循环删除
        // 这里为了稳妥还是循环删除，或者使用 delayedQueue.removeAll(msgList);
        int removedCount = 0;
        for (String msg : msgList) {
            // RDelayedQueue.remove 会同时从内部 List 和 ZSet 中通过 Lua 脚本删除
            if (delayedQueue.remove(msg)) {
                removedCount++;
            }
        }
        LOGGER.info("从延迟队列[{}]中批量删除{}条消息", queueName, removedCount);
        return removedCount;
    }

    /**
     * 清空延迟队列中的所有消息
     */
    @SuppressWarnings("deprecation")
    public static int clearDelayQueue(String queueName) {
        if (CharSequenceUtil.isBlank(queueName)) {
            throw new IllegalArgumentException("队列名不能为空");
        }
        RDelayedQueue<String> delayedQueue = null;
        delayedQueue = getDelayedQueue(queueName);
        int size = delayedQueue.size();
        delayedQueue.clear();

        LOGGER.warn("已清空延迟队列[{}]，删除了{}条消息", queueName, size);
        return size;
    }

    /**
     * 获取延迟队列中的消息数量
     */
    @SuppressWarnings("deprecation")
    public static int getDelayQueueSize(String queueName) {
        if (CharSequenceUtil.isBlank(queueName)) {
            throw new IllegalArgumentException("队列名不能为空");
        }
        RDelayedQueue<String> delayedQueue;
        delayedQueue = getDelayedQueue(queueName);
        return delayedQueue.size();
    }

    /**
     * 队列参数验证方法
     */
    private static void validateParameters(String queueName, Object message, long delayTime, TimeUnit timeUnit) {
        if (CharSequenceUtil.isBlank(queueName)) {
            throw new IllegalArgumentException("队列名不能为空");
        }
        if (message == null) {
            throw new IllegalArgumentException("消息内容不能为null");
        }
        if (delayTime <= 0) {
            throw new IllegalArgumentException("延迟时间必须大于0");
        }
        if (timeUnit == null) {
            throw new IllegalArgumentException("时间单位不能为null");
        }
    }

    /**
     * 获取 RedissonClient 实例
     */
    private static RedissonClient getRedissonMQClient() {
        RedissonClient redissonMQClient = GXSpringContextUtils.getBean("redissonMQClient", RedissonClient.class);
        if (redissonMQClient == null) {
            throw new GXBusinessException("无法获取redissonMQClient实例，请确保已正确配置");
        }
        return redissonMQClient;
    }
}
