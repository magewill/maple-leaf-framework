package cn.maple.rocketmq.dispatcher.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.rocketmq.dispatcher.GXCdcDispatchStatistics;
import cn.maple.rocketmq.dispatcher.GXCdcMessageDispatcher;
import cn.maple.rocketmq.dto.inner.GXCdcEvent;
import cn.maple.rocketmq.dto.inner.GXCdcOperation;
import cn.maple.rocketmq.exception.GXCdcDispatchException;
import cn.maple.rocketmq.listener.GXCdcEventListener;
import lombok.extern.log4j.Log4j2;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
@Service
@ConditionalOnProperty(value = "maple.framework.cdc.enable", havingValue = "true")
public class GXCdcMessageDispatcherImpl implements GXCdcMessageDispatcher {
    private static final String LEGACY_ACTIVE_STATUS_PROPERTY = "cdc.active.status";

    private static final String ACTIVE_STATUS_PROPERTY = "maple.framework.cdc.active-status";

    private static final String DEAD_LETTER_TOPIC_PROPERTY = "maple.framework.cdc.dead-letter-topic";

    private static final String CDC_ACTIVE_STATUS_ENABLED = "enabled";

    private static final String CDC_ACTIVE_STATUS_DISABLED = "disabled";

    private final Environment environment;

    private final ObjectProvider<GXCdcEventListener> cdcEventListeners;

    private final GXCdcDispatchStatistics statistics;

    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;

    private final AtomicBoolean legacyKeyHintLogged = new AtomicBoolean(false);

    public GXCdcMessageDispatcherImpl(Environment environment,
                                      ObjectProvider<GXCdcEventListener> cdcEventListeners,
                                      GXCdcDispatchStatistics statistics,
                                      ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider) {
        this.environment = environment;
        this.cdcEventListeners = cdcEventListeners;
        this.statistics = statistics;
        this.rocketMQTemplateProvider = rocketMQTemplateProvider;
    }

    @Override
    public void dispatch(@Nullable String message, @Nullable String bizType) {
        if (!isCdcActive()) {
            statistics.recordSkipped();
            log.warn("CDC message ignored because CDC is not active. bizType={}", bizType);
            return;
        }
        if (message == null || CharSequenceUtil.isBlank(message)) {
            statistics.recordParseFailure();
            log.warn("CDC message ignored because message is blank. bizType={}", bizType);
            forwardToDeadLetter(message, bizType);
            return;
        }

        GXCdcEvent event = parseEvent(message, bizType);
        if (event == null) {
            statistics.recordParseFailure();
            forwardToDeadLetter(message, bizType);
            return;
        }

        GXCdcOperation operation = event.getOperation();
        if (operation == null) {
            statistics.recordUnknownOperation();
            log.warn("CDC message ignored because operation is unsupported. op={}, bizType={}",
                    event.getOp(), event.getBizType());
            forwardToDeadLetter(message, event.getBizType());
            return;
        }

        List<GXCdcEventListener> matchedListeners = resolveMatchedListeners(event);
        if (matchedListeners.isEmpty()) {
            statistics.recordNoTarget();
            log.debug("CDC message has no matched listener. bizType={}, op={}", event.getBizType(), event.getOp());
            return;
        }

        List<Throwable> failures = new ArrayList<>();
        for (GXCdcEventListener listener : matchedListeners) {
            try {
                dispatchToListener(listener, operation, event);
            } catch (Exception e) {
                failures.add(e);
                log.error("CDC listener failed. listener={}, bizType={}, op={}, error={}",
                        listener.getClass().getName(), event.getBizType(), event.getOp(), e.getMessage(), e);
            }
        }
        if (!failures.isEmpty()) {
            statistics.recordListenerFailure();
            throw new GXCdcDispatchException(event.getBizType(), event.getOp(), event.getTsMs(), failures,
                    failures.get(0));
        }
        statistics.recordSuccess();
    }

    private List<GXCdcEventListener> resolveMatchedListeners(GXCdcEvent event) {
        List<GXCdcEventListener> matchedListeners = new ArrayList<>();
        for (GXCdcEventListener listener : cdcEventListeners.orderedStream().toList()) {
            if (listener.supports(event) && !listener.shouldIgnore(event)) {
                matchedListeners.add(listener);
            }
        }
        return matchedListeners;
    }

    private boolean isCdcActive() {
        String cdcActiveStatus = environment.getProperty(ACTIVE_STATUS_PROPERTY);
        if (CharSequenceUtil.isBlank(cdcActiveStatus)) {
            cdcActiveStatus = environment.getProperty(LEGACY_ACTIVE_STATUS_PROPERTY, CDC_ACTIVE_STATUS_DISABLED);
            if (environment.containsProperty(LEGACY_ACTIVE_STATUS_PROPERTY) && legacyKeyHintLogged.compareAndSet(false, true)) {
                log.debug("Property {} is deprecated. Use {} instead.", LEGACY_ACTIVE_STATUS_PROPERTY, ACTIVE_STATUS_PROPERTY);
            }
        }
        return CDC_ACTIVE_STATUS_ENABLED.equalsIgnoreCase(cdcActiveStatus.trim());
    }

    private void forwardToDeadLetter(@Nullable String message, @Nullable String bizType) {
        String deadLetterTopic = environment.getProperty(DEAD_LETTER_TOPIC_PROPERTY);
        if (CharSequenceUtil.isBlank(deadLetterTopic) || message == null || CharSequenceUtil.isBlank(message)) {
            return;
        }
        RocketMQTemplate rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
        if (rocketMQTemplate == null) {
            log.warn("CDC dead letter topic is configured but no RocketMQTemplate is available. topic={}", deadLetterTopic);
            return;
        }
        try {
            rocketMQTemplate.sendOneWay(deadLetterTopic, MessageBuilder.withPayload(message).build());
            statistics.recordDeadLetter();
            log.warn("CDC message forwarded to dead letter topic. topic={}, bizType={}", deadLetterTopic, bizType);
        } catch (Exception e) {
            log.error("Failed to forward CDC message to dead letter topic. topic={}, error={}",
                    deadLetterTopic, e.getMessage(), e);
        }
    }

    private @Nullable GXCdcEvent parseEvent(String message, @Nullable String bizType) {
        try {
            Dict root = JSONUtil.toBean(message, Dict.class);
            Dict payload = resolvePayload(root);
            if (payload == null) {
                log.warn("CDC message ignored because payload is empty. bizType={}", bizType);
                return null;
            }
            return GXCdcEvent.fromPayload(payload, bizType, message);
        } catch (Exception e) {
            log.warn("CDC message ignored because parse failed. bizType={}, error={}", bizType, e.getMessage());
            return null;
        }
    }

    private @Nullable Dict resolvePayload(Dict root) {
        Dict payload = Convert.convert(Dict.class, root.getObj("payload"));
        return payload == null ? root : payload;
    }

    private void dispatchToListener(GXCdcEventListener listener, GXCdcOperation operation, GXCdcEvent event) {
        switch (operation) {
            case CREATE -> listener.onCreate(event);
            case UPDATE -> listener.onUpdate(event);
            case DELETE -> listener.onDelete(event);
            case READ -> listener.onRead(event);
        }
    }
}
