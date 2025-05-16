package cn.maple.rabbitmq.listener;

import org.springframework.amqp.core.Message;

/**
 * RabbitMQ队列消息监听器接口
 * <p>
 * 该接口定义了处理RabbitMQ队列消息的标准方法。实现此接口的类需要提供具体的消息处理逻辑。
 * 框架提供了默认实现{@link cn.maple.rabbitmq.listener.impl.GXDefaultRabbitMQQueueListenerImpl}，
 * 也可以根据业务需求自定义实现。
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * @Component
 * @RabbitListener(queues = "your-queue-name")
 * public class YourCustomListener implements GXRabbitMQQueueListener {
 *     @Override
 *     public void process(Message data) {
 *         // 自定义消息处理逻辑
 *         String content = new String(data.getBody(), StandardCharsets.UTF_8);
 *         // 处理消息内容...
 *     }
 * }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * 线程安全说明：
 * 实现类应当确保其process方法是线程安全的，因为在高并发环境下，多个线程可能同时调用同一个监听器实例的process方法。
 * 建议避免在实现类中使用共享的可变状态，或者确保对共享状态的访问是同步的。
 * </p>
 *
 * @author maple
 * @since 2023.1.0
 */
public interface GXRabbitMQQueueListener {
    /**
     * 处理RabbitMQ队列中的消息
     * <p>
     * 该方法会在监听到队列消息时被调用，用于处理接收到的消息数据。
     * 实现类应当在此方法中提供具体的消息处理逻辑，包括但不限于：
     * - 消息内容解析（如JSON反序列化）
     * - 业务逻辑处理
     * - 异常处理
     * </p>
     *
     * <p>
     * 注意事项：
     * 1. 实现类应当处理所有可能的异常，避免未捕获的异常导致消息处理失败
     * 2. 对于需要重试的场景，应当结合RabbitMQ的重试机制或自定义重试逻辑
     * 3. 处理完成后不需要手动确认消息，框架会根据方法是否抛出异常来决定是否确认消息
     * </p>
     *
     * @param data 接收到的消息对象，包含消息体、属性等信息
     */
    void process(Message data);
}
