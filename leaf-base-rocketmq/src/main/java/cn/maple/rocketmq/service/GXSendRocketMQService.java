package cn.maple.rocketmq.service;


import cn.maple.core.framework.service.GXBusinessService;
import cn.maple.rocketmq.dto.inner.GXRocketMQMessageReqDto;

public interface GXSendRocketMQService extends GXBusinessService {
    /**
     * Sends a normal message.
     *
     * @param messageReqDto message to send
     */
    void sendNormalMessage(GXRocketMQMessageReqDto messageReqDto);

    /**
     * Sends a delayed message.
     *
     * @param messageReqDto message to send
     */
    String sendDelayMessage(GXRocketMQMessageReqDto messageReqDto);

    /**
     * Sends a message asynchronously.
     *
     * @param messageReqDto message to send
     */
    boolean sendAsync(GXRocketMQMessageReqDto messageReqDto);

    /**
     * Sends a one-way message.
     *
     * @param messageReqDto message to send
     */
    boolean sendOneway(GXRocketMQMessageReqDto messageReqDto);

    /**
     * Sends a message synchronously.
     *
     * @param messageReqDto message to send
     */
    boolean syncSend(GXRocketMQMessageReqDto messageReqDto);
}
