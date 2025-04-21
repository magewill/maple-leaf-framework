package cn.maple.rocketmq.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.impl.GXBusinessServiceImpl;
import cn.maple.rocketmq.dto.inner.GXRocketMQMessageReqDto;
import cn.maple.rocketmq.service.GXSendRocketMQService;
import lombok.extern.log4j.Log4j2;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Objects;

/**
 * RocketMQ消息发送服务实现类
 * <p>
 * 提供多种消息发送模式：普通消息、延迟消息、同步消息、异步消息和单向(Oneway)消息
 * 每种发送模式都有不同的可靠性和性能特点，适用于不同的业务场景
 * </p>
 * 
 * @author maple
 */
@Log4j2
@Service
public class GXSendRocketMQServiceImpl extends GXBusinessServiceImpl implements GXSendRocketMQService {
    /**
     * RocketMQ模板，用于发送各类消息
     * 由Spring自动注入，线程安全
     */
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 发送普通消息
     * <p>
     * 普通消息是最基础的消息类型，不保证消息顺序，适用于一般的业务场景
     * 该方法会同步发送消息，如果发送失败会抛出异常
     * </p>
     *
     * @param messageReqDto 待发送的消息对象，包含消息内容、主题、标签等信息
     * @throws GXBusinessException 当消息发送失败或参数无效时抛出异常
     */
    @Override
    public void sendNormalMessage(GXRocketMQMessageReqDto messageReqDto) {
        Objects.requireNonNull(messageReqDto, "消息对象不能为空");
        Objects.requireNonNull(messageReqDto.getBody(), "消息内容不能为空");
        
        log.info("发送普通消息开始，主题: {}, 标签: {}", messageReqDto.getTopic(), messageReqDto.getTag());
        try {
            String messageKey = messageReqDto.getMessageKey();
            MessageBuilder<String> messageBuilder = MessageBuilder.withPayload(messageReqDto.getBody());
            if (CharSequenceUtil.isNotEmpty(messageKey)) {
                messageBuilder.setHeader(RocketMQHeaders.KEYS, messageKey);
            }
            Message<String> message = messageBuilder.build();
            rocketMQTemplate.send(getDestination(messageReqDto), message);
            log.info("普通消息发送成功，消息内容: {}", message);
        } catch (Exception e) {
            log.error("普通消息发送失败: {}", e.getMessage(), e);
            throw new GXBusinessException("发送普通消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送延迟消息
     * <p>
     * 延迟消息是指消息发送后，不会立即投递，而是在指定的时间后才投递到消费者进行消费
     * 适用于定时任务、订单超时取消等场景
     * </p>
     *
     * @param messageReqDto 待发送的消息对象，包含消息内容、主题、标签和延迟时间等信息
     * @return 消息ID，可用于后续跟踪消息
     * @throws GXBusinessException 当消息发送失败或参数无效时抛出异常
     */
    @Override
    public String sendDelayMessage(GXRocketMQMessageReqDto messageReqDto) {
        Objects.requireNonNull(messageReqDto, "消息对象不能为空");
        Objects.requireNonNull(messageReqDto.getBody(), "消息内容不能为空");
        
        if (messageReqDto.getDeliverTime() <= 0) {
            throw new GXBusinessException("延迟时间必须大于0秒");
        }
        
        log.info("发送延时消息开始，主题: {}, 标签: {}, 延迟: {}秒", 
                messageReqDto.getTopic(), messageReqDto.getTag(), messageReqDto.getDeliverTime());
        try {
            // 处理消息到时的绝对时间（毫秒）
            long deliveryTimeMills = System.currentTimeMillis() + messageReqDto.getDeliverTime() * 1000L;
            
            String messageKey = messageReqDto.getMessageKey();
            MessageBuilder<String> messageBuilder = MessageBuilder.withPayload(messageReqDto.getBody());
            if (CharSequenceUtil.isNotEmpty(messageKey)) {
                messageBuilder.setHeader(RocketMQHeaders.KEYS, messageKey);
            }
            Message<String> message = messageBuilder.build();
            
            SendResult sendResult = rocketMQTemplate.syncSendDeliverTimeMills(
                    getDestination(messageReqDto), message, deliveryTimeMills);
            
            log.info("延迟消息发送完毕，消息ID: {}, 消息返回结果: {}", sendResult.getMsgId(), sendResult);
            return sendResult.getMsgId();
        } catch (Exception e) {
            log.error("延迟消息发送失败: {}", e.getMessage(), e);
            throw new GXBusinessException("发送延迟消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送同步消息
     * <p>
     * 同步消息是指消息发送方发出数据后，会在收到接收方发回响应之后才发下一个数据包
     * 适用于重要的通知消息，如重要通知邮件、营销短信等
     * </p>
     *
     * @param messageReqDto 待发送的消息对象
     * @return 发送是否成功
     * @throws GXBusinessException 当消息发送失败或参数无效时抛出异常
     */
    @Override
    public boolean syncSend(GXRocketMQMessageReqDto messageReqDto) {
        Objects.requireNonNull(messageReqDto, "消息对象不能为空");
        Objects.requireNonNull(messageReqDto.getBody(), "消息内容不能为空");
        
        log.info("同步发送消息开始，主题: {}, 标签: {}", messageReqDto.getTopic(), messageReqDto.getTag());
        try {
            // 获取目标地址
            String destination = getDestination(messageReqDto);
            
            // 构建消息
            String messageKey = messageReqDto.getMessageKey();
            MessageBuilder<String> messageBuilder = MessageBuilder.withPayload(messageReqDto.getBody());
            if (CharSequenceUtil.isNotEmpty(messageKey)) {
                messageBuilder.setHeader(RocketMQHeaders.KEYS, messageKey);
            }
            Message<String> message = messageBuilder.build();
            
            // 同步发送消息
            SendResult sendResult = rocketMQTemplate.syncSend(destination, message);
            log.info("同步发送消息成功，消息ID: {}, 消息返回结果: {}", sendResult.getMsgId(), sendResult);
            return true;
        } catch (Exception e) {
            log.error("同步发送消息失败: {}", e.getMessage(), e);
            throw new GXBusinessException("同步发送消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送异步消息
     * <p>
     * 异步消息是指发送方发出数据后，不等接收方发回响应，接着发送下个数据包
     * 适用于可靠性要求不高，但要求高吞吐量的场景，如日志收集
     * </p>
     *
     * @param messageReqDto 待发送的消息对象
     * @return 发送请求是否成功提交
     * @throws GXBusinessException 当消息参数无效时抛出异常
     */
    @Override
    public boolean sendAsync(GXRocketMQMessageReqDto messageReqDto) {
        Objects.requireNonNull(messageReqDto, "消息对象不能为空");
        Objects.requireNonNull(messageReqDto.getBody(), "消息内容不能为空");
        
        log.info("异步发送消息开始，主题: {}, 标签: {}", messageReqDto.getTopic(), messageReqDto.getTag());
        try {
            // 构建消息
            String messageKey = messageReqDto.getMessageKey();
            MessageBuilder<String> messageBuilder = MessageBuilder.withPayload(messageReqDto.getBody());
            if (CharSequenceUtil.isNotEmpty(messageKey)) {
                messageBuilder.setHeader(RocketMQHeaders.KEYS, messageKey);
            }
            Message<String> message = messageBuilder.build();
            
            // 异步发送消息
            rocketMQTemplate.asyncSend(getDestination(messageReqDto), message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("异步消息发送成功，消息ID: {}, 消息返回结果: {}", sendResult.getMsgId(), sendResult);
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("异步消息发送失败: {}", throwable.getMessage(), throwable);
                }
            });
            
            log.info("异步消息发送请求已提交");
            return true;
        } catch (Exception e) {
            log.error("异步消息发送请求提交失败: {}", e.getMessage(), e);
            throw new GXBusinessException("异步消息发送请求提交失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送单向(Oneway)消息
     * <p>
     * 单向消息是指只负责发送消息，不等待服务器回应且没有回调函数触发
     * 适用于不关心发送结果的场景，如日志收集
     * 相比于异步消息，单向消息发送方式耗时非常短，性能最高
     * </p>
     *
     * @param messageReqDto 待发送的消息对象
     * @return 发送请求是否成功提交
     * @throws GXBusinessException 当消息参数无效时抛出异常
     */
    @Override
    public boolean sendOneway(GXRocketMQMessageReqDto messageReqDto) {
        Objects.requireNonNull(messageReqDto, "消息对象不能为空");
        Objects.requireNonNull(messageReqDto.getBody(), "消息内容不能为空");
        
        log.info("发送单向消息开始，主题: {}, 标签: {}", messageReqDto.getTopic(), messageReqDto.getTag());
        try {
            // 构建消息
            String messageKey = messageReqDto.getMessageKey();
            MessageBuilder<String> messageBuilder = MessageBuilder.withPayload(messageReqDto.getBody());
            if (CharSequenceUtil.isNotEmpty(messageKey)) {
                messageBuilder.setHeader(RocketMQHeaders.KEYS, messageKey);
            }
            Message<String> message = messageBuilder.build();
            
            // 发送单向消息
            rocketMQTemplate.sendOneWay(getDestination(messageReqDto), message);
            
            log.info("单向消息发送成功");
            return true;
        } catch (Exception e) {
            log.error("单向消息发送失败: {}", e.getMessage(), e);
            throw new GXBusinessException("单向消息发送失败: " + e.getMessage(), e);
        }
    }

    /**
     * 组装消息发送的目标地址
     * <p>
     * 根据消息的主题(Topic)和标签(Tag)组装完整的目标地址
     * 格式为：topic:tag，如果没有tag则只返回topic
     * </p>
     *
     * @param messageReqDto 消息信息对象，包含主题和标签信息
     * @return 组装后的目标地址字符串
     * @throws GXBusinessException 当主题为空时抛出异常
     */
    private String getDestination(GXRocketMQMessageReqDto messageReqDto) {
        Objects.requireNonNull(messageReqDto, "消息对象不能为空");
        
        if (CharSequenceUtil.isEmpty(messageReqDto.getTopic())) {
            throw new GXBusinessException("消息主题(Topic)不能为空");
        }
        
        if (CharSequenceUtil.isEmpty(messageReqDto.getTag())) {
            return messageReqDto.getTopic();
        }
        
        return CharSequenceUtil.format("{}:{}", messageReqDto.getTopic(), messageReqDto.getTag());
    }
}