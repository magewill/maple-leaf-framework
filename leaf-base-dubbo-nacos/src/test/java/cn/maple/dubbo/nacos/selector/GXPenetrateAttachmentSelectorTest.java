package cn.maple.dubbo.nacos.selector;

import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.RpcContextAttachment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    void selectFallsBackToClientBeforeServerAttachment() {
        Invocation invocation = invocationWithTraceId(null);
        RpcContextAttachment clientAttachment = attachmentWithTraceId("client-trace");
        RpcContextAttachment serverAttachment = attachmentWithTraceId("server-trace");

        Map<String, Object> selected = selector.select(invocation, clientAttachment, serverAttachment);

        assertEquals("client-trace", selected.get(GXTraceIdContextUtils.TRACE_ID_KEY));
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

    @Test
    void selectHandlesNullInvocation() {
        RpcContextAttachment clientAttachment = attachmentWithTraceId("client-trace");

        Map<String, Object> selected = selector.select(null, clientAttachment, null);

        assertEquals("client-trace", selected.get(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void selectReversePrefersServerAttachment() {
        Invocation invocation = invocationWithTraceId(null);
        RpcContextAttachment clientAttachment = attachmentWithTraceId("client-trace");
        RpcContextAttachment serverAttachment = attachmentWithTraceId("server-trace");

        Map<String, Object> selected = selector.selectReverse(invocation, clientAttachment, serverAttachment);

        assertEquals("server-trace", selected.get(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals(1, selected.size());
    }

    @Test
    void nullAttachmentsFallbackToThreadLocalOrGenerate() {
        GXTraceIdContextUtils.putTraceId("thread-trace");
        Map<String, Object> selected = selector.select(null, null, null);
        assertEquals("thread-trace", selected.get(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals(1, selected.size());

        MDC.clear();
        Map<String, Object> generated = selector.select(null, null, null);
        assertFalse(String.valueOf(generated.get(GXTraceIdContextUtils.TRACE_ID_KEY)).isBlank());
    }

    @Test
    void appNameCacheIsolatesAcrossReset() {
        GXPenetrateAttachmentSelector.resetAppNameCache();
        try (MockedStatic<GXCommonUtils> mocked = Mockito.mockStatic(GXCommonUtils.class)) {
            mocked.when(() -> GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "UnknownApp"))
                    .thenReturn("first-app");
            assertEquals("first-app", GXPenetrateAttachmentSelector.getCachedAppName());

            GXPenetrateAttachmentSelector.resetAppNameCache();
            mocked.when(() -> GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "UnknownApp"))
                    .thenReturn("second-app");
            assertEquals("second-app", GXPenetrateAttachmentSelector.getCachedAppName());
        } finally {
            GXPenetrateAttachmentSelector.resetAppNameCache();
        }
    }

    @Test
    void appNameCacheIsConsistentUnderConcurrency() throws Exception {
        GXPenetrateAttachmentSelector.resetAppNameCache();
        try (MockedStatic<GXCommonUtils> mocked = Mockito.mockStatic(GXCommonUtils.class)) {
            mocked.when(() -> GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "UnknownApp"))
                    .thenReturn("concurrent-app");
            assertEquals("concurrent-app", GXPenetrateAttachmentSelector.getCachedAppName());
        }
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                futures.add(executor.submit(GXPenetrateAttachmentSelector::getCachedAppName));
            }
            for (Future<String> future : futures) {
                assertEquals("concurrent-app", future.get(5, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
            GXPenetrateAttachmentSelector.resetAppNameCache();
        }
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
