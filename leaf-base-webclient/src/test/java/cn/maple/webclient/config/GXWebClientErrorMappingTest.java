package cn.maple.webclient.config;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.webclient.testsupport.GXWebClientTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Error body mapping matrix: unified response shape, Spring default shape, plain
 * text, empty body, invalid JSON and body code override.
 */
class GXWebClientErrorMappingTest {
    private final GXWebClientConfig config = new GXWebClientConfig();

    @Test
    void preservesUnifiedResponseMsgField() {
        GXBusinessException error = exchange(HttpStatus.INTERNAL_SERVER_ERROR,
                "{\"code\":500,\"msg\":\"remote detail\",\"data\":null}");

        assertThat(error.getCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(error.getMessage()).isEqualTo("服务器处理错误(5xx): remote detail");
    }

    @Test
    void keepsSpringDefaultMessageShape() {
        GXBusinessException error = exchange(HttpStatus.SERVICE_UNAVAILABLE,
                "{\"timestamp\":\"2026-10-23T00:00:00Z\",\"status\":503,\"message\":\"spring detail\",\"path\":\"/x\"}");

        assertThat(error.getCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(error.getMessage()).contains("spring detail");
    }

    @Test
    void fallsBackToStatusDescriptionForNonJsonBody() {
        GXBusinessException error = exchange(HttpStatus.BAD_REQUEST, "<html>bad request</html>");

        assertThat(error.getMessage()).isEqualTo("客户端请求错误(4xx): 请求参数错误");
    }

    @Test
    void fallsBackToStatusDescriptionForEmptyBody() {
        GXBusinessException error = exchange(HttpStatus.NOT_FOUND, "");

        assertThat(error.getCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(error.getMessage()).isEqualTo("客户端请求错误(4xx): 资源未找到");
    }

    @Test
    void fallsBackToStatusDescriptionForInvalidJson() {
        GXBusinessException error = exchange(HttpStatus.BAD_GATEWAY, "{\"msg\":");

        assertThat(error.getMessage()).isEqualTo("服务器处理错误(5xx): 网关错误");
    }

    @Test
    void overridesCodeWithBodyCode() {
        GXBusinessException error = exchange(HttpStatus.SERVICE_UNAVAILABLE,
                "{\"code\":504,\"msg\":\"gateway timed out\"}");

        assertThat(error.getCode()).isEqualTo(504);
        assertThat(error.getMessage()).contains("gateway timed out");
    }

    private GXBusinessException exchange(HttpStatus status, String body) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.web.client.retry-max-attempts", "0");
        WebClient webClient = GXWebClientTestSupport.plainBuilder(config, environment, null)
                .exchangeFunction(request -> Mono.just(ClientResponse.create(status)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .build()))
                .build();

        Throwable error = catchThrowable(() -> webClient.get().uri("http://127.0.0.1/ping")
                .retrieve()
                .bodyToMono(String.class)
                .block());
        assertThat(error).isInstanceOf(GXBusinessException.class);
        return (GXBusinessException) error;
    }
}
