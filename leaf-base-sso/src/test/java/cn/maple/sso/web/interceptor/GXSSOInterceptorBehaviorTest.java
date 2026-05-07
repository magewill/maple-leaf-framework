package cn.maple.sso.web.interceptor;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.annotation.GXIgnoreLoginIntercept;
import cn.maple.sso.annotation.GXPermissionAnnotation;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.oauth.GXSSOAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXSSOInterceptorBehaviorTest {
    @Test
    void annotationLookupFindsAndCachesMergedAnnotations() throws Exception {
        HandlerMethod handlerMethod = new HandlerMethod(new DemoController(), "ignored");

        assertTrue(GXHandlerMethodAnnotationUtils.hasMergedAnnotation(handlerMethod, GXIgnoreLoginIntercept.class));
        assertTrue(GXHandlerMethodAnnotationUtils.hasMergedAnnotation(handlerMethod, GXIgnoreLoginIntercept.class));
    }

    @Test
    void permissionInterceptorReturnsAsciiForbiddenMessage() throws Exception {
        TestPermissionInterceptor interceptor = new TestPermissionInterceptor();
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.exposeUnauthorizedAccess(new MockHttpServletRequest(), response);

        assertEquals(403, response.getStatus());
        assertEquals("Forbidden", response.getErrorMessage());
    }

    @Test
    void ignoreLoginAnnotationDoesNotBypassPermissionChecks() throws Exception {
        TestPermissionInterceptor interceptor = new TestPermissionInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GXSSOConstant.SSO_TOKEN_ATTR, Dict.create().set("userId", 1L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new DemoController(), "protectedAction");

        boolean allowed = interceptor.preHandle(request, response, handlerMethod);

        assertFalse(allowed);
        assertEquals(403, response.getStatus());
        assertEquals("Forbidden", response.getErrorMessage());
    }

    private static class DemoController {
        @GXIgnoreLoginIntercept
        @SuppressWarnings("unused")
        public void ignored() {
        }

        @GXIgnoreLoginIntercept
        @GXPermissionAnnotation("demo:protected")
        @SuppressWarnings("unused")
        public void protectedAction() {
        }
    }

    private static class TestPermissionInterceptor extends GXSSOPermissionInterceptor {
        private boolean exposeUnauthorizedAccess(HttpServletRequest request, MockHttpServletResponse response) throws Exception {
            return unauthorizedAccess(request, response);
        }

        @Override
        protected GXSSOAuthorization getAuthorization() {
            return (token, permission) -> false;
        }
    }
}
