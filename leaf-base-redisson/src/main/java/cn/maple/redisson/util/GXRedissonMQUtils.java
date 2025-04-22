package cn.maple.redisson.util;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.redisson.api.RFuture;
import org.redisson.api.RReliableTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

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
     * @throws GXBusinessException 如果获取RedissonClient失败或发布过程中发生异常
     */
    public static long publish(String topicName, Object message) {
        if (CharSequenceUtil.isBlank(topicName)) {
            throw new IllegalArgumentException("主题名不能为空");
        }
        if (message == null) {
            throw new IllegalArgumentException("消息内容不能为null");
        }

        try {
            RedissonClient redissonMQClient = GXSpringContextUtils.getBean("redissonMQClient", RedissonClient.class);
            if (redissonMQClient == null) {
                throw new GXBusinessException("无法获取redissonMQClient实例");
            }

            RReliableTopic reliableTopic = redissonMQClient.getReliableTopic(topicName);
            return reliableTopic.publish(message);
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("发布消息到主题[{}]时发生异常: {}", topicName, e.getMessage(), e);
            throw new GXBusinessException("发布消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步发布Redis的可靠主题消息
     * <p>
     * 将消息异步发布到指定的Redisson可靠主题中，该操作不会阻塞调用线程。
     * 该方法会进行参数验证，确保主题名和消息不为空，并处理可能的异常。
     * </p>
     *
     * @param topicName 主题名字，不能为null或空
     * @param message   发布的消息，不能为null
     * @return 目前主题中的消息数量
     * @throws GXBusinessException  如果获取RedissonClient失败或发布过程中发生异常
     * @throws ExecutionException   如果异步操作执行过程中发生异常
     * @throws InterruptedException 如果当前线程在等待结果时被中断
     */
    public static long publishAsync(String topicName, Object message) throws ExecutionException, InterruptedException {
        if (CharSequenceUtil.isBlank(topicName)) {
            throw new IllegalArgumentException("主题名不能为空");
        }
        if (message == null) {
            throw new IllegalArgumentException("消息内容不能为null");
        }

        try {
            RedissonClient redissonMQClient = GXSpringContextUtils.getBean("redissonMQClient", RedissonClient.class);
            if (redissonMQClient == null) {
                throw new GXBusinessException("无法获取redissonMQClient实例");
            }

            RReliableTopic reliableTopic = redissonMQClient.getReliableTopic(topicName);
            RFuture<Long> longRFuture = reliableTopic.publishAsync(message);
            return longRFuture.get();
        } catch (GXBusinessException e) {
            throw e;
        } catch (ExecutionException | InterruptedException e) {
            LOGGER.error("异步发布消息到主题[{}]时发生异常: {}", topicName, e.getMessage(), e);
            throw e; // 重新抛出原始异常，保留调用栈信息
        } catch (Exception e) {
            LOGGER.error("异步发布消息到主题[{}]时发生未知异常: {}", topicName, e.getMessage(), e);
            throw new GXBusinessException("异步发布消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取处理debezium server的可靠主题
     * <p>
     * 使用指定的编解码器创建或获取一个Redisson可靠主题实例，用于处理Debezium事件。
     * 该方法会检查参数的有效性，并确保返回有效的主题实例。
     * </p>
     *
     * @param redissonMQClient Redisson客户端对象，不能为null
     * @param name             主题名称，不能为null或空
     * @param codec            消息编解码器，可以为null，此时使用客户端默认编解码器
     * @return 可靠主题实例
     * @throws IllegalArgumentException 如果redissonMQClient为null或name为空
     */
    public static RReliableTopic getDebeziumReliableTopic(RedissonClient redissonMQClient, String name, Codec codec) {
        if (redissonMQClient == null) {
            throw new IllegalArgumentException("Redisson客户端不能为null");
        }
        if (CharSequenceUtil.isBlank(name)) {
            throw new IllegalArgumentException("主题名称不能为空");
        }

        if (ObjectUtil.isNull(codec)) {
            codec = redissonMQClient.getConfig().getCodec();
        }
        return redissonMQClient.getReliableTopic(name, codec);
    }

    /**
     * 获取处理debezium server的可靠主题（使用默认编解码器）
     * <p>
     * 使用客户端默认的编解码器创建或获取一个Redisson可靠主题实例，用于处理Debezium事件。
     * 该方法是{@link #getDebeziumReliableTopic(RedissonClient, String, Codec)}的简化版本。
     * </p>
     *
     * @param redissonMQClient Redisson客户端对象，不能为null
     * @param name             主题名称，不能为null或空
     * @return 可靠主题实例
     * @throws IllegalArgumentException 如果redissonMQClient为null或name为空
     */
    public static RReliableTopic getDebeziumReliableTopic(RedissonClient redissonMQClient, String name) {
        if (redissonMQClient == null) {
            throw new IllegalArgumentException("Redisson客户端不能为null");
        }
        if (CharSequenceUtil.isBlank(name)) {
            throw new IllegalArgumentException("主题名称不能为空");
        }

        Codec codec = redissonMQClient.getConfig().getCodec();
        return getDebeziumReliableTopic(redissonMQClient, name, codec);
    }
}
