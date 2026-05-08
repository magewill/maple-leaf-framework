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

            log.debug("Start sending normal message. topic={}, tag={}", messageRequest.topic(), messageRequest.tag());
            SendResult sendResult = rocketMQTemplate.syncSend(destination, message);
            checkSendResult("Normal message", sendResult);
            log.info("Normal message sent. msgId={}", sendResult.getMsgId());
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send normal message: {}", e.getMessage(), e);
            throw new GXBusinessException("Failed to send normal message: " + e.getMessage(), e);
        }
    }

    @Override
    public String sendDelayMessage(GXRocketMQMessageReqDto messageReqDto) {
        MessageRequest messageRequest = MessageRequest.from(messageReqDto);
        try {
            if (messageRequest.deliverTime() <= 0) {
                throw new GXBusinessException("Delay time must be greater than 0 seconds");
            }

            Message<String> message = buildMessage(messageRequest);
            String destination = getDestination(messageRequest);
            long deliveryTimeMills = getDeliveryTimeMills(messageRequest.deliverTime());

            log.debug("Start sending delay message. topic={}, tag={}, delaySeconds={}",
                    messageRequest.topic(), messageRequest.tag(), messageRequest.deliverTime());

            SendResult sendResult = rocketMQTemplate.syncSendDeliverTimeMills(destination, message, deliveryTimeMills);
            checkSendResult("Delay message", sendResult);

            log.info("Delay message sent. msgId={}, deliveryTimeMillis={}", sendResult.getMsgId(), deliveryTimeMills);
            return sendResult.getMsgId();
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send delay message: {}", e.getMessage(), e);
            throw new GXBusinessException("Failed to send delay message: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean syncSend(GXRocketMQMessageReqDto messageReqDto) {
        MessageRequest messageRequest = MessageRequest.from(messageReqDto);
        try {
            Message<String> message = buildMessage(messageRequest);
            String destination = getDestination(messageRequest);

            log.debug("Start sending sync message. topic={}, tag={}", messageRequest.topic(), messageRequest.tag());

            SendResult sendResult = rocketMQTemplate.syncSend(destination, message);
            checkSendResult("Sync message", sendResult);
            log.info("Sync message sent. msgId={}", sendResult.getMsgId());
            return true;
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send sync message: {}", e.getMessage(), e);
            throw new GXBusinessException("Failed to send sync message: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean sendAsync(GXRocketMQMessageReqDto messageReqDto) {
        MessageRequest messageRequest = MessageRequest.from(messageReqDto);
        try {
            Message<String> message = buildMessage(messageRequest);
            String destination = getDestination(messageRequest);

            log.debug("Start sending async message. topic={}, tag={}", messageRequest.topic(), messageRequest.tag());

            rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    if (!isSendOk(sendResult)) {
                        log.error("Async message send status is not ok. topic={}, tag={}, status={}, msgId={}",
                                messageRequest.topic(), messageRequest.tag(), getSendStatus(sendResult),
                                sendResult == null ? null : sendResult.getMsgId());
                        return;
                    }
                    log.info("Async message sent. topic={}, tag={}, msgId={}",
                            messageRequest.topic(), messageRequest.tag(), sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable throwable) {
                    String errorMessage = throwable == null ? null : throwable.getMessage();
                    log.error("Failed to send async message. topic={}, tag={}, error={}",
                            messageRequest.topic(), messageRequest.tag(), errorMessage, throwable);
                }
            });

            log.debug("Async message send request submitted.");
            return true;
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to submit async message send request: {}", e.getMessage(), e);
            throw new GXBusinessException("Failed to submit async message send request: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean sendOneway(GXRocketMQMessageReqDto messageReqDto) {
        MessageRequest messageRequest = MessageRequest.from(messageReqDto);
        try {
            Message<String> message = buildMessage(messageRequest);
            String destination = getDestination(messageRequest);

            log.debug("Start sending oneway message. topic={}, tag={}", messageRequest.topic(), messageRequest.tag());

            rocketMQTemplate.sendOneWay(destination, message);

            log.info("Oneway message send operation completed. topic={}, tag={}", messageRequest.topic(), messageRequest.tag());
            return true;
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send oneway message: {}", e.getMessage(), e);
            throw new GXBusinessException("Failed to send oneway message: " + e.getMessage(), e);
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
            throw new GXBusinessException("Delay time is too large to calculate delivery time", e);
        }
    }

    private void checkSendResult(String messageType, SendResult sendResult) {
        if (!isSendOk(sendResult)) {
            throw new GXBusinessException(CharSequenceUtil.format("{} send failed. status={}, msgId={}",
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
                throw new GXBusinessException("Message request must not be null");
            }
            if (CharSequenceUtil.isBlank(messageReqDto.getTopic())) {
                throw new GXBusinessException("Message topic must not be blank");
            }
            if (CharSequenceUtil.isBlank(messageReqDto.getBody())) {
                throw new GXBusinessException("Message body must not be blank");
            }

            String topic = messageReqDto.getTopic().trim();
            String tag = CharSequenceUtil.blankToDefault(messageReqDto.getTag(), "").trim();
            String messageKey = CharSequenceUtil.blankToDefault(messageReqDto.getMessageKey(), "").trim();
            return new MessageRequest(topic, tag, messageReqDto.getBody(), messageReqDto.getDeliverTime(), messageKey);
        }
    }
}
