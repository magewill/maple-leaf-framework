package cn.maple.dubbo.zk.filter;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.springframework.core.Ordered;

import java.util.Objects;

/**
 * Provider-side Dubbo filter that restores TraceId for service execution and response propagation.
 */
@Activate(group = {CommonConstants.PROVIDER}, order = Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class GXDubboServerTraceIdFilter implements Filter {
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String originalTraceId = GXTraceIdContextUtils.getNullableTraceId();
        try {
            String traceId = resolveTraceId(invocation);
            String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
            log.info("[{} --->> Dubbo Service] receive TraceId: {}", appName, traceId);

            GXTraceIdContextUtils.putTraceId(traceId);
            invocation.setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
            RpcContext.getClientAttachment().setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
            RpcContext.getServerAttachment().setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);

            Result result = invoker.invoke(invocation);
            attachTraceId(result, traceId);
            return result;
        } catch (Exception e) {
            log.error("Failed to process Dubbo server request", e);
            throw e;
        } finally {
            GXTraceIdContextUtils.restoreTraceId(originalTraceId);
        }
    }

    private String resolveTraceId(Invocation invocation) {
        String traceId = getInvocationAttachment(invocation);
        if (CharSequenceUtil.isNotBlank(traceId)) {
            return traceId;
        }
        traceId = RpcContext.getServerAttachment().getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        if (CharSequenceUtil.isNotBlank(traceId)) {
            return traceId;
        }
        traceId = RpcContext.getClientAttachment().getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        if (CharSequenceUtil.isNotBlank(traceId)) {
            return traceId;
        }
        traceId = GXTraceIdContextUtils.getTraceId();
        if (CharSequenceUtil.isNotBlank(traceId)) {
            return traceId;
        }
        traceId = GXTraceIdContextUtils.generateTraceId();
        log.debug("Generated TraceId in Dubbo server filter: {}", traceId);
        return traceId;
    }

    private String getInvocationAttachment(Invocation invocation) {
        Object traceId = invocation.getObjectAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        if (Objects.isNull(traceId)) {
            traceId = invocation.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        }
        return Objects.toString(traceId, null);
    }

    private void attachTraceId(Result result, String traceId) {
        if (Objects.isNull(result) || CharSequenceUtil.isBlank(traceId)) {
            return;
        }
        if (result instanceof AsyncRpcResult) {
            result.whenCompleteWithContext((response, throwable) -> attachTraceIdWithContext(response, traceId));
            return;
        }
        result.setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
    }

    private void attachTraceIdWithContext(Result result, String traceId) {
        String originalTraceId = GXTraceIdContextUtils.getNullableTraceId();
        try {
            GXTraceIdContextUtils.putTraceId(traceId);
            attachTraceId(result, traceId);
        } finally {
            GXTraceIdContextUtils.restoreTraceId(originalTraceId);
        }
    }
}
