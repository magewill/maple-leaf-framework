package cn.maple.core.framework.web.interceptor;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.service.GXRenewalTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GXRenewalTokenInterceptorTest {

    @Test
    void preHandleReturnsRefreshedTokenWhenRenewalIsNeeded() throws Exception {
        GXRenewalTokenService service = new GXRenewalTokenService() {
            @Override
            public boolean renewalToken() {
                return true;
            }

            @Override
            public String renewalTokenHeaderValue(Dict extraData) {
                return "new-token";
            }
        };
        GXRenewalTokenInterceptor interceptor = new GXRenewalTokenInterceptor(provider(service));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertTrue(result);
        assertEquals("new-token", response.getHeader("Renewal-Token"));
    }

    @Test
    void preHandleDoesNothingWhenServiceUnavailable() throws Exception {
        GXRenewalTokenInterceptor interceptor = new GXRenewalTokenInterceptor(provider(null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertTrue(result);
        assertNull(response.getHeader("Renewal-Token"));
    }

    @Test
    void preHandleKeepsLegacyRenewMarkerWhenRefreshTokenIsNotOverridden() throws Exception {
        GXRenewalTokenService service = new GXRenewalTokenService() {
            @Override
            public boolean renewalToken() {
                return true;
            }
        };
        GXRenewalTokenInterceptor interceptor = new GXRenewalTokenInterceptor(provider(service));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertTrue(result);
        assertEquals("renew", response.getHeader("Renewal-Token"));
    }

    private static ObjectProvider<GXRenewalTokenService> provider(GXRenewalTokenService service) {
        @SuppressWarnings("unchecked")
        ObjectProvider<GXRenewalTokenService> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique()).thenReturn(service);
        return provider;
    }
}
