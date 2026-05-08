package cn.maple.feign.codec;

import cn.maple.core.framework.exception.GXBusinessException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GXFeignCustomErrorDecoderTest {
    private final GXFeignCustomErrorDecoder decoder = new GXFeignCustomErrorDecoder();

    @Test
    void mapsBadRequestToBusinessExceptionWithResponseBody() {
        Response response = response(400, "invalid parameter");

        Exception exception = decoder.decode("Client#get", response);

        assertThat(exception).isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("invalid parameter");
    }

    @Test
    void truncatesLargeErrorResponseBody() {
        String largeBody = "x".repeat(9000);
        Response response = response(400, largeBody);

        Exception exception = decoder.decode("Client#get", response);

        assertThat(exception).isInstanceOf(GXBusinessException.class);
        assertThat(exception.getMessage()).contains("...[truncated]");
        assertThat(exception.getMessage().length()).isLessThan(8500);
    }

    @Test
    void delegatesUnhandledStatusToDefaultDecoder() {
        Response response = response(418, "teapot");

        Exception exception = decoder.decode("Client#get", response);

        assertThat(exception).isNotInstanceOf(GXBusinessException.class);
    }

    private Response response(int status, String body) {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "http://example.test/resource",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        return Response.builder()
                .request(request)
                .status(status)
                .reason("status-" + status)
                .body(body, StandardCharsets.UTF_8)
                .build();
    }
}
