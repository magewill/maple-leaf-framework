package cn.maple.sso.web.interceptor;

import cn.maple.core.framework.annotation.GXIgnoreLoginIntercept;
import cn.maple.sso.annotation.GXPermissionAnnotation;
import cn.maple.sso.oauth.GXSSOAuthorization;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.utils.GXSSOHelperUtil;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXSSOInterceptorBehaviorTest {
    @BeforeEach
    void setUp() {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());
    }

    @AfterEach
    void tearDown() {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());
    }

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
    void ignoreLoginAnnotationBypassesPermissionTokenValidation() throws Exception {
        TestPermissionInterceptor interceptor = new TestPermissionInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new DemoController(), "protectedAction");

        boolean allowed = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(allowed);
        assertEquals(200, response.getStatus());
    }

    @Test
    void ignoreLoginAnnotationWithoutPermissionAllowsAnonymousRequest() throws Exception {
        TestPermissionInterceptor interceptor = new TestPermissionInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new DemoController(), "ignored");

        boolean allowed = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(allowed);
        assertEquals(200, response.getStatus());
    }

    @Test
    void authorizationInterceptorSkipsTokenValidationForIgnoreLoginAnnotation() throws Exception {
        GXSSOAuthorizationInterceptor interceptor = new GXSSOAuthorizationInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new DemoController(), "ignored");

        boolean allowed = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(allowed);
        assertEquals(200, response.getStatus());
    }

    @Test
    void authorizationInterceptorSkipsTokenValidationForErrorDispatch() throws Exception {
        GXSSOAuthorizationInterceptor interceptor = new GXSSOAuthorizationInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new DemoController(), "unannotated");

        boolean allowed = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(allowed);
        assertEquals(200, response.getStatus());
    }

    @Test
    void authorizationInterceptorDoesNotSkipNormalErrorPathRequest() throws Exception {
        GXSSOAuthorizationInterceptor interceptor = new GXSSOAuthorizationInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new DemoController(), "unannotated");

        boolean allowed = interceptor.preHandle(request, response, handlerMethod);

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
    }

    @Test
    void ignoreLoginAnnotationAllowsAnonymousRequestWhenUriPermissionModeEnabled() throws Exception {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setPermissionUri(true));
        TestPermissionInterceptor interceptor = new TestPermissionInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new DemoController(), "ignored");

        boolean allowed = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(allowed);
        assertEquals(200, response.getStatus());
    }

    @Test
    void permissionSkipDoesNotRequireTokenWhenUriPermissionModeEnabled() throws Exception {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setPermissionUri(true));
        TestPermissionInterceptor interceptor = new TestPermissionInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new DemoController(), "skipPermission");

        boolean allowed = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(allowed);
        assertEquals(200, response.getStatus());
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

        @GXPermissionAnnotation(action = cn.maple.sso.enums.GXAction.Skip)
        @SuppressWarnings("unused")
        public void skipPermission() {
        }

        @SuppressWarnings("unused")
        public void unannotated() {
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
