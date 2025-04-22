package cn.maple.rabbitmq.callback;

import org.springframework.retry.RetryContext;

/**
 * RabbitMQ消息重试恢复回调接口
 * <p>
 * 该接口用于处理消息发送重试耗尽后的恢复策略。当消息发送失败并且重试次数达到上限后，
 * Spring AMQP会调用该回调接口来执行自定义的恢复逻辑。
 * </p>
 * <p>
 * 实现该接口可以自定义消息重试失败后的处理策略，例如：
 * 1. 将消息保存到数据库或死信队列中
 * 2. 发送告警通知
 * 3. 执行降级处理
 * 4. 记录详细的错误日志
 * </p>
 * <p>
 * 线程安全说明：实现类应当保证线程安全，因为该回调可能在多线程环境下被调用
 * </p>
 * 
 * @author maple
 */
public interface GXRecoveryCallback {
    /**
     * 消息重试恢复回调方法
     * <p>
     * 当消息发送重试次数耗尽后，会调用该方法执行恢复逻辑。
     * 通过RetryContext可以获取到重试的上下文信息，包括异常原因、重试次数等。
     * </p>
     * <p>
     * 返回值说明：
     * - 如果返回null，表示恢复操作未产生结果
     * - 如果返回非null对象，该对象将作为重试操作的最终结果返回
     * </p>
     *
     * @param retryContext 重试上下文，包含重试相关的信息，如异常、重试次数等
     * @return 恢复操作的结果，可以为null
     * @throws Exception 如果恢复过程中发生异常，可以抛出异常
     */
    Object recover(RetryContext retryContext) throws Exception;
}
