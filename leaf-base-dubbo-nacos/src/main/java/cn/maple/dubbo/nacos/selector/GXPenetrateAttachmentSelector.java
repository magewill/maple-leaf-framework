package cn.maple.dubbo.nacos.selector;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.PenetrateAttachmentSelector;
import org.apache.dubbo.rpc.RpcContextAttachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Selects the TraceId attachment that should cross Dubbo request and response boundaries.
 */
@Activate(group = {CommonConstants.PROVIDER, CommonConstants.CONSUMER})
public class GXPenetrateAttachmentSelector implements PenetrateAttachmentSelector {
    private static final Logger LOG = LoggerFactory.getLogger(GXPenetrateAttachmentSelector.class);

    @Override
    public Map<String, Object> select(Invocation invocation, RpcContextAttachment clientAttachment, RpcContextAttachment serverAttachment) {
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "UnknownApp");
        LOG.debug("[{} --->> Dubbo Client Selector] select attachments", appName);

        String traceId = resolveTraceId(invocation, clientAttachment, serverAttachment);

        LOG.debug("[{} --->> Dubbo Client Selector] select TraceId: {}", appName, traceId);
        return Dict.create().set(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
    }

    @Override
    public Map<String, Object> selectReverse(Invocation invocation, RpcContextAttachment clientResponseContext, RpcContextAttachment serverResponseContext) {
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "UnknownApp");
        LOG.debug("[{} --->> Dubbo Server Selector] select reverse attachments", appName);

        String traceId = resolveTraceId(invocation, clientResponseContext, serverResponseContext);

        LOG.debug("[{} --->> Dubbo Server Selector] select reverse TraceId: {}", appName, traceId);
        return Dict.create().set(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
    }

    private String resolveTraceId(Invocation invocation, RpcContextAttachment clientAttachment, RpcContextAttachment serverAttachment) {
        String traceId = getInvocationAttachment(invocation);
        if (CharSequenceUtil.isNotBlank(traceId)) {
            return traceId;
        }
        traceId = getAttachment(serverAttachment);
        if (CharSequenceUtil.isNotBlank(traceId)) {
            return traceId;
        }
        traceId = getAttachment(clientAttachment);
        if (CharSequenceUtil.isNotBlank(traceId)) {
            return traceId;
        }
        traceId = GXTraceIdContextUtils.getTraceId();
        if (CharSequenceUtil.isNotBlank(traceId)) {
            return traceId;
        }
        traceId = GXTraceIdContextUtils.generateTraceId();
        LOG.debug("Generated TraceId in Dubbo attachment selector: {}", traceId);
        return traceId;
    }

    private String getAttachment(RpcContextAttachment attachment) {
        if (attachment == null) {
            return null;
        }
        Object traceId = attachment.getObjectAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        if (Objects.isNull(traceId)) {
            traceId = attachment.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        }
        return Objects.toString(traceId, null);
    }

    private String getInvocationAttachment(Invocation invocation) {
        Object traceId = invocation.getObjectAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        if (Objects.isNull(traceId)) {
            traceId = invocation.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        }
        return Objects.toString(traceId, null);
    }
}
