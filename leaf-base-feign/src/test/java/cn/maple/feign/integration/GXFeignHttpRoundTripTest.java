package cn.maple.feign.integration;

import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.feign.codec.GXFeignCustomErrorDecoder;
import cn.maple.feign.interceptor.GXFeignRequestInterceptor;
import cn.maple.feign.service.GXFeignService;
import cn.maple.feign.support.GXFeignTestClients;
import cn.maple.feign.support.GXFeignTestHttpServer;
import feign.Contract;
import feign.Feign;
import feign.FeignException;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GXFeignHttpRoundTripTest {
    private GXFeignTestHttpServer server;

    @BeforeEach
    void setUp() {
        server = GXFeignTestHttpServer.start();
    }

    @AfterEach
    void tearDown() {
        server.close();
        RequestContextHolder.resetRequestAttributes();
        GXTraceIdContextUtils.clearTraceId();
        GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
    }

    @Test
    void serviceHeadersReachTheServerOverRealHttp() {
        server.stub("/ok", 200, "ok");
        GXFeignTestClients.RoundTripClient client = client(new StubFeignService(), null);

        assertThat(client.ok()).isEqualTo("ok");

        List<String> authTokens = server.lastRequest("/ok").headerValues(GXCommonConstant.X_AUTH_TOKEN);
        assertThat(authTokens).containsExactly("generated-token");
        assertThat(server.lastRequest("/ok").headerValues(GXTokenConstant.PLATFORM)).containsExactly("web");
        assertThat(server.lastRequest("/ok").headerValues(GXTraceIdContextUtils.TRACE_ID_KEY))
                .containsExactly("trace-1");
        assertThat(server.lastRequest("/ok").headerValues("X-Request-Start-Time")).hasSize(1);
        assertThat(server.lastRequest("/ok").headerValues("User-Agent").getFirst())
                .startsWith("Maple-Leaf-Feign/1.0 (");
    }

    @Test
    void responseSemanticHeadersAreNotSentOverRealHttp() {
        server.stub("/ok", 200, "ok");

        client(new StubFeignService(), null).ok();

        assertThat(server.lastRequest("/ok").headerValues("X-Frame-Options")).isEmpty();
        assertThat(server.lastRequest("/ok").headerValues("X-XSS-Protection")).isEmpty();
        assertThat(server.lastRequest("/ok").headerValues("Cache-Control")).isEmpty();
        assertThat(server.lastRequest("/ok").headerValues("Pragma")).isEmpty();
        assertThat(server.lastRequest("/ok").headerValues("Expires")).isEmpty();
        assertThat(server.lastRequest("/ok").headerValues("X-Content-Type-Options")).isEmpty();
    }

    @Test
    void annotatedHeadersArePropagatedAndSensitiveOnesBlockedOverRealHttp() {
        server.stub("/annotated", 200, "ok");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "tenant-1");
        request.addHeader("Authorization", "Bearer user-token");
        request.addHeader("Cookie", "SESSION=secret");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        client(new StubFeignService(), null).annotated();

        assertThat(server.lastRequest("/annotated").headerValues("X-Tenant-Id")).containsExactly("tenant-1");
        assertThat(server.lastRequest("/annotated").headerValues("Authorization")).isEmpty();
        assertThat(server.lastRequest("/annotated").headerValues("Cookie")).isEmpty();
    }

    @Test
    void businessErrorKeepsRemoteCodeAndMessageOverRealHttp() {
        server.stub("/bad-request", 400, "{\"code\":1001,\"msg\":\"inventory short\",\"data\":{\"sku\":\"A1\"}}");

        GXFeignTestClients.RoundTripClient client = client(new StubFeignService(), new GXFeignCustomErrorDecoder());

        assertThatThrownBy(client::badRequest)
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("inventory short")
                .satisfies(thrown -> assertThat(((GXBusinessException) thrown).getCode()).isEqualTo(1001));
    }

    @Test
    void defaultDecoderKeepsFeignExceptionOverRealHttp() {
        server.stub("/bad-request", 400, "{\"code\":1001,\"msg\":\"inventory short\"}");

        GXFeignTestClients.RoundTripClient client = client(new StubFeignService(), null);

        assertThatThrownBy(client::badRequest).isInstanceOf(FeignException.class);
    }

    private GXFeignTestClients.RoundTripClient client(GXFeignService feignService, ErrorDecoder errorDecoder) {
        RequestInterceptor interceptor = new GXFeignRequestInterceptor(new FixedObjectProvider<>(feignService));
        Feign.Builder builder = Feign.builder()
                .contract(new Contract.Default())
                .encoder(new Encoder.Default())
                .decoder(new Decoder.Default())
                .requestInterceptor(interceptor);
        if (errorDecoder != null) {
            builder.errorDecoder(errorDecoder);
        }
        return builder.target(GXFeignTestClients.RoundTripClient.class, server.baseUrl());
    }

    private static final class StubFeignService implements GXFeignService {
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

    private record FixedObjectProvider<T>(T value) implements ObjectProvider<T> {
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
