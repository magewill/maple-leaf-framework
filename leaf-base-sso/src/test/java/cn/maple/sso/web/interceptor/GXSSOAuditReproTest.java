package cn.maple.sso.web.interceptor;

import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXAuthCodeUtils;
import cn.maple.sso.annotation.GXPermissionAnnotation;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.metrics.GXSSOMetrics;
import cn.maple.sso.oauth.GXSSOAuthorization;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.service.GXAbstractSSOService;
import cn.maple.sso.service.GXTokenConfigService;
import cn.maple.sso.support.GXSSOTestDoubles;
import cn.maple.sso.utils.GXSSOHelperUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproductions of the audit findings. Every case in this class MUST fail on the
 * pre-hardening implementation and pass afterwards.
 */
@ResourceLock("sso-global-config")
class GXSSOAuditReproTest {
    private final TestSSOService service = new TestSSOService();

    private ApplicationContext originalApplicationContext;

    @BeforeEach
    void setUp() {
        originalApplicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());
        GXSSOMetrics.reset();
    }

    @AfterEach
    void tearDown() {
        ApplicationContext applicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        if (applicationContext instanceof GenericApplicationContext context) {
            context.close();
        }
        RequestContextHolder.resetRequestAttributes();
        System.clearProperty("spring.profiles.active");
        GXSSOTestDoubles.restoreApplicationContext(originalApplicationContext);
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());
        GXSSOMetrics.reset();
    }

    @Test
    void f01UriPermissionModeIsBypassedByDefault() throws Exception {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setPermissionUri(true));
        DenyAllPermissionInterceptor interceptor = new DenyAllPermissionInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/secure");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new DemoController(), "unannotated");

        boolean allowed = interceptor.preHandle(request, response, handlerMethod);

        assertFalse(allowed, "URI mode must decide by itself instead of falling back to nothingAnnotationPass");
        assertEquals(403, response.getStatus());
    }

    @Test
    void f02NoCacheImplMeansNoServerSideValidation() {
        GXSSOTestDoubles.FixedTokenConfigService tokenConfigService = new GXSSOTestDoubles.FixedTokenConfigService();
        registerContext(tokenConfigService);
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        request.addHeader("token", forgeToken(tokenConfigService.getTokenSecret(), 7L));

        Dict token = service.getSSOToken(request);

        assertTrue(token.isEmpty(), "without a cache implementation the token must not authenticate");
    }

    @Test
    void f03DefaultSecretIsRejectedInProduction() {
        GXSSOTestDoubles.FixedTokenConfigService tokenConfigService = new GXSSOTestDoubles.FixedTokenConfigService()
                .secret(cn.maple.core.framework.constant.GXTokenConstant.TOKEN_SECRET_KEY);
        registerContext(tokenConfigService);
        System.setProperty("spring.profiles.active", "prod");

        assertThrows(GXBusinessException.class, service::runValidation,
                "production profile must refuse to start with the framework default secret");
    }

    @Test
    void f04DefaultConfigIssuesNonExpiringToken() {
        AtomicInteger ttl = new AtomicInteger(Integer.MIN_VALUE);
        registerContext(new GXSSOTestDoubles.FixedTokenConfigService());
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setCache(new GXSSOCache() {
            @Override
            public boolean set(Dict ssoToken, int expires) {
                ttl.set(expires);
                return true;
            }
        }));

        service.setCookie(new MockHttpServletRequest(), new MockHttpServletResponse(),
                Dict.create().set("userId", 7L));

        assertTrue(ttl.get() > 0, "default configuration must issue a token with an absolute expiry");
    }

    @Test
    void f15InterceptorChainOrderIsExplicit() {
        Order authorizationOrder = GXSSOAuthorizationInterceptor.class.getAnnotation(Order.class);
        Order permissionOrder = GXSSOPermissionInterceptor.class.getAnnotation(Order.class);

        assertNotNull(authorizationOrder, "login interceptor must declare an explicit order");
        assertNotNull(permissionOrder, "permission interceptor must declare an explicit order");
        assertEquals(GXSSOConstant.AUTHORIZATION_ORDER, authorizationOrder.value());
        assertEquals(GXSSOConstant.PERMISSION_ORDER, permissionOrder.value());
        assertTrue(authorizationOrder.value() < permissionOrder.value(), "login interceptor must run first");
    }

    private void registerContext(GXTokenConfigService tokenConfigService) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(GXTokenConfigService.class, () -> tokenConfigService);
        context.refresh();
        GXSSOTestDoubles.installApplicationContext(context);
    }

    private String forgeToken(String secret, Long userId) {
        return GXAuthCodeUtils.authCodeEncode(
                JSONUtil.toJsonStr(Dict.create().set("userId", userId).set("loginAt", 1L)), secret, 0);
    }

    private static class TestSSOService extends GXAbstractSSOService {
        private void runValidation() {
            validateConfiguration();
        }
    }

    private static class DenyAllPermissionInterceptor extends GXSSOPermissionInterceptor {
        @Override
        protected GXSSOAuthorization getAuthorization() {
            return (token, permission) -> false;
        }
    }

    private static class DemoController {
        @SuppressWarnings("unused")
        public void unannotated() {
        }

        @SuppressWarnings("unused")
        @GXPermissionAnnotation("demo:read")
        public void protectedAction() {
        }
    }
}
