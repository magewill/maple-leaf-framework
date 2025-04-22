package cn.maple.rabbitmq.service;

import cn.maple.core.framework.service.GXBusinessService;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;

/**
 * RabbitMQ消息发送服务接口
 * <p>
 * 该接口定义了向RabbitMQ发送消息的相关方法，提供了统一的消息发送入口。
 * 通过该服务可以将消息发送到指定的交换机和路由键，支持消息确认和返回机制。
 * </p>
 * <p>
 * 使用该服务可以简化RabbitMQ消息发送的流程，统一消息格式和发送逻辑，
 * 便于后续的扩展和维护。
 * </p>
 * 
 * @author maple
 */
public interface GXSendRabbitMQService extends GXBusinessService {
    /**
     * 发送常规消息到RabbitMQ
     * <p>
     * 将消息发送到指定的交换机和路由键，支持消息确认和返回机制。
     * 消息内容、交换机、路由键等信息通过GXRabbitMQMessageReqDto对象传入。
     * </p>
     * <p>
     * 该方法是线程安全的，可以在多线程环境下调用。
     * </p>
     *
     * @param messageReqDto 待发送的消息请求对象，包含消息内容、交换机、路由键等信息
     */
    void sendNormalMessage(GXRabbitMQMessageReqDto messageReqDto);
}