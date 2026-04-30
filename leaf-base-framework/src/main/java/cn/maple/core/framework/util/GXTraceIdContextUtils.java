package cn.maple.core.framework.util;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Objects;

public class GXTraceIdContextUtils {
    public static final String TRACE_ID_KEY = "X-B3-TraceId";

    private static final Logger LOG = LoggerFactory.getLogger(GXTraceIdContextUtils.class);

    private GXTraceIdContextUtils() {
        throw new AssertionError("Utility class, cannot be instantiated");
    }

    public static String getTraceId() {
        String threadName = Thread.currentThread().getName();
        String traceId = MDC.get(TRACE_ID_KEY);
        LOG.debug("线程 {} 获取的 TraceId: {}", threadName, traceId);
        return Objects.isNull(traceId) ? "" : traceId;
    }

    public static void setTraceId(String traceId) {
        if (CharSequenceUtil.isNotBlank(traceId) && CharSequenceUtil.isBlank(MDC.get(TRACE_ID_KEY))) {
            MDC.put(TRACE_ID_KEY, traceId);
            LOG.debug("线程 {} 设置的 TraceId: {}", Thread.currentThread().getName(), traceId);
        } else {
            LOG.debug("线程 {} 未设置 TraceId: 当前已存在 TraceId 或传入的 TraceId 为空", Thread.currentThread().getName());
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
        LOG.debug("线程 {} 写入 TraceId: {}", Thread.currentThread().getName(), traceId);
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
        if (Objects.isNull(traceId)) {
            LOG.debug("线程 {} 的 MDC 中不存在 TraceId，无需移除", Thread.currentThread().getName());
            return;
        }
        String threadName = Thread.currentThread().getName();
        LOG.debug("线程 {} 移除的 TraceId: {}", threadName, traceId);
        MDC.remove(TRACE_ID_KEY);
    }

    public static void clearTraceId() {
        MDC.clear();
        LOG.debug("线程 {} 清空了 MDC", Thread.currentThread().getName());
    }

    public static String generateTraceId() {
        String threadName = Thread.currentThread().getName();
        String traceId = GXTraceIdGenerator.generateTraceId();
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
        if (CharSequenceUtil.isNotBlank(appName)) {
            traceId = CharSequenceUtil.format("{}:{}", appName, traceId);
        }
        LOG.debug("线程 {} 生成的 TraceId: {}", threadName, traceId);
        return traceId;
    }

    public static void setTraceIdIfAbsent() {
        String currentTraceId = getTraceId();
        if (CharSequenceUtil.isBlank(currentTraceId)) {
            String newTraceId = generateTraceId();
            setTraceId(newTraceId);
            LOG.debug("线程 {} 自动设置了新的 TraceId: {}", Thread.currentThread().getName(), newTraceId);
        } else {
            LOG.debug("线程 {} 已存在 TraceId: {}，无需设置", Thread.currentThread().getName(), currentTraceId);
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
