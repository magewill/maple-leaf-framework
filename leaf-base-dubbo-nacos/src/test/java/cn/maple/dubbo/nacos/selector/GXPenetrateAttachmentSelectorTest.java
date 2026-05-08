package cn.maple.dubbo.nacos.selector;

import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.RpcContextAttachment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GXPenetrateAttachmentSelectorTest {
    private final GXPenetrateAttachmentSelector selector = new GXPenetrateAttachmentSelector();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void selectUsesInvocationTraceIdFirstAndOnlyReturnsTraceId() {
        Invocation invocation = invocationWithTraceId("invocation-trace");
        RpcContextAttachment clientAttachment = attachmentWithTraceId("client-trace");
        RpcContextAttachment serverAttachment = attachmentWithTraceId("server-trace");

        Map<String, Object> selected = selector.select(invocation, clientAttachment, serverAttachment);

        assertEquals("invocation-trace", selected.get(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals(1, selected.size());
        assertFalse(selected.containsKey("author"));
        assertFalse(selected.containsKey("SPC"));
    }

    @Test
    void selectReverseFallsBackToServerThenClientThenThreadLocal() {
        Invocation invocation = invocationWithTraceId(null);
        RpcContextAttachment clientAttachment = attachmentWithTraceId("client-trace");
        RpcContextAttachment serverAttachment = attachmentWithTraceId("server-trace");

        Map<String, Object> selected = selector.selectReverse(invocation, clientAttachment, serverAttachment);

        assertEquals("server-trace", selected.get(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void selectUsesThreadLocalWhenAttachmentsAreEmpty() {
        GXTraceIdContextUtils.putTraceId("thread-trace");
        Invocation invocation = invocationWithTraceId(null);

        Map<String, Object> selected = selector.select(invocation, null, null);

        assertEquals("thread-trace", selected.get(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    private Invocation invocationWithTraceId(String traceId) {
        Invocation invocation = mock(Invocation.class);
        when(invocation.getObjectAttachment(GXTraceIdContextUtils.TRACE_ID_KEY)).thenReturn(traceId);
        when(invocation.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY)).thenReturn(traceId);
        return invocation;
    }

    private RpcContextAttachment attachmentWithTraceId(String traceId) {
        RpcContextAttachment attachment = mock(RpcContextAttachment.class);
        when(attachment.getObjectAttachment(GXTraceIdContextUtils.TRACE_ID_KEY)).thenReturn(traceId);
        when(attachment.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY)).thenReturn(traceId);
        return attachment;
    }
}
