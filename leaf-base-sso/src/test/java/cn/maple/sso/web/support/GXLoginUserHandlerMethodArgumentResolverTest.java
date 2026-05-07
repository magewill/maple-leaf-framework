package cn.maple.sso.web.support;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.sso.annotation.GXLoginUserAnnotation;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.dto.GXUserInfoDto;
import cn.maple.sso.service.GXTokenConfigService;
import cn.maple.sso.service.GXUUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        ReflectionTestUtils.setField(GXApplicationContextSingleton.INSTANCE, "applicationContext", originalApplicationContext);
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
    void malformedFallbackHeaderReturnsNull() throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader(GXTokenConstant.USER_TOKEN_NAME, "not-a-valid-token");

        Object result = resolver.resolveArgument(loginUserParameter(), null, new ServletWebRequest(servletRequest), null);

        assertNull(result);
    }

    private static MethodParameter loginUserParameter() throws NoSuchMethodException {
        Method method = DemoController.class.getDeclaredMethod("demo", GXUserInfoDto.class);
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
        ReflectionTestUtils.setField(GXApplicationContextSingleton.INSTANCE, "applicationContext", context);
    }

    private static class DemoController {
        @SuppressWarnings("unused")
        void demo(@GXLoginUserAnnotation GXUserInfoDto userInfo) {
        }
    }
}
