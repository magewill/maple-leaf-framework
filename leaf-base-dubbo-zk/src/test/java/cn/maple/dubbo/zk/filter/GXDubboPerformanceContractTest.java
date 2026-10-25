package cn.maple.dubbo.zk.filter;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.dubbo.zk.selector.GXPenetrateAttachmentSelector;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.maple.dubbo.zk.support.GXDubboTestSupport.appResponse;
import static cn.maple.dubbo.zk.support.GXDubboTestSupport.invocation;
import static cn.maple.dubbo.zk.support.GXDubboTestSupport.invoker;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Recomputable performance contract: how many Spring {@code Environment} reads and TraceId
 * resolutions a call is allowed to perform, and that the hot path logs at DEBUG only.
 */
class GXDubboPerformanceContractTest {
    @AfterEach
    void tearDown() {
        MDC.clear();
        RpcContext.removeContext();
        GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
        GXPenetrateAttachmentSelector.resetAppNameCache();
    }

    @Test
    void readsEnvironmentOnlyOnceForCachedAppName() {
        CountingEnvironment environment = new CountingEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("zk-perf", Map.of("spring.application.name", "zk-perf-app")));

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setEnvironment(environment);
        context.refresh();
        try {
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(context);
            GXPenetrateAttachmentSelector.resetAppNameCache();

            assertEquals("zk-perf-app", GXPenetrateAttachmentSelector.getCachedAppName());
            assertEquals("zk-perf-app", GXPenetrateAttachmentSelector.getCachedAppName());
            assertEquals("zk-perf-app", GXPenetrateAttachmentSelector.getCachedAppName());

            assertEquals(1, environment.propertyReads.get(), "appName must be read from the Environment exactly once");
        } finally {
            context.close();
        }
    }

    @Test
    void traceIdFiltersRequestEnvironmentOnlyThroughTheSharedCache() {
        List<String> filterSources = List.of(
                "src/main/java/cn/maple/dubbo/zk/filter/GXDubboClientTraceIdFilter.java",
                "src/main/java/cn/maple/dubbo/zk/filter/GXDubboServerTraceIdFilter.java"
        );

        filterSources.forEach(source -> {
            String content = readSource(source);
            assertFalse(content.contains("getEnvironmentValue("),
                    source + " must not query the Environment per request; use GXPenetrateAttachmentSelector.getCachedAppName()");
        });
    }

    @Test
    void corePathHasNoInfoLogs() {
        List<String> hotPathSources = List.of(
                "src/main/java/cn/maple/dubbo/zk/filter/GXDubboClientTraceIdFilter.java",
                "src/main/java/cn/maple/dubbo/zk/filter/GXDubboServerTraceIdFilter.java",
                "src/main/java/cn/maple/dubbo/zk/selector/GXPenetrateAttachmentSelector.java"
        );

        hotPathSources.forEach(source -> {
            String content = readSource(source);
            assertFalse(content.contains("log.info("), source + " must not log at INFO on the hot path");
            assertFalse(content.contains("LOG.info("), source + " must not log at INFO on the hot path");
        });
    }

    @Test
    void resolvesTraceIdExactlyOncePerExceptionResponse() {
        AtomicInteger reads = new AtomicInteger();
        Invocation invocation = mock(Invocation.class);
        when(invocation.getObjectAttachment(GXTraceIdContextUtils.TRACE_ID_KEY)).thenAnswer(answer -> {
            reads.incrementAndGet();
            return "perf-trace";
        });
        GXBusinessException business = new GXBusinessException("business-error", 4001, Dict.create());

        AppResponse response = appResponse(business);
        new GXDubboExceptionFilter().onResponse(response, invoker(TestService.class), invocation);

        assertEquals(1, reads.get(), "TraceId must be resolved once per response");
        assertEquals("perf-trace", business.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void attachmentReadsStayWithinBudget() {
        AtomicInteger reads = new AtomicInteger();
        Invocation invocation = mock(Invocation.class);
        when(invocation.getObjectAttachment(GXTraceIdContextUtils.TRACE_ID_KEY)).thenAnswer(answer -> {
            reads.incrementAndGet();
            return "budget-trace";
        });
        when(invocation.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY)).thenAnswer(answer -> {
            reads.incrementAndGet();
            return "budget-trace";
        });
        GXBusinessException business = new GXBusinessException("business-error", 4001, Dict.create());

        AppResponse response = appResponse(business);
        new GXDubboExceptionFilter().onResponse(response, invoker(TestService.class), invocation);

        assertTrue(reads.get() <= 4, "attachment reads per response must stay within budget, was " + reads.get());
    }

    private String readSource(String relativePath) {
        try {
            return Files.readString(Path.of(relativePath));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + relativePath, e);
        }
    }

    interface TestService {
        void call();
    }

    static final class CountingEnvironment extends StandardEnvironment {
        private static final long serialVersionUID = 1L;

        private final AtomicInteger propertyReads = new AtomicInteger();

        @Override
        public <T> T getProperty(String key, Class<T> targetType) {
            propertyReads.incrementAndGet();
            return super.getProperty(key, targetType);
        }
    }
}
