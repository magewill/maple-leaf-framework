package cn.maple.dubbo.zk;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.dubbo.zk.filter.GXDubboClientTraceIdFilter;
import cn.maple.dubbo.zk.filter.GXDubboExceptionFilter;
import cn.maple.dubbo.zk.selector.GXPenetrateAttachmentSelector;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static cn.maple.dubbo.zk.support.GXDubboTestSupport.appResponse;
import static cn.maple.dubbo.zk.support.GXDubboTestSupport.invocation;
import static cn.maple.dubbo.zk.support.GXDubboTestSupport.invoker;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Locks the concurrency semantics of the module's shared state:
 * the cached {@code spring.application.name}, the mutable exception data map, and the
 * {@code RpcContext} attachments written by the consumer filter.
 */
class GXDubboSharedStateContractTest {
    @BeforeEach
    void setUp() {
        MDC.clear();
        RpcContext.removeContext();
        GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
        GXPenetrateAttachmentSelector.resetAppNameCache();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        RpcContext.removeContext();
        GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
        GXPenetrateAttachmentSelector.resetAppNameCache();
    }

    @Test
    void concurrentFirstReadReturnsSameCachedAppName() throws Exception {
        try (AnnotationConfigApplicationContext context = contextWithAppName("zk-concurrent-app")) {
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(context);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                AtomicReference<String> first = new AtomicReference<>();
                AtomicReference<String> second = new AtomicReference<>();
                executor.submit(() -> {
                    start.await();
                    first.set(GXPenetrateAttachmentSelector.getCachedAppName());
                    return null;
                });
                executor.submit(() -> {
                    start.await();
                    second.set(GXPenetrateAttachmentSelector.getCachedAppName());
                    return null;
                });
                start.countDown();
                executor.shutdown();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "tasks must finish");

                assertEquals("zk-concurrent-app", first.get());
                assertEquals(first.get(), second.get());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void resetAppNameCacheAllowsSecondContextToBeRead() {
        try (AnnotationConfigApplicationContext first = contextWithAppName("zk-app-one")) {
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(first);
            assertEquals("zk-app-one", GXPenetrateAttachmentSelector.getCachedAppName());
        }

        GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
        GXPenetrateAttachmentSelector.resetAppNameCache();

        try (AnnotationConfigApplicationContext second = contextWithAppName("zk-app-two")) {
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(second);
            assertEquals("zk-app-two", GXPenetrateAttachmentSelector.getCachedAppName());
        }
    }

    @Test
    void sharedExceptionInstanceStaysUsableUnderConcurrency() throws Exception {
        GXBusinessException shared = new GXBusinessException("shared-business-error", 4001, Dict.create().set("orderId", "A-1"));
        GXDubboExceptionFilter filter = new GXDubboExceptionFilter();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 2; i++) {
                String traceId = "thread-" + i;
                executor.submit(() -> {
                    start.await();
                    GXTraceIdContextUtils.putTraceId(traceId);
                    try {
                        AppResponse response = appResponse(shared);
                        filter.onResponse(response, invoker(TestService.class), invocation("call"));
                        return response;
                    } finally {
                        GXTraceIdContextUtils.removeTraceId();
                    }
                });
            }
            start.countDown();
            executor.shutdown();

            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "filter must not deadlock");
        } finally {
            executor.shutdownNow();
        }

        assertEquals(4001, shared.getCode());
        assertEquals("A-1", shared.getData().getStr("orderId"));
        assertNotNull(shared.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY),
                "in-place traceId injection into the exception data is the accepted contract");
    }

    @Test
    void previousTraceIdDoesNotLeakIntoTheNextCall() {
        GXDubboClientTraceIdFilter filter = new GXDubboClientTraceIdFilter();

        RpcInvocation firstInvocation = new RpcInvocation();
        Result firstResult = invokeWithTrace(filter, firstInvocation);
        String firstTraceId = (String) firstResult.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        assertNotNull(firstTraceId);

        RpcContext.removeContext();
        MDC.clear();

        RpcInvocation secondInvocation = new RpcInvocation();
        Result secondResult = invokeWithTrace(filter, secondInvocation);

        assertNotEquals(firstTraceId, secondResult.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertNull(MDC.get(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    private Result invokeWithTrace(GXDubboClientTraceIdFilter filter, RpcInvocation invocation) {
        AppResponse response = new AppResponse(invocation);
        Invoker<?> invoker = mock(Invoker.class);
        when(invoker.invoke(invocation)).thenReturn(response);
        return filter.invoke(invoker, invocation);
    }

    private AnnotationConfigApplicationContext contextWithAppName(String appName) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("zk-test", Map.of("spring.application.name", appName)));
        context.refresh();
        return context;
    }

    interface TestService {
        void call();
    }
}
