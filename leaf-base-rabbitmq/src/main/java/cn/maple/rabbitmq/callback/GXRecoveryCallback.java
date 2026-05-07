package cn.maple.rabbitmq.callback;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

@FunctionalInterface
public interface GXRecoveryCallback extends RabbitTemplate.RecoveryCallback {
    @Override
    Object recover(Throwable throwable);
}
