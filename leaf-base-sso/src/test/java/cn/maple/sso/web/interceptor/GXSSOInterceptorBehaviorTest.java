package cn.maple.sso.web.interceptor;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.annotation.GXIgnoreLoginIntercept;
import cn.maple.sso.annotation.GXPermissionAnnotation;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.oauth.GXSSOAuthorization;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.properties.GXUrlWhiteListsConfigProperties;
import cn.maple.sso.utils.GXSSOHelperUtil;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;


import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("sso-global-config")
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
    void capacityEvictsOldestInsteadOfClear() throws Exception {
        GXSSOProperties properties = new GXSSOProperties();
        properties.setCapacity(4);
        GXSSOHelperUtil.setSsoConfig(properties);

        String[] methodNames = {"first", "second", "third", "fourth", "fifth"};
        for (String methodName : methodNames) {
            HandlerMethod handlerMethod = new HandlerMethod(new CacheBurstController(), methodName);
            GXHandlerMethodAnnotationUtils.hasIgnoreAnnotation(handlerMethod);
            GXHandlerMethodAnnotationUtils.hasHttpInvokerTokenAnnotation(handlerMethod);
            GXHandlerMethodAnnotationUtils.permissionInfo(handlerMethod);
        }

        assertEquals(4, GXHandlerMethodAnnotationUtils.cacheSize(),
                "a burst of new handlers must evict down to sso.config.capacity instead of clearing the whole cache");
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
    void configuredIllegalUrlRedirectsInsteadOfForbidden() throws Exception {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setIllegalUrl("https://sso.example.com/403"));
        GXSSOPermissionInterceptor interceptor = new GXSSOPermissionInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/secure");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // A permission-annotated handler denies an anonymous request on its own, so the case does
        // not depend on nothing-annotation-pass: the only variable under test is illegal-url.
        assertFalse(interceptor.preHandle(request, response,
                        new HandlerMethod(new DemoController(), "permissionNormal")),
                "a permission-annotated handler without a token must be denied");
        assertEquals(302, response.getStatus(), "a configured illegal-url turns the denial into a redirect");
        assertEquals("https://sso.example.com/403", response.getRedirectedUrl(),
                "sso.config.illegal-url must drive the redirect target");
    }

    @Test
    void explicitIllegalUrlSetterOverridesConfiguration() throws Exception {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setIllegalUrl("https://config.example.com/403"));
        GXSSOPermissionInterceptor interceptor = new GXSSOPermissionInterceptor();
        assertEquals("https://config.example.com/403", interceptor.getIllegalUrl(),
                "the interceptor must read sso.config.illegal-url");

        interceptor.setIllegalUrl("https://setter.example.com/403");
        assertEquals("https://setter.example.com/403", interceptor.getIllegalUrl(),
                "an explicit setter call must win over configuration");

        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(new MockHttpServletRequest("GET", "/api/secure"), response,
                new HandlerMethod(new DemoController(), "permissionNormal"));
        assertEquals("https://setter.example.com/403", response.getRedirectedUrl(),
                "the denial must redirect to the setter value, not to the configured value");
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

    @Test
    void nothingAnnotationPassIsReadFromConfiguration() throws Exception {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setNothingAnnotationPass(false));
        GXSSOPermissionInterceptor interceptor = new GXSSOPermissionInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/secure");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new HandlerMethod(new DemoController(), "unannotated")),
                "sso.config.nothing-annotation-pass=false must deny handlers without a permission annotation");
        assertEquals(403, response.getStatus());
    }

    @Test
    void nothingAnnotationPassPrefersExplicitSetterOverConfiguration() {
        GXSSOPermissionInterceptor interceptor = new GXSSOPermissionInterceptor();
        assertTrue(interceptor.isNothingAnnotationPass(), "default must stay true (FR-019)");

        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setNothingAnnotationPass(false));
        assertFalse(interceptor.isNothingAnnotationPass(), "the interceptor must read the configuration value");

        interceptor.setNothingAnnotationPass(true);
        assertTrue(interceptor.isNothingAnnotationPass(), "an explicit setter call wins over configuration");
    }

    @Test
    void blankUriIsDeniedWhenUriPermissionModeEnabled() throws Exception {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setPermissionUri(true));
        GXSSOPermissionInterceptor interceptor = new GXSSOPermissionInterceptor() {
            @Override
            protected GXSSOAuthorization getAuthorization() {
                return (token, permission) -> true;
            }
        };
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GXSSOConstant.SSO_TOKEN_ATTR, Dict.create().set("userId", 7L));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new HandlerMethod(new DemoController(), "unannotated")),
                "a blank request uri must fail closed in uri mode, even when the authorizer permits everything");
        assertEquals(403, response.getStatus());
    }

    @ParameterizedTest(name = "kind={0} uriMode={1} nothingAnnotationPass={2} -> allowed={3}")
    @CsvSource({
            "none,false,true,true",
            "none,false,false,false",
            "none,true,true,false",
            "none,true,false,false",
            "normal,false,true,false",
            "normal,false,false,false",
            "normal,true,true,false",
            "normal,true,false,false",
            "skip,false,true,true",
            "skip,false,false,true",
            "skip,true,true,true",
            "skip,true,false,true"
    })
    void permissionMatrixIsDecidedBySingleChain(String kind, boolean uriMode,
                                               boolean nothingAnnotationPass, boolean expected) throws Exception {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setPermissionUri(uriMode));
        TestPermissionInterceptor interceptor = new TestPermissionInterceptor();
        interceptor.setNothingAnnotationPass(nothingAnnotationPass);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/secure");
        request.setAttribute(GXSSOConstant.SSO_TOKEN_ATTR, Dict.create().set("userId", 7L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new DemoController(), methodFor(kind));

        boolean allowed = interceptor.preHandle(request, response, handlerMethod);

        assertEquals(expected, allowed, "permission chain must have exactly one decision point");
        if (!expected) {
            assertEquals(403, response.getStatus());
        }
    }

    @Test
    void whiteListMatchesPathWithinContextPath() throws Exception {
        GXSSOAuthorizationInterceptor interceptor = interceptorWithWhiteList("/public/**");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/ping");
        request.setContextPath("/api");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), unannotatedHandler()),
                "the whitelist must be matched against the path within the application");
    }

    @Test
    void whiteListAcceptsRawUriForm() throws Exception {
        GXSSOAuthorizationInterceptor interceptor = interceptorWithWhiteList("/api/public/**");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/ping");
        request.setContextPath("/api");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), unannotatedHandler()),
                "a whitelist entry that already contains the context path must keep working");
    }

    @Test
    void emptyWhiteListIsFailClosed() throws Exception {
        GXSSOAuthorizationInterceptor interceptor = interceptorWithWhiteList();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, unannotatedHandler()),
                "without a whitelist the request must still require a token");
    }

    @Test
    void trailingSlashPatternIsHonoured() throws Exception {
        GXSSOAuthorizationInterceptor interceptor = interceptorWithWhiteList("/public/");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), unannotatedHandler()));
    }

    private static HandlerMethod unannotatedHandler() throws NoSuchMethodException {
        return new HandlerMethod(new DemoController(), "unannotated");
    }

    private static GXSSOAuthorizationInterceptor interceptorWithWhiteList(String... patterns) {
        GXSSOAuthorizationInterceptor interceptor = new GXSSOAuthorizationInterceptor();
        GXUrlWhiteListsConfigProperties config = new GXUrlWhiteListsConfigProperties();
        config.setWhiteLists(List.of(patterns));
        interceptor.setUrlWhiteListsConfigProperties(config);
        return interceptor;
    }

    private static String methodFor(String kind) {
        return switch (kind) {
            case "normal" -> "permissionNormal";
            case "skip" -> "skipPermission";
            case "ignore" -> "ignored";
            default -> "unannotated";
        };
    }

    private static class DemoController {
        @GXIgnoreLoginIntercept
        @SuppressWarnings("unused")
        public void ignored() {
        }

        @GXPermissionAnnotation("demo:read")
        @SuppressWarnings("unused")
        public void permissionNormal() {
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

    /**
     * Dedicated handler type for the cache capacity test: its bean type name is unique, so the
     * keys inserted by the test can never be pre-populated by other cases in the same JVM.
     */
    @SuppressWarnings("unused")
    private static class CacheBurstController {
        public void first() {
        }

        public void second() {
        }

        @GXPermissionAnnotation("burst:third")
        public void third() {
        }

        public void fourth() {
        }

        @GXIgnoreLoginIntercept
        public void fifth() {
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
