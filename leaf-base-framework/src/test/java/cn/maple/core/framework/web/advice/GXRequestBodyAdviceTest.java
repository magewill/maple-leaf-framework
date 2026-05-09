package cn.maple.core.framework.web.advice;

import cn.maple.core.framework.dto.protocol.req.GXBaseReqProtocol;
import cn.maple.core.framework.exception.GXBeanValidateException;
import cn.maple.core.framework.service.GXRequestBodyAdviceService;
import cn.maple.core.framework.util.GXCommonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.context.request.RequestContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GXRequestBodyAdviceTest {

    private static <T> ObjectProvider<T> provider(T service) {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique()).thenReturn(service);
        when(provider.getIfAvailable()).thenReturn(service);
        return provider;
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void supportsUsesDefaultProtocolScopeWhenServiceUnavailable() {
        GXRequestBodyAdvice advice = new GXRequestBodyAdvice(provider(null));

        assertTrue(advice.supports(null, ValidRequest.class, null));
        assertFalse(advice.supports(null, String.class, null));
    }

    @Test
    void supportsAllRequestBodiesWhenConfigured() {
        GXRequestBodyAdvice advice = new GXRequestBodyAdvice(provider(null));

        try (MockedStatic<GXCommonUtils> commonUtils = Mockito.mockStatic(GXCommonUtils.class)) {
            commonUtils.when(() -> GXCommonUtils.getEnvironmentValue(
                    "maple.framework.web.advice.capture-all-request-bodies", boolean.class, false
            )).thenReturn(true);

            assertTrue(advice.supports(null, String.class, null));
        }
    }

    @Test
    void afterBodyReadDoesNotRequireRequestContext() {
        GXRequestBodyAdvice advice = new GXRequestBodyAdvice(provider(null));

        assertDoesNotThrow(() -> advice.afterBodyRead(new ValidRequest(), null, null, ValidRequest.class, null));
    }

    @Test
    void defaultServicePropagatesValidationFailure() {
        GXRequestBodyAdviceService service = new GXRequestBodyAdviceService() {
        };

        assertThrows(GXBeanValidateException.class,
                () -> service.afterBodyRead(new InvalidRequest(), null, null, InvalidRequest.class, null));
    }

    static class ValidRequest extends GXBaseReqProtocol {
        protected void verify() {
        }
    }

    static class InvalidRequest extends GXBaseReqProtocol {
        protected void verify() {
            throw new GXBeanValidateException("invalid");
        }
    }
}
