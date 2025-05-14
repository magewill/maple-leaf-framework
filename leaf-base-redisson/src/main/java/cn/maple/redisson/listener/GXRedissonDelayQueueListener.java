package cn.maple.redisson.listener;

import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.redisson.api.RReliableTopic;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 延迟队列数据到期时的监听器接口
 * <p>
 * 该接口定义了处理延迟队列中到期消息的标准方法。实现类负责将消息从延迟队列发布到可靠主题中，
 * 确保在分布式环境下消息能够可靠传递。接口提供了默认实现，简化了使用流程。
 * </p>
 *
 * <p>
 * 线程安全说明：
 * - 默认实现在获取RedissonClient和发布消息时考虑了线程安全问题
 * - 使用了异步处理机制，避免阻塞调用线程
 * - 添加了异常处理和日志记录，提高系统稳定性
 * - 使用CompletableFuture确保线程安全的异步处理
 * - 所有方法都是线程安全的，可以在多线程环境下调用
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建监听器实现类
 * @Component
 * @GXConvertRedissonDelayQueueToTopic(delayQueueName = "order-timeout-queue", topicName = "order-timeout-topic")
 * public class OrderTimeoutListener implements GXRedissonDelayQueueListener {
 *     // 可以选择重写execute方法自定义处理逻辑
 *     // 如果不重写，将使用默认实现，自动将消息发布到指定主题
 * }
 *
 * // 2. 发送延迟消息到队列
 * GXRedissonQueueUtils.sendDelayedMessage("order-timeout-queue", "order-123", 30, TimeUnit.MINUTES);
 * </pre>
 * </p>
 *
 * <p>
 * 注意事项：
 * - 实现类必须添加@Component注解以被Spring管理
 * - 必须配合@GXConvertRedissonDelayQueueToTopic注解使用，指定延迟队列名和目标主题名
 * - 确保redissonMQClient已正确配置并可通过Spring上下文获取
 * - 接口默认实现使用异步处理，如需同步处理可使用executeSync方法
 * - 实现类可以重写execute方法自定义处理逻辑，但必须确保线程安全
 * </p>
 *
 * <p>
 * 性能优化：
 * - 使用CompletableFuture实现非阻塞异步处理，提高系统吞吐量
 * - 通过日志级别控制，减少不必要的日志输出
 * - 异常处理机制确保单个消息处理失败不会影响整体系统
 * - 提供超时控制，防止长时间阻塞
 * </p>
 */
public interface GXRedissonDelayQueueListener {
    /**
     * 日志记录器
     */
    Logger log = LoggerFactory.getLogger(GXRedissonDelayQueueListener.class);

    /**
     * 处理延迟队列中的消息并发布到指定主题
     * <p>
     * 默认实现会将消息发布到指定的可靠主题中。如需自定义处理逻辑，可重写此方法。
     * 该方法使用异步处理机制，避免阻塞调用线程，并包含完善的异常处理。
     * </p>
     *
     * @param topicName 目标主题名称，消息将被发布到该主题
     * @param message   要处理的消息内容
     * @return 异步处理结果，true表示处理成功，false表示处理失败
     */
    default CompletableFuture<Boolean> execute(String topicName, String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 从Spring上下文获取RedissonClient实例
                RedissonClient redissonMQClient = GXSpringContextUtils.getBean("redissonMQClient", RedissonClient.class);
                if (ObjectUtil.isNull(redissonMQClient)) {
                    log.error("无法获取redissonMQClient实例，消息处理失败 - 主题: {}, 消息: {}", topicName, message);
                    return false;
                }

                // 获取可靠主题并发布消息
                RReliableTopic reliableTopic = redissonMQClient.getReliableTopic(topicName);
                long publishResult = reliableTopic.publish(message);

                log.debug("消息已发布到主题 {} - 消息ID: {}, 内容: {}", topicName, publishResult, message);
                return true;
            } catch (Exception e) {
                log.error("发布消息到主题 {} 时发生异常: {}", topicName, e.getMessage(), e);
                throw new GXBusinessException("发布消息失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 处理延迟队列中的消息并发布到指定主题（同步版本）
     * <p>
     * 此方法是{@link #execute(String, String)}的同步版本，会阻塞等待结果。
     * 在不需要异步处理的场景下使用。
     * </p>
     *
     * @param topicName 目标主题名称
     * @param message   要处理的消息内容
     * @param timeout   等待超时时间
     * @param unit      时间单位
     * @return 处理结果，true表示处理成功，false表示处理失败或超时
     */
    default boolean executeSync(String topicName, String message, long timeout, TimeUnit unit) {
        try {
            return execute(topicName, message).get(timeout, unit);
        } catch (Exception e) {
            log.error("同步执行消息处理时发生异常 - 主题: {}, 消息: {}, 异常: {}", topicName, message, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 处理延迟队列中的消息并发布到指定主题（同步版本，使用默认超时时间）
     * <p>
     * 此方法使用默认的30秒超时时间调用{@link #executeSync(String, String, long, TimeUnit)}。
     * </p>
     *
     * @param topicName 目标主题名称
     * @param message   要处理的消息内容
     * @return 处理结果，true表示处理成功，false表示处理失败或超时
     */
    default boolean executeSync(String topicName, String message) {
        return executeSync(topicName, message, 30, TimeUnit.SECONDS);
    }
}
