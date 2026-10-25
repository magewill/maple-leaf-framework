package cn.maple.dubbo.zk.filter;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.dubbo.zk.selector.GXPenetrateAttachmentSelector;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GXDubboTraceIdFilterTest {
    private final GXDubboClientTraceIdFilter clientFilter = new GXDubboClientTraceIdFilter();
    private final GXDubboServerTraceIdFilter serverFilter = new GXDubboServerTraceIdFilter();

    @BeforeEach
    void setUp() {
        MDC.clear();
        RpcContext.removeContext();
        GXPenetrateAttachmentSelector.resetAppNameCache();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        RpcContext.removeContext();
    }

    @Test
    void clientPrefersInvocationAttachment() {
        RpcInvocation invocation = new RpcInvocation();
        invocation.setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, "from-invocation");
        AppResponse response = new AppResponse(invocation);

        Result result = clientFilter.invoke(invokerReturning(invocation, response), invocation);

        assertEquals("from-invocation", result.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals("from-invocation", RpcContext.getClientAttachment().getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void clientFallsBackToThreadLocalThenGenerates() {
        RpcInvocation emptyInvocation = new RpcInvocation();
        Result generated = clientFilter.invoke(invokerReturning(emptyInvocation, new AppResponse(emptyInvocation)), emptyInvocation);
        String generatedTraceId = (String) generated.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        assertFalse(CharSequenceUtil.isBlank(generatedTraceId));

        RpcContext.removeContext();
        GXTraceIdContextUtils.putTraceId("from-mdc");
        RpcInvocation secondInvocation = new RpcInvocation();
        Result fromMdc = clientFilter.invoke(invokerReturning(secondInvocation, new AppResponse(secondInvocation)), secondInvocation);
        assertEquals("from-mdc", fromMdc.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void serverPrefersInvocationAttachment() {
        RpcInvocation invocation = new RpcInvocation();
        invocation.setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, "server-invocation");
        AppResponse response = new AppResponse(invocation);

        Result result = serverFilter.invoke(invokerReturning(invocation, response), invocation);

        assertEquals("server-invocation", result.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals("server-invocation", RpcContext.getServerAttachment().getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void serverGeneratesTraceIdWhenNothingAvailable() {
        RpcInvocation invocation = new RpcInvocation();
        Result result = serverFilter.invoke(invokerReturning(invocation, new AppResponse(invocation)), invocation);

        Object traceId = result.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        assertNotNull(traceId);
        assertFalse(CharSequenceUtil.isBlank((String) traceId));
    }

    @Test
    void restoresMdcAfterSuccessfulInvocation() {
        GXTraceIdContextUtils.putTraceId("outer-trace");
        RpcInvocation invocation = new RpcInvocation();

        serverFilter.invoke(invokerReturning(invocation, new AppResponse(invocation)), invocation);

        assertEquals("outer-trace", MDC.get(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void restoresMdcAfterInvocationThrows() {
        RpcInvocation invocation = new RpcInvocation();
        Invoker<?> invoker = mock(Invoker.class);
        when(invoker.invoke(invocation)).thenThrow(new RpcException("boom"));

        assertThrows(RpcException.class, () -> clientFilter.invoke(invoker, invocation));

        assertNull(MDC.get(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void attachesTraceIdToAsyncResultAndRestoresMdc() {
        GXTraceIdContextUtils.putTraceId("outer-async");
        RpcInvocation invocation = new RpcInvocation();
        invocation.setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, "async-trace");
        AppResponse appResponse = new AppResponse(invocation);
        AsyncRpcResult asyncResult = AsyncRpcResult.newDefaultAsyncResult(appResponse, invocation);

        Result result = clientFilter.invoke(invokerReturning(invocation, asyncResult), invocation);

        assertEquals("async-trace", result.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals("outer-async", MDC.get(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void oneTraceIdAcrossConsumerAndProvider() {
        RpcInvocation invocation = new RpcInvocation();
        GXPenetrateAttachmentSelector selector = new GXPenetrateAttachmentSelector();

        Map<String, Object> selected = selector.select(invocation, RpcContext.getClientAttachment(), RpcContext.getServerAttachment());
        invocation.addObjectAttachments(selected);

        clientFilter.invoke(invokerReturning(invocation, new AppResponse(invocation)), invocation);
        String consumerTraceId = invocation.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        assertFalse(CharSequenceUtil.isBlank(consumerTraceId));

        MDC.clear();
        AppResponse providerResponse = new AppResponse(invocation);
        serverFilter.invoke(invokerReturning(invocation, providerResponse), invocation);
        assertEquals(consumerTraceId, providerResponse.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));

        Map<String, Object> reverse = selector.selectReverse(invocation, RpcContext.getClientAttachment(), RpcContext.getServerAttachment());
        assertEquals(consumerTraceId, reverse.get(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void keepsResultUntouchedWhenInvocationReturnsNull() {
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mock(Invoker.class);
        when(invoker.invoke(invocation)).thenReturn(null);

        assertNull(clientFilter.invoke(invoker, invocation));
        assertNull(serverFilter.invoke(invoker, invocation));
    }

    @Test
    void regeneratesWhenAllAttachmentsAreBlank() {
        RpcInvocation invocation = new RpcInvocation();
        invocation.setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, "   ");
        RpcContext.getClientAttachment().setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, "   ");
        RpcContext.getServerAttachment().setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, "   ");

        Result result = clientFilter.invoke(invokerReturning(invocation, new AppResponse(invocation)), invocation);

        String traceId = (String) result.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        assertFalse(CharSequenceUtil.isBlank(traceId));
        assertFalse("   ".equals(traceId));
    }

    private Invoker<?> invokerReturning(Invocation invocation, Result result) {
        Invoker<?> invoker = mock(Invoker.class);
        when(invoker.invoke(invocation)).thenReturn(result);
        return invoker;
    }
}
