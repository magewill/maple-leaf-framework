package cn.maple.dubbo.nacos.filter;

import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GXDubboTraceIdFilterTest {
    @AfterEach
    void tearDown() {
        MDC.clear();
        RpcContext.getClientAttachment().clearAttachments();
        RpcContext.getServerAttachment().clearAttachments();
    }

    @Test
    void clientFilterPropagatesInvocationTraceIdAndRestoresOriginalTraceId() {
        GXTraceIdContextUtils.putTraceId("original");
        Invocation invocation = invocationWithTraceId("invocation-trace");
        Invoker<?> invoker = successfulInvoker(invocation);

        new GXDubboClientTraceIdFilter().invoke(invoker, invocation);

        verify(invocation).setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, "invocation-trace");
        assertEquals("invocation-trace", RpcContext.getClientAttachment().getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals("invocation-trace", RpcContext.getServerAttachment().getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals("original", GXTraceIdContextUtils.getTraceId());
    }

    @Test
    void serverFilterUsesServerAttachmentAndRestoresTraceIdWhenInvokerThrows() {
        GXTraceIdContextUtils.putTraceId("original");
        RpcContext.getServerAttachment().setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, "server-trace");
        Invocation invocation = invocationWithTraceId(null);
        Invoker<?> invoker = mock(Invoker.class);
        when(invoker.invoke(invocation)).thenThrow(new RpcException("boom"));

        assertThrows(RpcException.class, () -> new GXDubboServerTraceIdFilter().invoke(invoker, invocation));

        verify(invocation).setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, "server-trace");
        assertEquals("original", GXTraceIdContextUtils.getTraceId());
    }

    @Test
    void clientFilterGeneratesTraceIdWhenAllSourcesAreEmpty() {
        Invocation invocation = invocationWithTraceId(null);
        Invoker<?> invoker = successfulInvoker(invocation);

        new GXDubboClientTraceIdFilter().invoke(invoker, invocation);

        String generatedTraceId = RpcContext.getClientAttachment().getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        assertFalse(generatedTraceId.isBlank());
        verify(invocation).setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, generatedTraceId);
        assertEquals("", GXTraceIdContextUtils.getTraceId());
    }

    private Invocation invocationWithTraceId(String traceId) {
        Invocation invocation = mock(Invocation.class);
        when(invocation.getObjectAttachment(GXTraceIdContextUtils.TRACE_ID_KEY)).thenReturn(traceId);
        when(invocation.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY)).thenReturn(traceId);
        return invocation;
    }

    private Invoker<?> successfulInvoker(Invocation invocation) {
        Invoker<?> invoker = mock(Invoker.class);
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(result);
        return invoker;
    }
}
