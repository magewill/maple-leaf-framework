package cn.maple.core.framework.util;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXCurrentRequestContextUtilsTest {
    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void requestAccessorsReturnNullOutsideHttpContext() {
        RequestContextHolder.resetRequestAttributes();

        assertNull(GXCurrentRequestContextUtils.getHttpServletRequest());
        assertNull(GXCurrentRequestContextUtils.getHttpServletResponse());
        assertTrue(GXCurrentRequestContextUtils.isRPC());
        assertFalse(GXCurrentRequestContextUtils.isHTTP());
        assertNull(GXCurrentRequestContextUtils.getHttpServletRequestAttribute("missing", String.class));
        assertNull(GXCurrentRequestContextUtils.setHttpServletRequestAttribute("name", "value"));
    }

    @Test
    void requestAttributeAccessorsHandleHttpContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        GXCurrentRequestContextUtils.setHttpServletRequestAttribute("name", "value");

        assertEquals("value", GXCurrentRequestContextUtils.getHttpServletRequestAttribute("name", String.class));
    }

    @Test
    void conversionAccessorsFallbackWhenTargetTypeOrValueIsInvalid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("age", "abc");
        request.addHeader("X-Age", "abc");
        request.setAttribute("age", "abc");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertEquals(0, GXCurrentRequestContextUtils.getHttpParam("age", int.class));
        assertEquals(99, GXCurrentRequestContextUtils.getHeader("X-Age", Integer.class, 99));
        assertNull(GXCurrentRequestContextUtils.getHeader("X-Age", null));
        assertNull(GXCurrentRequestContextUtils.getHttpServletRequestAttribute("age", Integer.class));
        assertNull(GXCurrentRequestContextUtils.getHttpServletRequestAttribute("age", null));
    }

    @Test
    void getLoginCredentialsUsesRequestAttributeWhenItIsDict() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Dict credentials = Dict.create().set(GXTokenConstant.TOKEN_USER_NAME_FIELD_NAME, "alice");
        request.setAttribute(GXCommonConstant.SSO_TOKEN_ATTR, credentials);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertEquals("alice", GXCurrentRequestContextUtils
                .getLoginCredentials(GXTokenConstant.TOKEN_NAME, "secret")
                .getStr(GXTokenConstant.TOKEN_USER_NAME_FIELD_NAME));
    }

    @Test
    void getLoginCredentialsIgnoresInvalidAttributeTypeAndInvalidToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GXCommonConstant.SSO_TOKEN_ATTR, "invalid");
        request.addHeader(GXTokenConstant.TOKEN_NAME, "not-a-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertTrue(GXCurrentRequestContextUtils.getLoginCredentials(GXTokenConstant.TOKEN_NAME, "secret").isEmpty());
    }

    @Test
    void getLoginCredentialsDecodesTokenHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String secretKey = "secret";
        String token = GXAuthCodeUtils.authCodeEncode("{\"userName\":\"alice\",\"userId\":7}", secretKey, 60);
        request.addHeader(GXTokenConstant.TOKEN_NAME, token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Dict credentials = GXCurrentRequestContextUtils.getLoginCredentials(GXTokenConstant.TOKEN_NAME, secretKey);

        assertEquals("alice", credentials.getStr("userName"));
        assertEquals(7, credentials.getInt("userId"));
    }

    @Test
    void getLoginFieldFromTokenHandlesBlankFieldNameAndConversionFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String secretKey = "secret";
        String token = GXAuthCodeUtils.authCodeEncode("{\"userId\":\"abc\"}", secretKey, 60);
        request.addHeader(GXTokenConstant.TOKEN_NAME, token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertNull(GXCurrentRequestContextUtils.getLoginFieldFromToken(
                GXTokenConstant.TOKEN_NAME, "", Integer.class, secretKey));
        assertNull(GXCurrentRequestContextUtils.getLoginFieldFromToken(
                GXTokenConstant.TOKEN_NAME, "userId", Integer.class, secretKey));
    }

    @Test
    void getLoginCredentialsReturnsEmptyForNonObjectTokenPayload() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String secretKey = "secret";
        request.addHeader(GXTokenConstant.TOKEN_NAME, GXAuthCodeUtils.authCodeEncode("[]", secretKey, 60));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertTrue(GXCurrentRequestContextUtils.getLoginCredentials(GXTokenConstant.TOKEN_NAME, secretKey).isEmpty());
    }

    @Test
    void tokenExistsChecksCurrentRequestWithoutAssertDependency() {
        RequestContextHolder.resetRequestAttributes();
        assertFalse(GXCurrentRequestContextUtils.tokenExists());

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertFalse(GXCurrentRequestContextUtils.tokenExists());

        request.addHeader(GXTokenConstant.TOKEN_NAME, "token");
        assertTrue(GXCurrentRequestContextUtils.tokenExists());
    }

    @Test
    void rateLimiterIsSafeForConcurrentCalls() throws Exception {
        int taskCount = 64;
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>(taskCount);
            for (int i = 0; i < taskCount; i++) {
                tasks.add(() -> GXCurrentRequestContextUtils.isRateLimited("client-" + System.nanoTime()));
            }

            List<Future<Boolean>> futures = executorService.invokeAll(tasks);

            for (Future<Boolean> future : futures) {
                assertFalse(future.get());
            }
        } finally {
            executorService.close();
        }
    }

    @Test
    void invalidIpByteArrayThrowsBusinessException() {
        GXBusinessException exception = assertThrows(GXBusinessException.class,
                () -> GXCurrentRequestContextUtils.isInternalV4IP(new byte[]{1, 2, 3}));

        assertEquals(HttpStatus.HTTP_INTERNAL_ERROR, exception.getCode());
    }

    @Test
    void filterXssRejectsOversizedInput() {
        String oversized = "a".repeat(1024 * 1024 + 1);

        assertThrows(IllegalArgumentException.class, () -> GXCurrentRequestContextUtils.filterXSS(oversized));
    }

    @Test
    void safeHeaderRejectsInvalidValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Test", "select * from users");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertDoesNotThrow(() -> GXCurrentRequestContextUtils.getSafeHeader("missing"));
        assertThrows(GXBusinessException.class, () -> GXCurrentRequestContextUtils.getSafeHeader("X-Test"));
    }
}
