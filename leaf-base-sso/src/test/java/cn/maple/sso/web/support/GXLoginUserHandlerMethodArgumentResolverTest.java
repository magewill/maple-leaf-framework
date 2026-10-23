package cn.maple.sso.web.support;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.sso.annotation.GXLoginUserAnnotation;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.dto.GXUserInfoDto;
import cn.maple.sso.service.GXTokenConfigService;
import cn.maple.sso.service.GXUUserService;
import cn.maple.sso.support.GXSSOTestDoubles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("sso-global-config")
class GXLoginUserHandlerMethodArgumentResolverTest {
    private final GXLoginUserHandlerMethodArgumentResolver resolver = new GXLoginUserHandlerMethodArgumentResolver();
    private ApplicationContext originalApplicationContext;

    @BeforeEach
    void setUp() {
        originalApplicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        registerContext();
    }

    @AfterEach
    void tearDown() {
        ApplicationContext applicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        if (applicationContext instanceof GenericApplicationContext context) {
            context.close();
        }
        GXSSOTestDoubles.restoreApplicationContext(originalApplicationContext);
    }

    @Test
    void resolvesDictUserToDeclaredDtoType() throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(GXSSOConstant.SSO_TOKEN_ATTR, Dict.create().set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, 7L));

        Object result = resolver.resolveArgument(loginUserParameter(), null, new ServletWebRequest(servletRequest), null);

        assertTrue(result instanceof GXUserInfoDto);
        assertEquals(7L, ((GXUserInfoDto) result).getUserId());
        assertEquals("maple", ((GXUserInfoDto) result).getUsername());
    }

    @Test
    void missingUserThrowsBusinessException() throws Exception {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(GXTokenConfigService.class, () -> new GXTokenConfigService() {
        });
        context.registerBean(GXUUserService.class, () -> new GXUUserService() {
            @Override
            public Dict getUserByUserId(Long userId) {
                return Dict.create();
            }
        });
        context.refresh();
        GXSSOTestDoubles.installApplicationContext(context);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(GXSSOConstant.SSO_TOKEN_ATTR,
                Dict.create().set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, 7L));

        assertThrows(cn.maple.core.framework.exception.GXBusinessException.class,
                () -> resolver.resolveArgument(loginUserParameter(), null, new ServletWebRequest(servletRequest), null));
    }

    @Test
    void malformedFallbackTokenThrowsBusinessException() throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader(GXTokenConstant.USER_TOKEN_NAME, "not-a-valid-token");

        assertThrows(cn.maple.core.framework.exception.GXBusinessException.class,
                () -> resolver.resolveArgument(loginUserParameter(), null, new ServletWebRequest(servletRequest), null),
                "an unparsable token must fail loudly instead of injecting null");
    }

    @Test
    void anonymousRequestWithoutTokenThrowsBusinessException() throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        assertThrows(cn.maple.core.framework.exception.GXBusinessException.class,
                () -> resolver.resolveArgument(loginUserParameter(), null, new ServletWebRequest(servletRequest), null),
                "a request without any token must not inject null into a non nullable parameter");
    }

    @Test
    void supportsParameterAcceptsOnlyUserInfoDto() throws Exception {
        assertTrue(resolver.supportsParameter(loginUserParameter()),
                "GXUserInfoDto parameters must be supported");

        assertFalse(resolver.supportsParameter(parameterOf("dictParameter", Dict.class)),
                "Dict parameters are not supported: Spring would bind an empty Dict instead of the token");
        assertFalse(resolver.supportsParameter(parameterOf("longParameter", Long.class)),
                "Long parameters are not supported: read userId from GXUserInfoDto instead");
        assertFalse(resolver.supportsParameter(parameterOf("plainParameter", GXUserInfoDto.class)),
                "parameters without the annotation are not supported");
    }

    private static MethodParameter loginUserParameter() throws NoSuchMethodException {
        Method method = DemoController.class.getDeclaredMethod("demo", GXUserInfoDto.class);
        return new MethodParameter(method, 0);
    }

    private static MethodParameter parameterOf(String methodName, Class<?> parameterType) throws NoSuchMethodException {
        Method method = DemoController.class.getDeclaredMethod(methodName, parameterType);
        return new MethodParameter(method, 0);
    }

    private static void registerContext() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(GXTokenConfigService.class, () -> new GXTokenConfigService() {
        });
        context.registerBean(GXUUserService.class, () -> new GXUUserService() {
            @Override
            public Dict getUserByUserId(Long userId) {
                return Dict.create()
                        .set("userId", userId)
                        .set("username", "maple");
            }
        });
        context.refresh();
        GXSSOTestDoubles.installApplicationContext(context);
    }

    private static class DemoController {
        @SuppressWarnings("unused")
        void demo(@GXLoginUserAnnotation GXUserInfoDto userInfo) {
        }

        @SuppressWarnings("unused")
        void dictParameter(@GXLoginUserAnnotation Dict dict) {
        }

        @SuppressWarnings("unused")
        void longParameter(@GXLoginUserAnnotation Long userId) {
        }

        @SuppressWarnings("unused")
        void plainParameter(GXUserInfoDto userInfo) {
        }
    }
}
