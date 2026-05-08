package cn.maple.dubbo.nacos.filter;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
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

            return invoker.invoke(invocation);
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
}
