package cn.maple.canal.config;

import cn.maple.canal.properties.GXCanalProperties;
import jakarta.annotation.Resource;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ beans used by Canal message consumption.
 */
@Configuration
public class GXCanalConfig {
    @Resource
    private GXCanalProperties canalProperties;

    @Bean
    public Binding binding(Exchange fanoutExchange, Queue canalQueue) {
        return BindingBuilder.bind(canalQueue).to(fanoutExchange).with(canalProperties.getRoutingKey()).noargs();
    }

    @Bean
    public Exchange fanoutExchange() {
        return new FanoutExchange(canalProperties.getExchangeName(), true, false);
    }

    @Bean
    public Queue canalQueue() {
        return new Queue(canalProperties.getCanalQueueName());
    }
}
