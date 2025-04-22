package cn.maple.redisson.util;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Redisson队列工具类
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
public class GXRedissonQueueUtils {
    /**
     * 日志记录器
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(GXRedissonQueueUtils.class);

    /**
     * 私有构造函数，防止实例化
     * 工具类应使用静态方法，不应被实例化
     */
    private GXRedissonQueueUtils() {
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
     * @throws GXBusinessException      如果获取RedissonClient失败或发送过程中发生异常
     */
    public static void sendDelayMessage(String queueName, Object message, int delayTime, TimeUnit timeUnit) {
        // 参数验证
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

        try {
            String msg = convertMessageToString(message);
            LOGGER.info("发送Redisson的延迟队列消息 : queueName = {} , message = {}", queueName, msg);
            RDelayedQueue<String> delayedQueue = getDelayedQueue(queueName);
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
     * @throws GXBusinessException      如果获取RedissonClient失败或发送过程中发生异常
     */
    public static void sendAsyncDelayMessage(String queueName, Object message, int delayTime, TimeUnit timeUnit) {
        // 参数验证
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

        try {
            String msg = convertMessageToString(message);
            LOGGER.info("异步发送Redisson的延迟队列消息 : queueName = {} , message = {}", queueName, msg);
            RDelayedQueue<String> delayedQueue = getDelayedQueue(queueName);
            delayedQueue.offer(msg, delayTime, timeUnit);
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("异步发送延迟消息到队列[{}]时发生异常: {}", queueName, e.getMessage(), e);
            throw new GXBusinessException("异步发送延迟消息失败: " + e.getMessage(), e);
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
     * @throws GXBusinessException      如果无法获取RedissonClient实例
     */
    public static RDelayedQueue<String> getDelayedQueue(String queueName) {
        if (CharSequenceUtil.isBlank(queueName)) {
            throw new IllegalArgumentException("队列名不能为空");
        }

        RedissonClient redissonMQClient = GXSpringContextUtils.getBean("redissonMQClient", RedissonClient.class);
        if (Objects.isNull(redissonMQClient)) {
            throw new GXBusinessException("无法获取redissonMQClient实例，请确保已正确配置");
        }

        try {
            RBlockingQueue<String> destinationQueue = redissonMQClient.getBlockingQueue(queueName);
            return redissonMQClient.getDelayedQueue(destinationQueue);
        } catch (Exception e) {
            LOGGER.error("获取延迟队列[{}]实例时发生异常: {}", queueName, e.getMessage(), e);
            throw new GXBusinessException("获取延迟队列实例失败: " + e.getMessage(), e);
        }
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
     * @throws IllegalArgumentException 如果message为null
     */
    private static String convertMessageToString(Object message) {
        if (message == null) {
            throw new IllegalArgumentException("待转换的消息不能为null");
        }

        try {
            if (!(ClassUtil.isBasicType(message.getClass()) || String.class.isAssignableFrom(message.getClass()))) {
                return JSONUtil.toJsonStr(message);
            }
            return Convert.convert(String.class, message);
        } catch (Exception e) {
            LOGGER.error("消息转换为字符串时发生异常: {}", e.getMessage(), e);
            throw new GXBusinessException("消息格式转换失败: " + e.getMessage(), e);
        }
    }
}
