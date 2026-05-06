package cn.maple.core.framework.util;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class GXTraceIdContextUtils {
    public static final String TRACE_ID_KEY = "X-B3-TraceId";

    private static final Logger LOG = LoggerFactory.getLogger(GXTraceIdContextUtils.class);

    private GXTraceIdContextUtils() {
        throw new AssertionError("Utility class, cannot be instantiated");
    }

    public static String getTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId == null ? "" : traceId;
    }

    public static void setTraceId(String traceId) {
        if (CharSequenceUtil.isNotBlank(traceId) && CharSequenceUtil.isBlank(MDC.get(TRACE_ID_KEY))) {
            MDC.put(TRACE_ID_KEY, traceId);
            LOG.debug("Set trace id: thread={}, traceId={}", Thread.currentThread().getName(), traceId);
        }
    }

    public static String getNullableTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public static void putTraceId(String traceId) {
        if (CharSequenceUtil.isBlank(traceId)) {
            removeTraceId();
            return;
        }
        MDC.put(TRACE_ID_KEY, traceId);
        LOG.debug("Put trace id: thread={}, traceId={}", Thread.currentThread().getName(), traceId);
    }

    public static void restoreTraceId(String traceId) {
        if (CharSequenceUtil.isBlank(traceId)) {
            removeTraceId();
        } else {
            putTraceId(traceId);
        }
    }

    public static void removeTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null) {
            return;
        }
        String threadName = Thread.currentThread().getName();
        LOG.debug("Remove trace id: thread={}, traceId={}", threadName, traceId);
        MDC.remove(TRACE_ID_KEY);
    }

    public static void clearTraceId() {
        MDC.clear();
        LOG.debug("Clear MDC: thread={}", Thread.currentThread().getName());
    }

    public static String generateTraceId() {
        String traceId = GXTraceIdGenerator.generateTraceId();
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
        if (CharSequenceUtil.isNotBlank(appName)) {
            traceId = CharSequenceUtil.format("{}:{}", appName, traceId);
        }
        LOG.debug("Generate trace id: thread={}, traceId={}", Thread.currentThread().getName(), traceId);
        return traceId;
    }

    public static void setTraceIdIfAbsent() {
        String currentTraceId = getTraceId();
        if (CharSequenceUtil.isBlank(currentTraceId)) {
            String newTraceId = generateTraceId();
            setTraceId(newTraceId);
        }
    }

    private static class GXTraceIdGenerator {
        private GXTraceIdGenerator() {
            throw new AssertionError("Utility class, cannot be instantiated");
        }

        public static String generateTraceId() {
            return IdUtil.fastSimpleUUID();
        }
    }
}
