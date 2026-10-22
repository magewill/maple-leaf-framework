package cn.maple.rocketmq.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.impl.GXBusinessServiceImpl;
import cn.maple.core.framework.util.GXMdcThreadUtils;
import cn.maple.rocketmq.dto.inner.GXRocketMQMessageReqDto;
import cn.maple.rocketmq.handler.GXRocketMQAsyncSendFailureContext;
import cn.maple.rocketmq.handler.GXRocketMQAsyncSendFailureHandler;
import cn.maple.rocketmq.service.GXSendRocketMQService;
import lombok.extern.log4j.Log4j2;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Log4j2
@Service
public class GXSendRocketMQServiceImpl extends GXBusinessServiceImpl implements GXSendRocketMQService {
    private static final Pattern TOPIC_PATTERN = Pattern.compile("^[%a-zA-Z0-9_-]{1,127}$");

    private static final int MAX_TAG_LENGTH = 128;

    private static final int MAX_MESSAGE_KEY_LENGTH = 255;

    private static final int DEFAULT_MAX_MESSAGE_SIZE = 4 * 1024 * 1024;

    private static final String MAX_MESSAGE_SIZE_KEY = "rocketmq.producer.max-message-size";

    private static final String NULL_BODY_LITERAL = "null";

    private final RocketMQTemplate rocketMQTemplate;

    private final Environment environment;

    private final ObjectProvider<GXRocketMQAsyncSendFailureHandler> asyncSendFailureHandlers;

    public GXSendRocketMQServiceImpl(RocketMQTemplate rocketMQTemplate, Environment environment,
                                     ObjectProvider<GXRocketMQAsyncSendFailureHandler> asyncSendFailureHandlers) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.environment = environment;
        this.asyncSendFailureHandlers = asyncSendFailureHandlers;
    }

    @Override
    public void sendNormalMessage(GXRocketMQMessageReqDto messageReqDto) {
        MessageRequest messageRequest = toMessageRequest(messageReqDto);
        try {
            Message<String> message = buildMessage(messageRequest);
            String destination = getDestination(messageRequest);

            log.debug("Start sending normal message. topic={}, tag={}", messageRequest.topic(), messageRequest.tag());
            SendResult sendResult = rocketMQTemplate.syncSend(destination, message);
            checkSendResult("Normal message", destination, messageRequest.messageKey(), sendResult);
            log.debug("Normal message sent. msgId={}", sendResult.getMsgId());
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send normal message: {}", e.getMessage(), e);
            throw new GXBusinessException("Failed to send normal message: " + e.getMessage(), e);
        }
    }

    @Override
    public String sendDelayMessage(GXRocketMQMessageReqDto messageReqDto) {
        MessageRequest messageRequest = toMessageRequest(messageReqDto);
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
            checkSendResult("Delay message", destination, messageRequest.messageKey(), sendResult);

            log.debug("Delay message sent. msgId={}, deliveryTimeMillis={}", sendResult.getMsgId(), deliveryTimeMills);
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
        MessageRequest messageRequest = toMessageRequest(messageReqDto);
        try {
            Message<String> message = buildMessage(messageRequest);
            String destination = getDestination(messageRequest);

            log.debug("Start sending sync message. topic={}, tag={}", messageRequest.topic(), messageRequest.tag());

            SendResult sendResult = rocketMQTemplate.syncSend(destination, message);
            checkSendResult("Sync message", destination, messageRequest.messageKey(), sendResult);
            log.debug("Sync message sent. msgId={}", sendResult.getMsgId());
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
        MessageRequest messageRequest = toMessageRequest(messageReqDto);
        try {
            Message<String> message = buildMessage(messageRequest);
            String destination = getDestination(messageRequest);

            log.debug("Start sending async message. topic={}, tag={}", messageRequest.topic(), messageRequest.tag());
            Map<String, String> mdcContext = GXMdcThreadUtils.getMdcContext();

            rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    GXMdcThreadUtils.wrap(() -> handleAsyncSuccess(messageRequest, sendResult), mdcContext).run();
                }

                @Override
                public void onException(Throwable throwable) {
                    GXMdcThreadUtils.wrap(() -> handleAsyncException(messageRequest, throwable), mdcContext).run();
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
        MessageRequest messageRequest = toMessageRequest(messageReqDto);
        try {
            Message<String> message = buildMessage(messageRequest);
            String destination = getDestination(messageRequest);

            log.debug("Start sending oneway message. topic={}, tag={}", messageRequest.topic(), messageRequest.tag());

            rocketMQTemplate.sendOneWay(destination, message);

            log.debug("Oneway message send operation completed. topic={}, tag={}", messageRequest.topic(), messageRequest.tag());
            return true;
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send oneway message: {}", e.getMessage(), e);
            throw new GXBusinessException("Failed to send oneway message: " + e.getMessage(), e);
        }
    }

    private MessageRequest toMessageRequest(GXRocketMQMessageReqDto messageReqDto) {
        return MessageRequest.from(messageReqDto, maxMessageBytes());
    }

    private int maxMessageBytes() {
        Integer configured = environment.getProperty(MAX_MESSAGE_SIZE_KEY, Integer.class);
        if (configured == null || configured <= 0) {
            return DEFAULT_MAX_MESSAGE_SIZE;
        }
        return configured;
    }

    private void handleAsyncSuccess(MessageRequest messageRequest, SendResult sendResult) {
        if (!isSendOk(sendResult)) {
            log.error("Async message send status is not ok. destination={}, messageKey={}, status={}, msgId={}",
                    getDestination(messageRequest), messageRequest.messageKey(), getSendStatus(sendResult),
                    sendResult == null ? null : sendResult.getMsgId());
            notifyFailure(new GXRocketMQAsyncSendFailureContext(messageRequest.topic(), messageRequest.tag(),
                    messageRequest.messageKey(), sendResult == null ? null : sendResult.getMsgId(),
                    getSendStatus(sendResult),
                    sendResult == null ? new IllegalStateException("Async send returned null result") : null));
            return;
        }
        log.debug("Async message sent. topic={}, tag={}, msgId={}",
                messageRequest.topic(), messageRequest.tag(), sendResult.getMsgId());
    }

    private void handleAsyncException(MessageRequest messageRequest, Throwable throwable) {
        Throwable cause = throwable == null ? new IllegalStateException("Async send failed without cause") : throwable;
        log.error("Failed to send async message. destination={}, messageKey={}, error={}",
                getDestination(messageRequest), messageRequest.messageKey(), cause.getMessage(), cause);
        notifyFailure(new GXRocketMQAsyncSendFailureContext(messageRequest.topic(), messageRequest.tag(),
                messageRequest.messageKey(), null, null, cause));
    }

    private void notifyFailure(GXRocketMQAsyncSendFailureContext context) {
        List<GXRocketMQAsyncSendFailureHandler> handlers = asyncSendFailureHandlers.orderedStream().toList();
        if (handlers.isEmpty()) {
            log.debug("No async send failure handler registered. messageKey={}", context.messageKey());
            return;
        }
        for (GXRocketMQAsyncSendFailureHandler handler : handlers) {
            try {
                handler.onFailure(context);
            } catch (Exception e) {
                log.error("Async send failure handler threw. handler={}, messageKey={}",
                        handler.getClass().getName(), context.messageKey(), e);
            }
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

    private void checkSendResult(String messageType, String destination, String messageKey, SendResult sendResult) {
        if (!isSendOk(sendResult)) {
            log.error("{} send failed. destination={}, messageKey={}, status={}, msgId={}",
                    messageType, destination, messageKey, getSendStatus(sendResult),
                    sendResult == null ? null : sendResult.getMsgId());
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
        private static MessageRequest from(GXRocketMQMessageReqDto messageReqDto, int maxMessageBytes) {
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
            if (!TOPIC_PATTERN.matcher(topic).matches()) {
                throw new GXBusinessException("Message topic contains illegal characters or is longer than 127: " + topic);
            }

            String tag = CharSequenceUtil.blankToDefault(messageReqDto.getTag(), "").trim();
            if (tag.length() > MAX_TAG_LENGTH) {
                throw new GXBusinessException("Message tag is longer than " + MAX_TAG_LENGTH + ": " + tag);
            }
            if (tag.contains(":")) {
                throw new GXBusinessException("Message tag must not contain ':': " + tag);
            }

            String messageKey = CharSequenceUtil.blankToDefault(messageReqDto.getMessageKey(), "").trim();
            if (messageKey.length() > MAX_MESSAGE_KEY_LENGTH) {
                throw new GXBusinessException("Message messageKey is longer than " + MAX_MESSAGE_KEY_LENGTH);
            }

            String body = messageReqDto.getBody();
            if (NULL_BODY_LITERAL.equals(body.trim())) {
                throw new GXBusinessException("Message body must not be the literal null string");
            }
            int bodyBytes = body.getBytes(StandardCharsets.UTF_8).length;
            if (bodyBytes > maxMessageBytes) {
                throw new GXBusinessException("Message body is too large: " + bodyBytes + " bytes, max "
                        + maxMessageBytes);
            }

            return new MessageRequest(topic, tag, body, messageReqDto.getDeliverTime(), messageKey);
        }
    }
}
