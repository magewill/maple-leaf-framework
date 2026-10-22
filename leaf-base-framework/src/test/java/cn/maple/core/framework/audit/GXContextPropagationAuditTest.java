package cn.maple.core.framework.audit;

import cn.maple.core.framework.config.GXAsyncConfig;
import cn.maple.core.framework.filter.GXBaseRequestLoggingFilter;
import cn.maple.core.framework.filter.GXXssHttpServletRequestWrapper;
import cn.maple.core.framework.service.impl.GXBaseCacheLockServiceImpl;
import cn.maple.core.framework.util.GXMdcThreadUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.core.framework.wrapper.mdc.GXMdcWrapperForkJoinPool;
import cn.maple.core.framework.wrapper.mdc.GXMdcWrapperThreadPoolTaskExecutor;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * CURRENT BEHAVIOR - flip when fixed.
 *
 * <p>Characterizes how trace id / MDC context behaves across the async executor, the
 * ForkJoin wrapper, the Spring task executor wrapper and the request logging filter.
 * These assertions describe the current (defective) behavior on purpose.</p>
 */
class GXContextPropagationAuditTest {
    private static final String CUSTOM_MDC_KEY = "audit-user-id";

    @AfterEach
    void clearMdc() {
        MDC.remove(CUSTOM_MDC_KEY);
        GXTraceIdContextUtils.clearTraceId();
    }

    @Test
    void asyncExecutorDoesNotPropagateTraceId() throws Exception {
        GXTraceIdContextUtils.putTraceId("audit-async-trace");
        GXAsyncConfig config = new GXAsyncConfig(mock(ObjectProvider.class));
        Executor executor = config.getAsyncExecutor();
        CompletableFuture<String> captured = new CompletableFuture<>();

        executor.execute(() -> captured.complete(GXTraceIdContextUtils.getNullableTraceId()));

        assertNull(captured.get(5, TimeUnit.SECONDS));
    }

    @Test
    void forkJoinSubmitOfForkJoinTaskIsNotWrapped() throws Exception {
        GXTraceIdContextUtils.putTraceId("audit-forkjoin-trace");
        ForkJoinPool pool = new GXMdcWrapperForkJoinPool(1);
        AtomicReference<String> captured = new AtomicReference<>("unset");
        try {
            ForkJoinTask<?> task = ForkJoinTask.adapt(() -> captured.set(GXTraceIdContextUtils.getNullableTraceId()));
            pool.submit(task).get(5, TimeUnit.SECONDS);

            assertNull(captured.get());
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void asyncExecutorIsPlainExecutorWithoutTaskDecorator() {
        GXAsyncConfig config = new GXAsyncConfig(mock(ObjectProvider.class));

        Executor executor = config.getAsyncExecutor();

        assertInstanceOf(SimpleAsyncTaskExecutor.class, executor);
        assertFalse(executor instanceof GXMdcWrapperThreadPoolTaskExecutor);
    }

    @Test
    void customMdcKeyLeaksAfterRequestFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/audit/mdc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) {
                MDC.put(CUSTOM_MDC_KEY, "u-42");
            }
        });

        new GXBaseRequestLoggingFilter().doFilter(request, response, chain);

        assertEquals("u-42", MDC.get(CUSTOM_MDC_KEY));
        assertNull(MDC.get(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void nonMdcThreadLocalsAreNotCleared() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/audit/thread-local");
        request.setContentType("application/json");
        request.setContent("{\"note\":\"a\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        new GXXssHttpServletRequestWrapper(request);

        ThreadLocal<?> htmlFilter = staticThreadLocal(GXXssHttpServletRequestWrapper.class, "HTML_FILTER");
        assertNotNull(htmlFilter.get());

        GXBaseCacheLockServiceImpl lockService = new GXBaseCacheLockServiceImpl();
        assertTrue(lockService.tryLock("audit-lock"));
        assertTrue(lockService.releaseLock("audit-lock"));
        ThreadLocal<?> threadLocks = staticThreadLocal(GXBaseCacheLockServiceImpl.class, "THREAD_LOCKS");
        Map<?, ?> retained = (Map<?, ?>) threadLocks.get();
        assertNotNull(retained);
        assertTrue(retained.isEmpty());
    }

    private ThreadLocal<?> staticThreadLocal(Class<?> owner, String fieldName) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (ThreadLocal<?>) field.get(null);
    }

    @Test
    void explicitWrapRestoresTraceIdInsideWorkerThread() throws Exception {
        GXTraceIdContextUtils.putTraceId("audit-wrap-trace");
        Map<String, String> context = GXMdcThreadUtils.getMdcContext();
        CompletableFuture<String> captured = new CompletableFuture<>();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Runnable wrapped = GXMdcThreadUtils.wrap(
                    (Runnable) () -> captured.complete(GXTraceIdContextUtils.getNullableTraceId()), context);
            pool.execute(wrapped);

            assertEquals("audit-wrap-trace", captured.get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }
}
