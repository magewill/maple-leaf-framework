package cn.maple.rocketmq.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.impl.GXBusinessServiceImpl;
import cn.maple.rocketmq.dto.inner.GXRocketMQMessageReqDto;
import cn.maple.rocketmq.service.GXSendRocketMQService;
import lombok.extern.log4j.Log4j2;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class GXSendRocketMQServiceImpl extends GXBusinessServiceImpl implements GXSendRocketMQService {
    private final RocketMQTemplate rocketMQTemplate;

    public GXSendRocketMQServiceImpl(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Override
    public void sendNormalMessage(GXRocketMQMessageReqDto messageReqDto) {
        MessageRequest messageRequest = MessageRequest.from(messageReqDto);
        try {
            Message<String> message = buildMessage(messageRequest);
            String destination = getDestination(messageRequest);

            log.info("开始发送普通消息，主题: {}，标签: {}", messageRequest.topic(), messageRequest.tag());
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
        MessageRequest messageRequest = MessageRequest.from(messageReqDto);
        try {
            if (messageRequest.deliverTime() <= 0) {
                throw new GXBusinessException("延时时间必须大于0秒");
            }

            Message<String> message = buildMessage(messageRequest);
            String destination = getDestination(messageRequest);
            long deliveryTimeMills = getDeliveryTimeMills(messageRequest.deliverTime());

            log.info("开始发送延时消息，主题: {}，标签: {}，延时: {}秒",
                    messageRequest.topic(), messageRequest.tag(), messageRequest.deliverTime());

            SendResult sendResult = rocketMQTemplate.syncSendDeliverTimeMills(destination, message, deliveryTimeMills);
            checkSendResult("延时消息", sendResult);

            log.info("延时消息发送成功，消息ID: {}，投递时间: {}", sendResult.getMsgId(), deliveryTimeMills);
            return sendResult.getMsgId();
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("延时消息发送失败: {}", e.getMessage(), e);
            throw new GXBusinessException("发送延时消息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean syncSend(GXRocketMQMessageReqDto messageReqDto) {
        MessageRequest messageRequest = MessageRequest.from(messageReqDto);
        try {
            Message<String> message = buildMessage(messageRequest);
            String destination = getDestination(messageRequest);

            log.info("开始同步发送消息，主题: {}，标签: {}", messageRequest.topic(), messageRequest.tag());

            SendResult sendResult = rocketMQTemplate.syncSend(destination, message);
            checkSendResult("同步消息", sendResult);
            log.info("同步消息发送成功，消息ID: {}", sendResult.getMsgId());
            return true;
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("同步消息发送失败: {}", e.getMessage(), e);
            throw new GXBusinessException("同步消息发送失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean sendAsync(GXRocketMQMessageReqDto messageReqDto) {
        MessageRequest messageRequest = MessageRequest.from(messageReqDto);
        try {
            Message<String> message = buildMessage(messageRequest);
            String destination = getDestination(messageRequest);

            log.info("开始异步发送消息，主题: {}，标签: {}", messageRequest.topic(), messageRequest.tag());

            rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    if (!isSendOk(sendResult)) {
                        log.error("异步消息发送状态异常，主题: {}，标签: {}，状态: {}，消息ID: {}",
                                messageRequest.topic(), messageRequest.tag(), getSendStatus(sendResult),
                                sendResult == null ? null : sendResult.getMsgId());
                        return;
                    }
                    log.info("异步消息发送成功，主题: {}，标签: {}，消息ID: {}",
                            messageRequest.topic(), messageRequest.tag(), sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable throwable) {
                    String errorMessage = throwable == null ? null : throwable.getMessage();
                    log.error("异步消息发送失败，主题: {}，标签: {}，错误: {}",
                            messageRequest.topic(), messageRequest.tag(), errorMessage, throwable);
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
        MessageRequest messageRequest = MessageRequest.from(messageReqDto);
        try {
            Message<String> message = buildMessage(messageRequest);
            String destination = getDestination(messageRequest);

            log.info("开始发送单向消息，主题: {}，标签: {}", messageRequest.topic(), messageRequest.tag());

            rocketMQTemplate.sendOneWay(destination, message);

            log.info("单向消息发送操作完成，主题: {}，标签: {}", messageRequest.topic(), messageRequest.tag());
            return true;
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("单向消息发送失败: {}", e.getMessage(), e);
            throw new GXBusinessException("单向消息发送失败: " + e.getMessage(), e);
        }
    }

    private String getDestination(MessageRequest messageRequest) {
        if (CharSequenceUtil.isBlank(messageRequest.tag())) {
            return messageRequest.topic();
        }

        return CharSequenceUtil.format("{}:{}", messageRequest.topic(), messageRequest.tag());
    }

    private Message<String> buildMessage(MessageRequest messageRequest) {
        MessageBuilder<String> messageBuilder = MessageBuilder.withPayload(messageRequest.body());
        if (CharSequenceUtil.isNotBlank(messageRequest.messageKey())) {
            messageBuilder.setHeader(RocketMQHeaders.KEYS, messageRequest.messageKey());
        }
        return messageBuilder.build();
    }

    private long getDeliveryTimeMills(long deliverTimeSeconds) {
        try {
            return Math.addExact(System.currentTimeMillis(), Math.multiplyExact(deliverTimeSeconds, 1000L));
        } catch (ArithmeticException e) {
            throw new GXBusinessException("延时时间过大，无法计算投递时间", e);
        }
    }

    private void checkSendResult(String messageType, SendResult sendResult) {
        if (!isSendOk(sendResult)) {
            throw new GXBusinessException(CharSequenceUtil.format("{}发送失败，状态: {}，消息ID: {}",
                    messageType, getSendStatus(sendResult), sendResult == null ? null : sendResult.getMsgId()));
        }
    }

    private boolean isSendOk(SendResult sendResult) {
        return sendResult != null && SendStatus.SEND_OK == sendResult.getSendStatus();
    }

    private SendStatus getSendStatus(SendResult sendResult) {
        return sendResult == null ? null : sendResult.getSendStatus();
    }

    private record MessageRequest(String topic, String tag, String body, long deliverTime, String messageKey) {
        private static MessageRequest from(GXRocketMQMessageReqDto messageReqDto) {
            if (messageReqDto == null) {
                throw new GXBusinessException("消息对象不能为空");
            }
            if (CharSequenceUtil.isBlank(messageReqDto.getTopic())) {
                throw new GXBusinessException("消息主题(Topic)不能为空");
            }
            if (CharSequenceUtil.isBlank(messageReqDto.getBody())) {
                throw new GXBusinessException("消息内容不能为空");
            }

            String topic = messageReqDto.getTopic().trim();
            String tag = CharSequenceUtil.blankToDefault(messageReqDto.getTag(), "").trim();
            String messageKey = CharSequenceUtil.blankToDefault(messageReqDto.getMessageKey(), "").trim();
            return new MessageRequest(topic, tag, messageReqDto.getBody(), messageReqDto.getDeliverTime(), messageKey);
        }
    }
}
