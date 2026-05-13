package cn.maple.feign.interceptor;

import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.feign.annotation.GXFeignHeader;
import cn.maple.feign.service.GXFeignService;
import feign.MethodMetadata;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GXFeignRequestInterceptorTest {
    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        GXTraceIdContextUtils.clearTraceId();
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
        assertThat(headerValues(requestTemplate, "User-Agent")).hasSize(1);
        assertThat(headerValues(requestTemplate, "X-Request-Start-Time")).hasSize(1);
        assertThat(headerValues(requestTemplate, "X-Frame-Options")).containsExactly("DENY");
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

    private List<String> headerValues(RequestTemplate requestTemplate, String headerName) {
        Collection<String> values = requestTemplate.headers().get(headerName);
        return values == null ? List.of() : List.copyOf(values);
    }

    private RequestTemplate requestTemplateFor(Method method) throws Exception {
        Constructor<MethodMetadata> constructor = MethodMetadata.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        MethodMetadata methodMetadata = constructor.newInstance().method(method);
        return new RequestTemplate().methodMetadata(methodMetadata);
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
