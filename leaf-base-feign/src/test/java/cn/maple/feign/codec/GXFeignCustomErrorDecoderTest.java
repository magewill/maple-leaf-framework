package cn.maple.feign.codec;

import cn.maple.core.framework.exception.GXBusinessException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GXFeignCustomErrorDecoderTest {
    private static final String REQUEST_URL = "http://example.test/resource";

    private final GXFeignCustomErrorDecoder decoder = new GXFeignCustomErrorDecoder();

    @Test
    void mapsBadRequestToHttpStatusAndRemoteMessage() {
        Exception exception = decoder.decode("Client#get", response(400, "{\"code\":0,\"msg\":\"invalid parameter\"}"));

        assertThat(exception).isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("invalid parameter");
        assertThat(((GXBusinessException) exception).getCode()).isEqualTo(400);
    }

    @Test
    void overridesCodeAndMessageWithRemoteBusinessResult() {
        Exception exception = decoder.decode("Client#get",
                response(400, "{\"code\":1001,\"msg\":\"inventory short\",\"data\":{\"sku\":\"A1\"}}"));

        assertThat(exception).isInstanceOf(GXBusinessException.class);
        GXBusinessException businessException = (GXBusinessException) exception;
        assertThat(businessException.getCode()).isEqualTo(1001);
        assertThat(businessException.getMessage()).contains("inventory short");
        assertThat(businessException.getData()).containsEntry("sku", "A1");
    }

    @Test
    void unauthorizedDoesNotLeakResponseBody() {
        Exception exception = decoder.decode("Client#get", response(401, "secret-token-abc"));

        assertThat(exception).isInstanceOf(GXBusinessException.class)
                .hasMessageNotContaining("secret-token-abc");
        assertThat(((GXBusinessException) exception).getCode()).isEqualTo(401);
    }

    @Test
    void forbiddenDoesNotLeakResponseBody() {
        Exception exception = decoder.decode("Client#get", response(403, "internal-acl-detail"));

        assertThat(exception).isInstanceOf(GXBusinessException.class)
                .hasMessageNotContaining("internal-acl-detail");
        assertThat(((GXBusinessException) exception).getCode()).isEqualTo(403);
    }

    @Test
    void notFoundKeepsRequestUrl() {
        Exception exception = decoder.decode("Client#get", response(404, ""));

        assertThat(exception).isInstanceOf(GXBusinessException.class)
                .hasMessageContaining(REQUEST_URL);
        assertThat(((GXBusinessException) exception).getCode()).isEqualTo(404);
    }

    @Test
    void conflictKeepsRemoteMessage() {
        Exception exception = decoder.decode("Client#get", response(409, "{\"code\":409,\"msg\":\"duplicated order\"}"));

        assertThat(exception).isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("duplicated order");
        assertThat(((GXBusinessException) exception).getCode()).isEqualTo(409);
    }

    @Test
    void unprocessableEntityKeepsRemoteMessage() {
        Exception exception = decoder.decode("Client#get", response(422, "{\"code\":422,\"msg\":\"unprocessable\"}"));

        assertThat(exception).isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("unprocessable");
        assertThat(((GXBusinessException) exception).getCode()).isEqualTo(422);
    }

    @Test
    void tooManyRequestsKeepsRemoteMessage() {
        Exception exception = decoder.decode("Client#get", response(429, "{\"code\":429,\"msg\":\"slow down\"}"));

        assertThat(exception).isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("slow down");
        assertThat(((GXBusinessException) exception).getCode()).isEqualTo(429);
    }

    @Test
    void internalErrorUsesFixedTextWithoutBody() {
        Exception exception = decoder.decode("Client#get", response(500, "java.lang.NullPointerException at com.acme"));

        assertThat(exception).isInstanceOf(GXBusinessException.class)
                .hasMessageNotContaining("NullPointerException");
        assertThat(((GXBusinessException) exception).getCode()).isEqualTo(500);
    }

    @Test
    void serviceUnavailableUsesFixedText() {
        Exception exception = decoder.decode("Client#get", response(503, "boom"));

        assertThat(exception).isInstanceOf(GXBusinessException.class);
        assertThat(((GXBusinessException) exception).getCode()).isEqualTo(503);
    }

    @Test
    void gatewayTimeoutUsesFixedText() {
        Exception exception = decoder.decode("Client#get", response(504, "timeout"));

        assertThat(exception).isInstanceOf(GXBusinessException.class);
        assertThat(((GXBusinessException) exception).getCode()).isEqualTo(504);
    }

    @Test
    void unhandledStatusDelegatesToDefaultDecoder() {
        Response response = response(418, "teapot");

        Exception exception = decoder.decode("Client#get", response);

        assertThat(exception).isNotInstanceOf(GXBusinessException.class);
    }

    @Test
    void noContentDelegatesToDefaultDecoder() {
        Response response = response(204, "");

        Exception exception = decoder.decode("Client#get", response);

        assertThat(exception).isNotInstanceOf(GXBusinessException.class);
    }

    @Test
    void redirectDelegatesToDefaultDecoder() {
        Response response = response(301, "");

        Exception exception = decoder.decode("Client#get", response);

        assertThat(exception).isNotInstanceOf(GXBusinessException.class);
    }

    @Test
    void truncatesLargeErrorResponseBody() {
        String largeBody = "x".repeat(9000);

        Exception exception = decoder.decode("Client#get", response(400, largeBody));

        assertThat(exception).isInstanceOf(GXBusinessException.class);
        assertThat(exception.getMessage()).contains("...[truncated]");
        assertThat(exception.getMessage().length()).isLessThan(8500);
    }

    @Test
    void emptyBodyFallsBackToFixedText() {
        Exception exception = decoder.decode("Client#get", response(400, ""));

        assertThat(exception).isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("bad request");
        assertThat(((GXBusinessException) exception).getCode()).isEqualTo(400);
    }

    @Test
    void malformedJsonFallsBackToRawText() {
        Exception exception = decoder.decode("Client#get", response(400, "{not a json"));

        assertThat(exception).isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("{not a json");
        assertThat(((GXBusinessException) exception).getCode()).isEqualTo(400);
    }

    @Test
    void bodyReadFailureFallsBackToFixedText() {
        Response response = Response.builder()
                .request(request())
                .status(400)
                .reason("status-400")
                .body(new FailingBody())
                .build();

        Exception exception = decoder.decode("Client#get", response);

        assertThat(exception).isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("Failed to read error response body");
        assertThat(((GXBusinessException) exception).getCode()).isEqualTo(400);
    }

    private Response response(int status, String body) {
        return Response.builder()
                .request(request())
                .status(status)
                .reason("status-" + status)
                .body(body, StandardCharsets.UTF_8)
                .build();
    }

    private Request request() {
        return Request.create(
                Request.HttpMethod.GET,
                REQUEST_URL,
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null
        );
    }

    private static final class FailingBody implements Response.Body {
        @Override
        public Integer length() {
            return null;
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public InputStream asInputStream() throws IOException {
            throw new IOException("simulated read failure");
        }

        @Override
        public Reader asReader(Charset charset) throws IOException {
            throw new IOException("simulated read failure");
        }

        @Override
        public void close() {
        }
    }
}
