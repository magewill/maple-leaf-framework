package cn.maple.rocketmq.dispatcher.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.rocketmq.dispatcher.GXCdcMessageDispatcher;
import cn.maple.rocketmq.dto.inner.GXCdcEvent;
import cn.maple.rocketmq.dto.inner.GXCdcOperation;
import cn.maple.rocketmq.listener.GXCdcEventListener;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
@ConditionalOnProperty(value = "maple.framework.cdc.enable", havingValue = "true")
public class GXCdcMessageDispatcherImpl implements GXCdcMessageDispatcher {
    private static final String CDC_ACTIVE_STATUS_PROPERTY = "cdc.active.status";

    private static final String CDC_ACTIVE_STATUS_ENABLED = "enabled";

    private final Environment environment;

    private final List<GXCdcEventListener> cdcEventListeners;

    public GXCdcMessageDispatcherImpl(Environment environment, List<GXCdcEventListener> cdcEventListeners) {
        this.environment = environment;
        this.cdcEventListeners = cdcEventListeners;
    }

    @Override
    public void dispatch(@Nullable String message, @Nullable String bizType) {
        if (!isCdcActive()) {
            log.debug("CDC message ignored because {} is not enabled.", CDC_ACTIVE_STATUS_PROPERTY);
            return;
        }
        String rawMessage = message;
        if (rawMessage == null || CharSequenceUtil.isBlank(rawMessage)) {
            log.warn("CDC message ignored because message is blank.");
            return;
        }

        GXCdcEvent event = parseEvent(rawMessage, bizType);
        if (event == null) {
            return;
        }

        GXCdcOperation operation = event.getOperation();
        if (operation == null) {
            log.warn("CDC message ignored because operation is unsupported. op={}, bizType={}", event.getOp(), event.getBizType());
            return;
        }

        for (GXCdcEventListener listener : cdcEventListeners) {
            if (listener.supports(event) && !listener.shouldIgnore(event)) {
                dispatchToListener(listener, operation, event);
            }
        }
    }

    private boolean isCdcActive() {
        String cdcActiveStatus = environment.getProperty(CDC_ACTIVE_STATUS_PROPERTY, "disabled");
        return CDC_ACTIVE_STATUS_ENABLED.equalsIgnoreCase(cdcActiveStatus.trim());
    }

    private @Nullable GXCdcEvent parseEvent(String message, @Nullable String bizType) {
        try {
            Dict root = JSONUtil.toBean(message, Dict.class);
            Dict payload = resolvePayload(root);
            if (payload == null) {
                log.warn("CDC message ignored because payload is empty.");
                return null;
            }
            return GXCdcEvent.fromPayload(payload, bizType, message);
        } catch (Exception e) {
            log.error("CDC message ignored because parse failed: {}", e.getMessage(), e);
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
