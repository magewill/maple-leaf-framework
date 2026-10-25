package cn.maple.dubbo.zk.selector;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GXPenetrateAttachmentSelectorTest {
    private final GXPenetrateAttachmentSelector selector = new GXPenetrateAttachmentSelector();

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
    void selectPrefersInvocationAttachment() {
        RpcInvocation invocation = new RpcInvocation();
        invocation.setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, "invocation-trace");

        Map<String, Object> selected = selector.select(invocation, RpcContext.getClientAttachment(), RpcContext.getServerAttachment());

        assertEquals("invocation-trace", selected.get(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void selectReversePrefersInvocationAttachment() {
        RpcInvocation invocation = new RpcInvocation();
        invocation.setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, "reverse-trace");

        Map<String, Object> selected = selector.selectReverse(invocation, RpcContext.getClientAttachment(), RpcContext.getServerAttachment());

        assertEquals("reverse-trace", selected.get(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void selectedMapOnlyContainsTraceId() {
        RpcInvocation invocation = new RpcInvocation();
        invocation.setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, "single-key");

        Map<String, Object> selected = selector.select(invocation, null, null);

        assertEquals(1, selected.size());
        assertTrue(selected.containsKey(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void toleratesNullInvocationAndAttachments() {
        Map<String, Object> request = selector.select(null, null, null);
        Map<String, Object> response = selector.selectReverse(null, null, null);

        assertNotNull(request.get(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertNotNull(response.get(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertFalse(CharSequenceUtil.isBlank((String) request.get(GXTraceIdContextUtils.TRACE_ID_KEY)));
    }

    @Test
    void readsStringAttachmentWhenObjectAttachmentIsNull() {
        Invocation invocation = mock(Invocation.class);
        when(invocation.getObjectAttachment(GXTraceIdContextUtils.TRACE_ID_KEY)).thenReturn(null);
        when(invocation.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY)).thenReturn("string-trace");

        Map<String, Object> selected = selector.select(invocation, null, null);

        assertEquals("string-trace", selected.get(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void cachedAppNameNeverReturnsNullAndResetKeepsItReadable() {
        String before = GXPenetrateAttachmentSelector.getCachedAppName();
        GXPenetrateAttachmentSelector.resetAppNameCache();
        String after = GXPenetrateAttachmentSelector.getCachedAppName();

        assertNotNull(before);
        assertNotNull(after);
        assertEquals(before, after);
    }
}
