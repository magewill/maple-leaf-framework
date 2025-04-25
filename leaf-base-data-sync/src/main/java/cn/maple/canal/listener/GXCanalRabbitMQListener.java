package cn.maple.canal.listener;

import cn.maple.canal.service.GXCanalMessageParseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * Canal RabbitMQ 消息监听器
 * <p>
 * 该监听器负责从RabbitMQ队列中接收Canal发送的数据库变更消息，并将其转发给消息解析服务进行处理。
 * 监听器通过SpEL表达式动态获取配置参数，包括：
 * - 队列名称 (canalQueueName)
 * - 交换机名称 (exchangeName)
 * - 路由键 (routingKey)
 * - 并发消费者数量 (concurrencyCount)
 * </p>
 * <p>
 * 监听器使用了@Lazy注解，实现按需加载，减少系统启动时的资源消耗。
 * </p>
 * <p>
 * 使用示例：
 * 1. 确保配置了正确的RabbitMQ连接参数
 * 2. 确保Canal服务已配置为将变更数据发送到RabbitMQ
 * 3. 实现GXCanalMessageParseService接口以处理接收到的消息
 * </p>
 * <p>
 * 安全性考虑：
 * 1. 消息处理应在事务中进行，确保数据一致性
 * 2. 应对消息处理异常进行妥善处理，避免消息丢失
 * 3. 考虑使用消息确认机制，确保消息被正确处理
 * </p>
 */
@Slf4j
@Component
@Lazy
public class GXCanalRabbitMQListener {
    @Resource
    private GXCanalMessageParseService canalMessageParseService;

    /**
     * 监听RabbitMQ队列中的Canal消息
     * <p>
     * 该方法通过RabbitMQ监听器接收Canal发送的数据库变更消息，并将其转发给消息解析服务进行处理。
     * 监听器配置通过SpEL表达式动态获取，支持运行时配置变更。
     * </p>
     * <p>
     * 配置说明：
     * - Queue：指定要监听的队列名称，通过GXCanalProperties.getCanalQueueName()获取
     * - Exchange：指定交换机名称和类型，使用FANOUT类型实现消息广播
     * - RoutingKey：指定路由键，用于消息路由
     * - Concurrency：指定并发消费者数量，用于提高消息处理能力
     * </p>
     *
     * @param message 从RabbitMQ接收的原始消息字符串
     */
    @RabbitListener(bindings = {@QueueBinding(
            value = @Queue(value = "#{T(cn.maple.core.framework.util.GXSpELToolUtils).callBeanMethodSpELExpression(Class.forName('cn.maple.canal.properties.GXCanalProperties'), 'getCanalQueueName', Class.forName('java.lang.String'), new Class[0])}", durable = "true"),
            exchange = @Exchange(value = "#{T(cn.maple.core.framework.util.GXSpELToolUtils).callBeanMethodSpELExpression(Class.forName('cn.maple.canal.properties.GXCanalProperties'), 'getExchangeName', Class.forName('java.lang.String'), new Class[0])}", type = ExchangeTypes.FANOUT),
            key = "#{T(cn.maple.core.framework.util.GXSpELToolUtils).callBeanMethodSpELExpression(Class.forName('cn.maple.canal.properties.GXCanalProperties'), 'getRoutingKey', Class.forName('java.lang.String'), new Class[0])}")
    },
            concurrency = "#{T(cn.maple.core.framework.util.GXSpELToolUtils).callBeanMethodSpELExpression(Class.forName('cn.maple.canal.properties.GXCanalProperties'), 'getConcurrencyCount', Class.forName('java.lang.String'), new Class[0])}")
    public void listener(String message) {
        log.debug("接收到Canal消息，开始处理");
        try {
            canalMessageParseService.parseMessage(message);
            log.debug("Canal消息处理完成");
        } catch (Exception e) {
            log.error("处理Canal消息时发生异常: {}", e.getMessage(), e);
            // 异常处理策略可根据实际需求调整
            // 例如：记录失败消息、重试、发送告警等
        }
    }
}