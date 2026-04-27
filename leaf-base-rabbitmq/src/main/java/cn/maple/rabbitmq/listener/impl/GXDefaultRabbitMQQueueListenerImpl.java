package cn.maple.rabbitmq.listener.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONUtil;
import cn.maple.rabbitmq.listener.GXRabbitMQQueueListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * RabbitMQ默认队列监听器实现类
 * <p>
 * 该类实现了{@link GXRabbitMQQueueListener}接口，用于监听和处理RabbitMQ默认队列中的消息。
 * 通过@RabbitListener注解指定要监听的队列，队列名通过SpEL表达式动态获取。
 * 该监听器只在enable-rabbitmq配置为true且存在ConnectionFactory类时才会生效。
 * </p>
 *
 * <p>
 * 配置说明：
 * 1. 在application.yml或rabbit.yml中设置maple.framework.mq.rabbitmq.enable=true启用此监听器
 * 2. 通过gx.rabbitmq.default-queue-name配置项指定要监听的队列名
 * 3. 也可以通过自定义GXRabbitMQProperties实现类的getDefaultQueueName()方法提供队列名
 * </p>
 *
 * <p>
 * 懒加载说明：使用@Lazy注解实现懒加载，只有在实际需要使用时才会初始化，
 * 减少系统启动时的资源消耗。
 * </p>
 *
 * <p>
 * 线程安全说明：
 * 该实现类不维护任何共享状态，所有操作都在方法内部完成，因此是线程安全的。
 * 在高并发环境下，多个线程可以安全地调用process方法处理不同的消息。
 * </p>
 *
 * @author maple
 * @since 2023.1.0
 */
@Component
@Slf4j
@Lazy
@ConditionalOnExpression("${maple.framework.mq.rabbitmq.enable:false}")
@ConditionalOnClass(name = {"org.springframework.amqp.rabbit.connection.ConnectionFactory"})
public class GXDefaultRabbitMQQueueListenerImpl implements GXRabbitMQQueueListener {
    /**
     * 处理RabbitMQ队列中的消息
     * <p>
     * 该方法会将接收到的消息转换为字符串，并尝试解析为JSON格式。
     * 如果消息是JSON格式，则转换为Dict对象进行处理；否则直接记录原始消息内容。
     * </p>
     *
     * <p>
     * 性能优化说明：
     * 1. 使用StandardCharsets.UTF_8常量而非"UTF-8"字符串，避免重复创建Charset对象
     * 2. 使用JSONUtil.isTypeJSON进行快速JSON格式检测，减少不必要的异常捕获
     * 3. 对于大型消息或复杂处理逻辑，可以考虑使用CompletableFuture进行异步处理
     * </p>
     *
     * <p>
     * 内存安全说明：
     * 1. 使用StandardCharsets.UTF_8确保字符编码一致性
     * 2. 使用CharSequenceUtil.isNotBlank检查字符串是否为空，避免空指针异常
     * 3. 使用try-catch块捕获可能的异常，确保消息处理的健壮性
     * </p>
     *
     * @param data 接收到的消息对象
     */
    @Override
    @RabbitHandler
    @RabbitListener(queues = "${maple.framework.mq.rabbitmq.default-queue-name:${spring.rabbitmq.default-queue-name:${spring.rabbitmq.defaultQueueName:maple.default.queue}}}")
    public void process(Message data) {
        try {
            // 将消息体转换为字符串
            final String messageContent = new String(data.getBody(), StandardCharsets.UTF_8);

            // 检查消息内容是否为空
            if (CharSequenceUtil.isNotBlank(messageContent)) {
                processMessageContent(messageContent, data);
            } else {
                log.warn("接收到空消息");
            }
        } catch (AmqpException e) {
            log.error("处理RabbitMQ消息时发生AMQP异常: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("处理RabbitMQ消息时发生未预期的异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理消息内容
     * <p>
     * 根据消息内容类型（JSON或非JSON）进行不同的处理。
     * 对于大型消息或复杂处理逻辑，可以在此方法中使用CompletableFuture进行异步处理。
     * </p>
     *
     * @param messageContent  消息内容字符串
     * @param originalMessage 原始消息对象，可用于获取消息属性等信息
     */
    private void processMessageContent(String messageContent, Message originalMessage) {
        // 尝试解析为JSON格式
        if (JSONUtil.isTypeJSON(messageContent)) {
            processJsonMessage(messageContent);
        } else {
            processNonJsonMessage(messageContent);
        }
    }

    /**
     * 处理JSON格式的消息
     *
     * @param messageContent JSON格式的消息内容
     */
    private void processJsonMessage(String messageContent) {
        try {
            final Dict param = JSONUtil.toBean(messageContent, Dict.class);
            log.debug("接收到JSON消息: {}", param);
            // 在此处添加实际的业务处理逻辑
            // 对于复杂或耗时的处理，可以考虑使用异步处理
            // CompletableFuture.runAsync(() -> handleComplexJsonMessage(param));
        } catch (JSONException e) {
            log.warn("JSON解析异常: {}", e.getMessage());
        }
    }

    /**
     * 处理非JSON格式的消息
     *
     * @param messageContent 非JSON格式的消息内容
     */
    private void processNonJsonMessage(String messageContent) {
        log.debug("接收到非JSON消息: {}", messageContent);
        // 在此处添加处理非JSON消息的逻辑
    }

    /**
     * 处理复杂的JSON消息（异步方法示例）
     * <p>
     * 此方法展示了如何使用CompletableFuture进行异步处理。
     * 对于耗时的操作，如数据库访问、外部API调用等，可以考虑使用异步处理提高性能。
     * </p>
     *
     * @param param 解析后的JSON消息内容
     * @return 异步处理的Future对象
     */
    private CompletableFuture<Void> handleComplexJsonMessage(Dict param) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 模拟耗时操作
                Thread.sleep(100);
                // 实际的业务处理逻辑
                log.debug("异步处理JSON消息完成: {}", param);
            } catch (Exception e) {
                log.error("异步处理JSON消息时发生异常: {}", e.getMessage(), e);
            }
        });
    }
}
