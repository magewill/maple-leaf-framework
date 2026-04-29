package cn.maple.rocketmq.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.impl.GXBusinessServiceImpl;
import cn.maple.rocketmq.dto.inner.GXRocketMQMessageReqDto;
import cn.maple.rocketmq.service.GXSendRocketMQService;
import jakarta.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Log4j2
@Service
public class GXSendRocketMQServiceImpl extends GXBusinessServiceImpl implements GXSendRocketMQService {
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void sendNormalMessage(GXRocketMQMessageReqDto messageReqDto) {
        try {
            Message<String> message = buildMessage(messageReqDto);
            String destination = getDestination(messageReqDto);

            log.info("发送普通消息开始，主题: {}, 标签: {}", messageReqDto.getTopic(), messageReqDto.getTag());
            SendResult sendResult = rocketMQTemplate.syncSend(destination, message);
            checkSendResult("普通消息", sendResult);
            log.info("普通消息发送成功，消息ID: {}", sendResult.getMsgId());
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("普通消息发送失败: {}", e.getMessage(), e);
            throw new GXBusinessException("发送普通消息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String sendDelayMessage(GXRocketMQMessageReqDto messageReqDto) {
        try {
            Message<String> message = buildMessage(messageReqDto);
            if (messageReqDto.getDeliverTime() <= 0) {
                throw new GXBusinessException("延迟时间必须大于0秒");
            }
            String destination = getDestination(messageReqDto);

            log.info("发送延时消息开始，主题: {}, 标签: {}, 延迟: {}秒",
                    messageReqDto.getTopic(), messageReqDto.getTag(), messageReqDto.getDeliverTime());
            long deliveryTimeMills = System.currentTimeMillis() + messageReqDto.getDeliverTime() * 1000L;

            SendResult sendResult = rocketMQTemplate.syncSendDeliverTimeMills(
                    destination, message, deliveryTimeMills);
            checkSendResult("延迟消息", sendResult);

            log.info("延迟消息发送完毕，消息ID: {}, 投递时间: {}", sendResult.getMsgId(),
                    deliveryTimeMills);
            return sendResult.getMsgId();
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("延迟消息发送失败: {}", e.getMessage(), e);
            throw new GXBusinessException("发送延迟消息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean syncSend(GXRocketMQMessageReqDto messageReqDto) {
        try {
            Message<String> message = buildMessage(messageReqDto);
            String destination = getDestination(messageReqDto);

            log.info("同步发送消息开始，主题: {}, 标签: {}", messageReqDto.getTopic(), messageReqDto.getTag());

            SendResult sendResult = rocketMQTemplate.syncSend(destination, message);
            checkSendResult("同步消息", sendResult);
            log.info("同步发送消息成功，消息ID: {}", sendResult.getMsgId());
            return true;
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("同步发送消息失败: {}", e.getMessage(), e);
            throw new GXBusinessException("同步发送消息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean sendAsync(GXRocketMQMessageReqDto messageReqDto) {
        try {
            Message<String> message = buildMessage(messageReqDto);
            String destination = getDestination(messageReqDto);

            log.info("异步发送消息开始，主题: {}, 标签: {}", messageReqDto.getTopic(), messageReqDto.getTag());

            final String topic = messageReqDto.getTopic();
            final String tag = messageReqDto.getTag();
            rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    if (!isSendOk(sendResult)) {
                        log.error("异步消息发送状态异常，主题: {}, 标签: {}, 状态: {}, 消息ID: {}",
                                topic, tag, getSendStatus(sendResult), sendResult == null ? null : sendResult.getMsgId());
                        return;
                    }
                    log.info("异步消息发送成功，主题: {}, 标签: {}, 消息ID: {}",
                            topic, tag, sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("异步消息发送失败，主题: {}, 标签: {}, 错误: {}",
                            topic, tag, throwable.getMessage(), throwable);
                }
            });

            log.info("异步消息发送请求已提交");
            return true;
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("异步消息发送请求提交失败: {}", e.getMessage(), e);
            throw new GXBusinessException("异步消息发送请求提交失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean sendOneway(GXRocketMQMessageReqDto messageReqDto) {
        try {
            Message<String> message = buildMessage(messageReqDto);
            String destination = getDestination(messageReqDto);

            log.info("发送单向消息开始，主题: {}, 标签: {}", messageReqDto.getTopic(), messageReqDto.getTag());

            rocketMQTemplate.sendOneWay(destination, message);

            log.info("单向消息发送操作完成，主题: {}, 标签: {}",
                    messageReqDto.getTopic(), messageReqDto.getTag());
            return true;
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("单向消息发送失败: {}", e.getMessage(), e);
            throw new GXBusinessException("单向消息发送失败: " + e.getMessage(), e);
        }
    }

    private String getDestination(GXRocketMQMessageReqDto messageReqDto) {
        Objects.requireNonNull(messageReqDto, "消息对象不能为空");

        if (CharSequenceUtil.isBlank(messageReqDto.getTopic())) {
            throw new GXBusinessException("消息主题(Topic)不能为空");
        }
        String topic = messageReqDto.getTopic().trim();

        if (CharSequenceUtil.isBlank(messageReqDto.getTag())) {
            return topic;
        }

        return CharSequenceUtil.format("{}:{}", topic, messageReqDto.getTag().trim());
    }

    private Message<String> buildMessage(GXRocketMQMessageReqDto messageReqDto) {
        Objects.requireNonNull(messageReqDto, "消息对象不能为空");
        if (CharSequenceUtil.isBlank(messageReqDto.getBody())) {
            throw new GXBusinessException("消息内容不能为空");
        }

        MessageBuilder<String> messageBuilder = MessageBuilder.withPayload(messageReqDto.getBody());
        String messageKey = messageReqDto.getMessageKey();
        if (CharSequenceUtil.isNotBlank(messageKey)) {
            messageBuilder.setHeader(RocketMQHeaders.KEYS, messageKey.trim());
        }
        return messageBuilder.build();
    }

    private void checkSendResult(String messageType, SendResult sendResult) {
        if (!isSendOk(sendResult)) {
            throw new GXBusinessException(CharSequenceUtil.format("{}发送失败, 状态: {}, 消息ID: {}",
                    messageType, getSendStatus(sendResult), sendResult == null ? null : sendResult.getMsgId()));
        }
    }

    private boolean isSendOk(SendResult sendResult) {
        return sendResult != null && SendStatus.SEND_OK == sendResult.getSendStatus();
    }

    private SendStatus getSendStatus(SendResult sendResult) {
        return sendResult == null ? null : sendResult.getSendStatus();
    }
}
