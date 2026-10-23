package cn.maple.webclient.service;

import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.client.ClientRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Snapshot capture and header application semantics.
 */
class GXWebClientContextSnapshotTest {
    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        GXTraceIdContextUtils.removeTraceId();
    }

    @Test
    void capturesTraceIdAndPlatformFromCallerThread() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GXTraceIdContextUtils.TRACE_ID_KEY, "request-trace");
        request.addHeader(GXTokenConstant.PLATFORM, "app");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        GXWebClientContextSnapshot snapshot = GXWebClientContextSnapshot.capture();

        assertThat(snapshot.getTraceId()).isEqualTo("request-trace");
        assertThat(snapshot.getPlatform()).isEqualTo("app");
        assertThat(snapshot.getAuthToken()).isNull();
        assertThat(snapshot.isEmpty()).isFalse();
    }

    @Test
    void returnsEmptySnapshotWithoutRequestContext() {
        GXWebClientContextSnapshot snapshot = GXWebClientContextSnapshot.capture();

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.isEmpty()).isTrue();
        assertThat(snapshot.getTraceId()).isNull();
        assertThat(snapshot.getPlatform()).isNull();
    }

    @Test
    void appliesOnlyNonNullValues() {
        GXWebClientContextSnapshot snapshot = new GXWebClientContextSnapshot("trace-1", null, "token-1", 1L);
        ClientRequest.Builder builder = ClientRequest.create(HttpMethod.GET, URI.create("http://127.0.0.1/ping"));

        snapshot.apply(builder);
        ClientRequest request = builder.build();

        assertThat(request.headers().getFirst(GXTraceIdContextUtils.TRACE_ID_KEY)).isEqualTo("trace-1");
        assertThat(request.headers().getFirst(GXCommonConstant.X_AUTH_TOKEN)).isEqualTo("token-1");
        assertThat(request.headers().getFirst(GXTokenConstant.PLATFORM)).isNull();
    }

    @Test
    void captureDoesNotGenerateToken() {
        GXWebClientContextSnapshot snapshot = GXWebClientContextSnapshot.capture("explicit-token");

        assertThat(snapshot.getAuthToken()).isEqualTo("explicit-token");
        assertThat(GXWebClientContextSnapshot.capture().getAuthToken()).isNull();
    }
}
