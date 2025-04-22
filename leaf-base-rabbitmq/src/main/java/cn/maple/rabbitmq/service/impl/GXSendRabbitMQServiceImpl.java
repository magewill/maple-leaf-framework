package cn.maple.rabbitmq.service.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.service.impl.GXBusinessServiceImpl;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import cn.maple.rabbitmq.service.GXSendRabbitMQService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * RabbitMQ消息发送服务实现类
 * <p>
 * 该类实现了GXSendRabbitMQService接口，提供了向RabbitMQ发送消息的具体实现。
 * 通过Spring的RabbitTemplate组件实现消息的发送，支持消息确认和返回机制。
 * </p>
 * <p>
 * 线程安全说明：该实现类是线程安全的，可以在多线程环境下使用。
 * RabbitTemplate本身是线程安全的，可以被多个线程共享。
 * </p>
 *
 * @author maple
 */
@Service
@Slf4j
public class GXSendRabbitMQServiceImpl extends GXBusinessServiceImpl implements GXSendRabbitMQService {
    /**
     * RabbitMQ模板组件，用于发送消息
     * 由Spring自动注入，线程安全
     */
    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送常规消息到RabbitMQ
     * <p>
     * 将消息发送到指定的交换机和路由键，支持消息确认和返回机制。
     * 该方法会将消息内容转换为JSON格式，并设置适当的消息属性。
     * </p>
     * <p>
     * 内存安全说明：
     * 1. 使用StandardCharsets.UTF_8确保字符编码一致性
     * 2. 显式设置消息的内容类型和编码，避免乱码问题
     * 3. 通过JSONUtil工具类处理JSON转换，避免手动字符串拼接
     * </p>
     *
     * @param messageReqDto 待发送的消息请求对象，包含消息内容、交换机、路由键等信息
     * @throws AmqpException 如果消息发送过程中发生错误，将抛出AmqpException异常
     */
    @Override
    public void sendNormalMessage(GXRabbitMQMessageReqDto messageReqDto) {
        try {
            // 提取消息相关信息
            Dict data = messageReqDto.getData();
            String exchange = messageReqDto.getExchange();
            String routingKey = messageReqDto.getRoutingKey();
            CorrelationData correlationData = messageReqDto.getCorrelationData();
            MessageProperties messageProperties = messageReqDto.getMessageProperties();

            // 设置消息属性
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
            if (CharSequenceUtil.isNotBlank(messageReqDto.getTag())) {
                messageProperties.setConsumerTag(messageReqDto.getTag());
            }

            // 创建消息并发送
            Message message = new Message(JSONUtil.toJsonStr(data).getBytes(StandardCharsets.UTF_8), messageProperties);
            rabbitTemplate.convertAndSend(exchange, routingKey, message, correlationData);

            log.debug("消息发送成功 - 交换机: {}, 路由键: {}", exchange, routingKey);
        } catch (AmqpException e) {
            log.error("消息发送失败 - 原因: {}", e.getMessage(), e);
            throw e; // 重新抛出异常，让调用者决定如何处理
        } catch (Exception e) {
            log.error("消息发送过程中发生未预期的异常: {}", e.getMessage(), e);
            throw new AmqpException("消息发送过程中发生未预期的异常", e);
        }
    }
}