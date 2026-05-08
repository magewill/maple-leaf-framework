package cn.maple.canal.listener;

import cn.maple.canal.service.GXCanalMessageParseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Lazy
public class GXCanalRabbitMQListener {
    @Resource
    private GXCanalMessageParseService canalMessageParseService;

    @RabbitListener(bindings = {@QueueBinding(
            value = @Queue(value = "#{T(cn.maple.core.framework.util.GXSpELToolUtils).callBeanMethodSpELExpression(Class.forName('cn.maple.canal.properties.GXCanalProperties'), 'getCanalQueueName', Class.forName('java.lang.String'), new Class[0])}", durable = "true"),
            exchange = @Exchange(value = "#{T(cn.maple.core.framework.util.GXSpELToolUtils).callBeanMethodSpELExpression(Class.forName('cn.maple.canal.properties.GXCanalProperties'), 'getExchangeName', Class.forName('java.lang.String'), new Class[0])}", type = ExchangeTypes.FANOUT),
            key = "#{T(cn.maple.core.framework.util.GXSpELToolUtils).callBeanMethodSpELExpression(Class.forName('cn.maple.canal.properties.GXCanalProperties'), 'getRoutingKey', Class.forName('java.lang.String'), new Class[0])}")
    },
            concurrency = "#{T(cn.maple.core.framework.util.GXSpELToolUtils).callBeanMethodSpELExpression(Class.forName('cn.maple.canal.properties.GXCanalProperties'), 'getConcurrencyCount', Class.forName('java.lang.String'), new Class[0])}")
    public void listener(String message) {
        log.debug("Receive Canal message, start processing");
        try {
            canalMessageParseService.parseMessage(message);
            log.debug("Canal message processed");
        } catch (Exception e) {
            log.error("Failed to process Canal message: {}", e.getMessage(), e);
            throw e;
        }
    }
}
