package cn.maple.feign.interceptor;

import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.feign.annotation.GXFeignHeader;
import cn.maple.feign.service.GXFeignService;
import feign.MethodMetadata;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GXFeignRequestInterceptorTest {
    private static final List<String> RESPONSE_SEMANTIC_HEADERS = List.of(
            "X-Content-Type-Options",
            "X-Frame-Options",
            "X-XSS-Protection",
            "Cache-Control",
            "Pragma",
            "Expires"
    );

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        GXTraceIdContextUtils.clearTraceId();
        GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
    }

    @Test
    void serviceHeadersAndCommonHeadersAreIdempotent() {
        GXFeignRequestInterceptor interceptor = new GXFeignRequestInterceptor(new SingleObjectProvider<>(new StubFeignService()));
        RequestTemplate requestTemplate = new RequestTemplate();
        requestTemplate.header(GXCommonConstant.X_AUTH_TOKEN, "old-token");
        requestTemplate.header(GXTokenConstant.PLATFORM, "old-platform");
        requestTemplate.header(GXTraceIdContextUtils.TRACE_ID_KEY, "old-trace");
        requestTemplate.header("User-Agent", "old-agent");

        interceptor.apply(requestTemplate);
        interceptor.apply(requestTemplate);

        assertThat(headerValues(requestTemplate, GXCommonConstant.X_AUTH_TOKEN)).containsExactly("generated-token");
        assertThat(headerValues(requestTemplate, GXTokenConstant.PLATFORM)).containsExactly("web");
        assertThat(headerValues(requestTemplate, GXTraceIdContextUtils.TRACE_ID_KEY)).containsExactly("trace-1");
        assertThat(headerValues(requestTemplate, "User-Agent")).containsExactly("old-agent");
        assertThat(headerValues(requestTemplate, "X-Request-Start-Time")).hasSize(1);
    }

    @Test
    void responseSemanticHeadersAreNotWrittenToOutboundRequest() {
        GXFeignRequestInterceptor interceptor = new GXFeignRequestInterceptor(new SingleObjectProvider<>(new StubFeignService()));
        RequestTemplate requestTemplate = new RequestTemplate();

        interceptor.apply(requestTemplate);

        RESPONSE_SEMANTIC_HEADERS.forEach(headerName ->
                assertThat(headerValues(requestTemplate, headerName))
                        .as("outbound request must not carry response header %s", headerName)
                        .isEmpty());
    }

    @Test
    void defaultUserAgentIsAppliedWhenCallerDidNotProvideOne() {
        GXFeignRequestInterceptor interceptor = new GXFeignRequestInterceptor(new SingleObjectProvider<>(new StubFeignService()));
        RequestTemplate requestTemplate = new RequestTemplate();

        interceptor.apply(requestTemplate);

        assertThat(headerValues(requestTemplate, "User-Agent")).hasSize(1);
        assertThat(headerValues(requestTemplate, "User-Agent").getFirst()).startsWith("Maple-Leaf-Feign/1.0 (");
    }

    @Test
    void requestSourceHeaderIsPropagatedWhenApplicationNameIsConfigured() {
        configureEnvironment("spring.application.name", "leaf-demo");
        GXFeignRequestInterceptor interceptor = new GXFeignRequestInterceptor(new SingleObjectProvider<>(new StubFeignService()));
        RequestTemplate requestTemplate = new RequestTemplate();

        interceptor.apply(requestTemplate);

        assertThat(headerValues(requestTemplate, "X-Request-Source")).containsExactly("leaf-demo");
    }

    @Test
    void missingFeignServiceStillPropagatesTraceIdSafely() {
        GXFeignRequestInterceptor interceptor = new GXFeignRequestInterceptor(new SingleObjectProvider<>(null));
        RequestTemplate requestTemplate = new RequestTemplate();

        interceptor.apply(requestTemplate);

        assertThat(headerValues(requestTemplate, GXCommonConstant.X_AUTH_TOKEN)).isEmpty();
        assertThat(headerValues(requestTemplate, GXTraceIdContextUtils.TRACE_ID_KEY)).hasSize(1);
    }

    @Test
    void serviceCanReadRealRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(GXTokenConstant.PLATFORM, "app");
        request.setAttribute(GXTraceIdContextUtils.TRACE_ID_KEY, "request-trace");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        GXFeignRequestInterceptor interceptor = new GXFeignRequestInterceptor(new SingleObjectProvider<>(new GXFeignService() {
            @Override
            public String generateHttpAuthToken() {
                return "context-token";
            }
        }));
        RequestTemplate requestTemplate = new RequestTemplate();

        interceptor.apply(requestTemplate);

        assertThat(headerValues(requestTemplate, GXCommonConstant.X_AUTH_TOKEN)).containsExactly("context-token");
        assertThat(headerValues(requestTemplate, GXTokenConstant.PLATFORM)).containsExactly("app");
        assertThat(headerValues(requestTemplate, GXTraceIdContextUtils.TRACE_ID_KEY)).containsExactly("request-trace");
    }

    @Test
    void annotatedHeadersPropagateOnlyAllowedHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "tenant-1");
        request.addHeader(GXTraceIdContextUtils.TRACE_ID_KEY, "trace-from-request");
        request.addHeader("Authorization", "Bearer user-token");
        request.addHeader("Cookie", "SESSION=secret");
        request.addHeader("Connection", "keep-alive");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        GXFeignRequestInterceptor interceptor = new GXFeignRequestInterceptor(new SingleObjectProvider<>(new StubFeignService()));
        RequestTemplate requestTemplate = requestTemplateFor(AnnotatedHeadersClient.class.getMethod("call"));

        interceptor.apply(requestTemplate);

        assertThat(headerValues(requestTemplate, "X-Tenant-Id")).containsExactly("tenant-1");
        assertThat(headerValues(requestTemplate, GXTraceIdContextUtils.TRACE_ID_KEY)).containsExactly("trace-1");
        assertThat(headerValues(requestTemplate, "Authorization")).isEmpty();
        assertThat(headerValues(requestTemplate, "Cookie")).isEmpty();
        assertThat(headerValues(requestTemplate, "Connection")).isEmpty();
    }

    @Test
    void annotatedHeadersAreSkippedWithoutWebContext() throws Exception {
        GXFeignRequestInterceptor interceptor = new GXFeignRequestInterceptor(new SingleObjectProvider<>(new StubFeignService()));
        RequestTemplate requestTemplate = requestTemplateFor(AnnotatedHeadersClient.class.getMethod("call"));

        interceptor.apply(requestTemplate);

        assertThat(headerValues(requestTemplate, "X-Tenant-Id")).isEmpty();
    }

    @Test
    void webContextWithServiceBeanResolvesAllHeaderCategories() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "tenant-1");
        request.addHeader(GXTokenConstant.PLATFORM, "app");
        request.setAttribute(GXTraceIdContextUtils.TRACE_ID_KEY, "request-trace");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate requestTemplate = requestTemplateFor(AnnotatedHeadersClient.class);
        new GXFeignRequestInterceptor(new SingleObjectProvider<>(new StubFeignService())).apply(requestTemplate);

        assertThat(headerValues(requestTemplate, GXCommonConstant.X_AUTH_TOKEN)).containsExactly("generated-token");
        assertThat(headerValues(requestTemplate, GXTokenConstant.PLATFORM)).containsExactly("web");
        assertThat(headerValues(requestTemplate, "X-Tenant-Id")).containsExactly("tenant-1");
        assertThat(headerValues(requestTemplate, "X-Request-Start-Time")).hasSize(1);
    }

    @Test
    void webContextWithoutServiceBeanHasNoTokenButKeepsTraceId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "tenant-1");
        request.setAttribute(GXTraceIdContextUtils.TRACE_ID_KEY, "request-trace");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate requestTemplate = requestTemplateFor(AnnotatedHeadersClient.class);
        new GXFeignRequestInterceptor(new SingleObjectProvider<>(null)).apply(requestTemplate);

        assertThat(headerValues(requestTemplate, GXCommonConstant.X_AUTH_TOKEN)).isEmpty();
        assertThat(headerValues(requestTemplate, GXTokenConstant.PLATFORM)).isEmpty();
        assertThat(headerValues(requestTemplate, "X-Tenant-Id")).containsExactly("tenant-1");
        assertThat(headerValues(requestTemplate, GXTraceIdContextUtils.TRACE_ID_KEY)).hasSize(1);
    }

    @Test
    void noWebContextWithServiceBeanPropagatesTokenAndPlatform() {
        RequestTemplate requestTemplate = new RequestTemplate();
        new GXFeignRequestInterceptor(new SingleObjectProvider<>(new StubFeignService())).apply(requestTemplate);

        assertThat(headerValues(requestTemplate, GXCommonConstant.X_AUTH_TOKEN)).containsExactly("generated-token");
        assertThat(headerValues(requestTemplate, GXTokenConstant.PLATFORM)).containsExactly("web");
        assertThat(headerValues(requestTemplate, GXTraceIdContextUtils.TRACE_ID_KEY)).containsExactly("trace-1");
    }

    @Test
    void noWebContextWithoutServiceBeanGeneratesTraceIdWithoutLeakingContext() {
        RequestTemplate requestTemplate = new RequestTemplate();
        new GXFeignRequestInterceptor(new SingleObjectProvider<>(null)).apply(requestTemplate);

        assertThat(headerValues(requestTemplate, GXCommonConstant.X_AUTH_TOKEN)).isEmpty();
        assertThat(headerValues(requestTemplate, GXTokenConstant.PLATFORM)).isEmpty();
        assertThat(headerValues(requestTemplate, GXTraceIdContextUtils.TRACE_ID_KEY)).hasSize(1);
        assertThat(headerValues(requestTemplate, GXTraceIdContextUtils.TRACE_ID_KEY).getFirst()).isNotBlank();
        assertThat(GXTraceIdContextUtils.getTraceId()).isEmpty();
    }

    @Test
    void existingTraceIdIsPreservedWithoutExtraGeneratedValue() {
        GXTraceIdContextUtils.putTraceId("existing-trace");
        RequestTemplate requestTemplate = new RequestTemplate();

        new GXFeignRequestInterceptor(new SingleObjectProvider<>(null)).apply(requestTemplate);

        assertThat(headerValues(requestTemplate, GXTraceIdContextUtils.TRACE_ID_KEY)).containsExactly("existing-trace");
        assertThat(GXTraceIdContextUtils.getTraceId()).isEqualTo("existing-trace");
    }

    @Test
    void headerMatrixIsIdempotentAcrossAllContexts() {
        List<ObjectProvider<GXFeignService>> providers = List.of(
                new SingleObjectProvider<>(new StubFeignService()),
                new SingleObjectProvider<>(null));

        for (ObjectProvider<GXFeignService> provider : providers) {
            assertSingleValuedHeadersWithWebContext(provider);
            assertSingleValuedHeadersWithoutWebContext(provider);
        }
    }

    private void assertSingleValuedHeadersWithWebContext(ObjectProvider<GXFeignService> provider) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "tenant-1");
        request.addHeader(GXTokenConstant.PLATFORM, "app");
        request.setAttribute(GXTraceIdContextUtils.TRACE_ID_KEY, "request-trace");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate requestTemplate = requestTemplateFor(AnnotatedHeadersClient.class);
        GXFeignRequestInterceptor interceptor = new GXFeignRequestInterceptor(provider);
        interceptor.apply(requestTemplate);
        interceptor.apply(requestTemplate);

        requestTemplate.headers().forEach((headerName, values) ->
                assertThat(values).as("header %s must stay single valued", headerName).hasSize(1));
        assertThat(headerValues(requestTemplate, "X-Tenant-Id")).hasSize(1);
        assertThat(headerValues(requestTemplate, "X-Request-Start-Time")).hasSize(1);
        assertThat(headerValues(requestTemplate, "User-Agent")).hasSize(1);
    }

    private void assertSingleValuedHeadersWithoutWebContext(ObjectProvider<GXFeignService> provider) {
        RequestContextHolder.resetRequestAttributes();
        RequestTemplate requestTemplate = requestTemplateFor(AnnotatedHeadersClient.class);
        GXFeignRequestInterceptor interceptor = new GXFeignRequestInterceptor(provider);
        interceptor.apply(requestTemplate);
        interceptor.apply(requestTemplate);

        requestTemplate.headers().forEach((headerName, values) ->
                assertThat(values).as("header %s must stay single valued", headerName).hasSize(1));
        assertThat(headerValues(requestTemplate, "X-Tenant-Id")).isEmpty();
        assertThat(headerValues(requestTemplate, "X-Request-Start-Time")).hasSize(1);
    }

    @Test
    void annotatedHeaderLookupScalesWithValidDeclaredHeaderCount() {
        CountingRequest request = new CountingRequest();
        request.addHeader("X-A", "a");
        request.addHeader("X-B", "b");
        request.addHeader("X-C", "c");
        request.addHeader("X-D", "d");
        request.addHeader("Authorization", "Bearer token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        GXFeignRequestInterceptor interceptor = new GXFeignRequestInterceptor(new SingleObjectProvider<>(new StubFeignService()));

        RequestTemplate singleHeaderTemplate = requestTemplateFor(SingleHeaderClient.class);
        interceptor.apply(singleHeaderTemplate);
        int singleHeaderLookups = request.count("X-A");

        request.resetCounts();
        RequestTemplate multiHeaderTemplate = requestTemplateFor(MultiHeaderClient.class);
        interceptor.apply(multiHeaderTemplate);
        int multiHeaderLookups = request.count("X-A") + request.count("X-B")
                + request.count("X-C") + request.count("X-D");

        assertThat(singleHeaderLookups).as("one declared header").isEqualTo(1);
        assertThat(multiHeaderLookups).as("four valid declared headers").isEqualTo(4);
        assertThat(request.count("Authorization")).as("blocked header must not be read").isZero();
    }

    @Test
    void authTokenEnabledFailsFastWhenTokenConfigurationIsMissing() {
        GXFeignRequestInterceptor interceptor = new GXFeignRequestInterceptor(new SingleObjectProvider<>(new GXFeignService() {
        }));
        RequestTemplate requestTemplate = new RequestTemplate();

        assertThatThrownBy(() -> interceptor.apply(requestTemplate))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("maple.framework.web.feign.token");
    }

    @Test
    void authTokenDisabledSkipsTokenGenerationAndKeepsOtherHeaders() {
        configureEnvironment("maple.framework.feign.auth-token.enabled", "false");
        GXFeignRequestInterceptor interceptor = new GXFeignRequestInterceptor(new SingleObjectProvider<>(new GXFeignService() {
            @Override
            public String getPlatform() {
                return "web";
            }

            @Override
            public String getTraceId() {
                return "trace-1";
            }
        }));
        RequestTemplate requestTemplate = new RequestTemplate();

        interceptor.apply(requestTemplate);

        assertThat(headerValues(requestTemplate, GXCommonConstant.X_AUTH_TOKEN)).isEmpty();
        assertThat(headerValues(requestTemplate, GXTokenConstant.PLATFORM)).containsExactly("web");
        assertThat(headerValues(requestTemplate, GXTraceIdContextUtils.TRACE_ID_KEY)).containsExactly("trace-1");
    }

    @Test
    void applyThroughputBaseline(TestReporter reporter) {
        GXFeignRequestInterceptor interceptor = new GXFeignRequestInterceptor(new SingleObjectProvider<>(new StubFeignService()));
        int warmupSamples = 50_000;
        int measuredSamples = 100_000;
        RequestTemplate requestTemplate = new RequestTemplate();

        for (int i = 0; i < warmupSamples; i++) {
            interceptor.apply(requestTemplate);
        }

        long[] timings = new long[measuredSamples];
        long measurementStart = System.nanoTime();
        for (int i = 0; i < measuredSamples; i++) {
            long singleStart = System.nanoTime();
            interceptor.apply(requestTemplate);
            timings[i] = System.nanoTime() - singleStart;
        }
        long elapsedNanos = System.nanoTime() - measurementStart;

        Arrays.sort(timings);
        long p50 = timings[measuredSamples / 2];
        long p95 = timings[(int) (measuredSamples * 0.95)];
        long p99 = timings[(int) (measuredSamples * 0.99)];
        double opsPerSecond = measuredSamples * 1_000_000_000.0 / elapsedNanos;

        String metricsLine = "samples=%d p50=%dns p95=%dns p99=%dns opsPerSecond=%.0f"
                .formatted(measuredSamples, p50, p95, p99, opsPerSecond);
        reporter.publishEntry("apply-throughput", metricsLine);
        writeMetrics(metricsLine);

        assertThat(p50).isPositive();
        assertThat(p99).isLessThan(1_000_000L);
        assertThat(headerValues(requestTemplate, "X-Request-Start-Time")).hasSize(1);
    }

    private void writeMetrics(String metricsLine) {
        try {
            Files.writeString(Path.of("target", "gx-feign-apply-baseline.txt"),
                    metricsLine + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to persist apply baseline metrics", e);
        }
    }

    private void configureEnvironment(String key, String value) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(new MockEnvironment().withProperty(key, value));
        context.refresh();
        GXApplicationContextSingleton.INSTANCE.setApplicationContext(context);
    }

    private List<String> headerValues(RequestTemplate requestTemplate, String headerName) {
        Collection<String> values = requestTemplate.headers().get(headerName);
        return values == null ? List.of() : List.copyOf(values);
    }

    private RequestTemplate requestTemplateFor(Class<?> clientType) {
        Method method = clientType.getMethods()[0];
        return requestTemplateFor(method);
    }

    private RequestTemplate requestTemplateFor(Method method) {
        try {
            Constructor<MethodMetadata> constructor = MethodMetadata.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            MethodMetadata methodMetadata = constructor.newInstance().method(method);
            return new RequestTemplate().methodMetadata(methodMetadata);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class StubFeignService implements GXFeignService {
        @Override
        public String generateHttpAuthToken() {
            return "generated-token";
        }

        @Override
        public String getPlatform() {
            return "web";
        }

        @Override
        public String getTraceId() {
            return "trace-1";
        }
    }

    private interface SingleHeaderClient {
        @GXFeignHeader(names = {"X-A"})
        void call();
    }

    private interface MultiHeaderClient {
        @GXFeignHeader(names = {"X-A", "X-B", "X-C", "Authorization", "X-D"})
        void call();
    }

    private static final class CountingRequest extends MockHttpServletRequest {
        private final Map<String, Integer> headerLookups = new ConcurrentHashMap<>();

        @Override
        public Enumeration<String> getHeaders(String name) {
            String key = name.toLowerCase(Locale.ROOT);
            headerLookups.put(key, headerLookups.getOrDefault(key, 0) + 1);
            return super.getHeaders(name);
        }

        int count(String headerName) {
            return headerLookups.getOrDefault(headerName.toLowerCase(Locale.ROOT), 0);
        }

        void resetCounts() {
            headerLookups.clear();
        }
    }

    private interface AnnotatedHeadersClient {
        @GXFeignHeader(names = {
                "X-Tenant-Id",
                "X-B3-TraceId",
                "Authorization",
                "Cookie",
                "Connection"
        })
        void call();
    }

    private record SingleObjectProvider<T>(T value) implements ObjectProvider<T> {
        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getObject() {
            return value;
        }
    }
}
