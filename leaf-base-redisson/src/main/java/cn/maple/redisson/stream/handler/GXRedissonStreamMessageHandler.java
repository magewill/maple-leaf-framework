package cn.maple.redisson.stream.handler;

import cn.maple.redisson.stream.dto.req.GXRedissonStreamMessageDto;

/**
 * 消息处理器接口
 * <p>
 * 业务方实现此接口，并注册到 MessageQueueManager。
 * 框架保证：
 * - handle() 抛出异常 → 触发重试（直至 maxRetry 或进死信队列）
 * - handle() 正常返回 → ACK，消息从 PEL 移除
 */
@FunctionalInterface
public interface GXRedissonStreamMessageHandler {
    /**
     * 处理消息。
     *
     * @param message 待处理消息（payload 已反序列化）
     * @throws Exception 处理失败时抛出，框架负责重试逻辑
     */
    void handle(GXRedissonStreamMessageDto message) throws Exception;
}