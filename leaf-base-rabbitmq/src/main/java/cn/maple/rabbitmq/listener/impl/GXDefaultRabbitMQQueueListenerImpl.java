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

/**
 * RabbitMQ默认队列监听器实现类
 * <p>
 * 该类实现了GXRabbitMQQueueListener接口，用于监听和处理RabbitMQ默认队列中的消息。
 * 通过@RabbitListener注解指定要监听的队列，队列名通过SpEL表达式动态获取。
 * 该监听器只在enable-rabbitmq配置为true且存在ConnectionFactory类时才会生效。
 * </p>
 * <p>
 * 懒加载说明：使用@Lazy注解实现懒加载，只有在实际需要使用时才会初始化，
 * 减少系统启动时的资源消耗。
 * </p>
 * 
 * @author maple
 */
@Component
@Slf4j
@Lazy
@ConditionalOnExpression("'${enable-rabbitmq}'.equals('true')")
@ConditionalOnClass(name = {"org.springframework.amqp.rabbit.connection.ConnectionFactory"})
public class GXDefaultRabbitMQQueueListenerImpl implements GXRabbitMQQueueListener {
    
    /**
     * 处理RabbitMQ队列中的消息
     * <p>
     * 该方法会将接收到的消息转换为字符串，并尝试解析为JSON格式。
     * 如果消息是JSON格式，则转换为Dict对象进行处理；否则直接记录原始消息内容。
     * </p>
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
    @RabbitListener(queues = "#{T(cn.maple.core.framework.util.GXSpELToolUtils).callBeanMethodSpELExpression(Class.forName('cn.maple.rabbitmq.properties.GXRabbitMQProperties'), 'getDefaultQueueName', Class.forName('java.lang.String'), new Class[0])}")
    public void process(Message data) {
        try {
            // 将消息体转换为字符串
            final String messageContent = new String(data.getBody(), StandardCharsets.UTF_8);
            
            // 检查消息内容是否为空
            if (CharSequenceUtil.isNotBlank(messageContent)) {
                // 尝试解析为JSON格式
                if (JSONUtil.isTypeJSON(messageContent)) {
                    try {
                        final Dict param = JSONUtil.toBean(messageContent, Dict.class);
                        log.debug("接收到JSON消息: {}", param);
                        // 在此处添加实际的业务处理逻辑
                    } catch (JSONException e) {
                        log.warn("JSON解析异常: {}", e.getMessage());
                    }
                } else {
                    log.debug("接收到非JSON消息: {}", messageContent);
                    // 在此处添加处理非JSON消息的逻辑
                }
            } else {
                log.warn("接收到空消息");
            }
        } catch (AmqpException e) {
            log.error("处理RabbitMQ消息时发生AMQP异常: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("处理RabbitMQ消息时发生未预期的异常: {}", e.getMessage(), e);
        }
    }
}
