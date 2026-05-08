package cn.maple.webclient.handler;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.util.GXResultUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GXWebClientExceptionHandlerTest {
    private final GXWebClientExceptionHandler handler = new GXWebClientExceptionHandler();

    @Test
    void handleWebClientRequestExceptionReturnsInternalErrorResult() {
        WebClientRequestException exception = new WebClientRequestException(
                new RuntimeException("network"),
                HttpMethod.GET,
                URI.create("https://example.test"),
                HttpHeaders.EMPTY
        );

        GXResultUtils<Dict> result = handler.handleWebClientRequestException(exception);

        assertThat(result.getCode()).isEqualTo(HttpStatus.HTTP_INTERNAL_ERROR);
        assertThat(result.getMsg()).isEqualTo("WebClient网络请求异常");
    }

    @Test
    void handleWebClientResponseExceptionReturnsRemoteStatusResult() {
        WebClientResponseException exception = WebClientResponseException.create(
                HttpStatus.HTTP_BAD_GATEWAY,
                "Bad Gateway",
                HttpHeaders.EMPTY,
                "bad gateway".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        GXResultUtils<Dict> result = handler.handleWebClientResponseException(exception);

        assertThat(result.getCode()).isEqualTo(HttpStatus.HTTP_BAD_GATEWAY);
        assertThat(result.getMsg()).isEqualTo("WebClient remote service response exception");
    }
}
