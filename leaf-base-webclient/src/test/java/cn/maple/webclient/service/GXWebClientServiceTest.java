package cn.maple.webclient.service;

import cn.maple.core.framework.config.aware.GXApplicationContextAware;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GXWebClientServiceTest {
    private final GXWebClientService service = new GXWebClientService() {
        @Override
        public String getAuthTokenSecret() {
            return "secret";
        }
    };

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        GXTraceIdContextUtils.removeTraceId();
    }

    @Test
    void generateHttpAuthTokenReadsConfiguredSourceAndSecret() {
        new ApplicationContextRunner()
                .withUserConfiguration(GXApplicationContextAware.class)
                .withPropertyValues(
                        "maple.framework.web.client.token=source-token",
                        "maple.framework.web.client.secret=secret"
                )
                .run(context -> {
                    GXWebClientService defaultService = new GXWebClientService() {
                    };

                    assertThat(defaultService.generateHttpAuthToken()).isNotBlank();
                });
    }

    @Test
    void generateHttpAuthTokenRejectsInvalidInput() {
        assertThatThrownBy(() -> service.generateHttpAuthToken("", 60))
                .isInstanceOf(GXBusinessException.class)
                .hasMessage("Please configure maple.framework.web.client.token");

        assertThatThrownBy(() -> service.generateHttpAuthToken("source", 0))
                .isInstanceOf(GXBusinessException.class)
                .hasMessage("WebClient Token expiry must be greater than 0");
    }

    @Test
    void checkTokenValidityHandlesMissingInvalidAndValidToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(service.checkTokenValidity()).isFalse();

        request.addHeader(GXCommonConstant.X_AUTH_TOKEN, "invalid");
        assertThat(service.checkTokenValidity()).isFalse();

        MockHttpServletRequest validRequest = new MockHttpServletRequest();
        validRequest.addHeader(GXCommonConstant.X_AUTH_TOKEN, service.generateHttpAuthToken("source", 60));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(validRequest));
        assertThat(service.checkTokenValidity()).isTrue();
    }

    @Test
    void getTraceIdPrefersRequestAttributeThenMdcFallback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GXTraceIdContextUtils.TRACE_ID_KEY, "request-trace");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        GXTraceIdContextUtils.putTraceId("mdc-trace");

        assertThat(service.getTraceId()).isEqualTo("request-trace");

        request.removeAttribute(GXTraceIdContextUtils.TRACE_ID_KEY);
        assertThat(service.getTraceId()).isEqualTo("mdc-trace");
    }

    @Test
    void hmacMethodsDelegateToCommonUtility() {
        new ApplicationContextRunner()
                .withUserConfiguration(GXApplicationContextAware.class)
                .withBean(JsonMapper.class, JsonMapper::new)
                .run(context -> {
                    String hmac = service.generateHmac("payload", "secret");

                    assertThat(hmac).isNotBlank();
                    assertThat(service.checkHmac("secret", hmac, "payload")).isTrue();
                    assertThat(service.checkHmac("secret", hmac, "other")).isFalse();
                });
    }

    @Test
    void noOpLoggingHooksAreSafeToCall() {
        assertThatCode(() -> {
            service.logRequestResponse(null, "url", null, null, null, null, null, 1L);
            service.logRequestResponse(null, "url", null, 1L, "request-id");
            service.logRequestException(null, "url", new RuntimeException("boom"), "request-id");
        }).doesNotThrowAnyException();
    }
}
