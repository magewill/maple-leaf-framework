package cn.maple.core.framework.audit;

import cn.maple.core.framework.service.GXRequestBodyAdviceService;
import cn.maple.core.framework.service.impl.GXDynamicCallMethodServiceImpl;
import cn.maple.core.framework.service.impl.GXFileUploadServiceImpl;
import cn.maple.core.framework.service.impl.GXSensitiveFieldDeEncryptServiceImpl;
import cn.maple.core.framework.web.advice.GXRequestBodyAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * CURRENT BEHAVIOR - flip when fixed.
 *
 * <p>Characterizes the security defaults of the module: silent crypto downgrade, a usable
 * hardcoded default key, the full request body being stored as a request attribute, and
 * arbitrary class loading by name.</p>
 */
class GXSecurityBoundaryAuditTest {
    private static final String REQUEST_BODY_ATTRIBUTE = "JSON_REQUEST_BODY";

    @Test
    void decryptReturnsInputWhenCiphertextIsInvalid() {
        GXSensitiveFieldDeEncryptServiceImpl service = new GXSensitiveFieldDeEncryptServiceImpl();

        String result = service.decryptAlgorithm("not-a-valid-ciphertext", null);

        assertEquals("not-a-valid-ciphertext", result);
    }

    @Test
    void encryptUsesHardcodedDefaultKeyDeterministically() {
        GXSensitiveFieldDeEncryptServiceImpl service = new GXSensitiveFieldDeEncryptServiceImpl();

        String first = service.encryptAlgorithm("13800000000", null);
        String second = service.encryptAlgorithm("13800000000", null);

        assertNotNull(first);
        assertTrue(first.length() > 0);
        assertEquals(first, second);
    }

    @Test
    void requestBodyIsStoredWithSensitiveFields() throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/audit/body");
        ServletServerHttpRequest inputMessage = new ServletServerHttpRequest(servletRequest);
        ObjectProvider<GXRequestBodyAdviceService> provider = mock(ObjectProvider.class);
        GXRequestBodyAdvice advice = new GXRequestBodyAdvice(provider);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "alice");
        body.put("password", "s3cr3t-value");
        MethodParameter parameter = new MethodParameter(
                PayloadTarget.class.getDeclaredMethod("handle", Map.class), 0);
        Class<? extends HttpMessageConverter<?>> converterType = StringHttpMessageConverter.class;

        advice.afterBodyRead(body, inputMessage, parameter, Map.class, converterType);

        Object stored = servletRequest.getAttribute(REQUEST_BODY_ATTRIBUTE);
        assertNotNull(stored);
        assertTrue(stored.toString().contains("s3cr3t-value"));
    }

    @Test
    void arbitraryClassCanBeLoadedByName() throws Exception {
        GXDynamicCallMethodServiceImpl service = new GXDynamicCallMethodServiceImpl();
        Method loadClass = GXDynamicCallMethodServiceImpl.class.getDeclaredMethod("loadClass", String.class);
        loadClass.setAccessible(true);

        Object loaded = loadClass.invoke(service, "java.lang.ProcessBuilder");

        assertEquals(ProcessBuilder.class, loaded);
    }

    @Test
    void uploadStoragePathIsSharedInstanceState() throws Exception {
        GXFileUploadServiceImpl service = new GXFileUploadServiceImpl();
        Field storagePathField = GXFileUploadServiceImpl.class.getDeclaredField("fileStoragePath");
        storagePathField.setAccessible(true);
        Path firstRequest = Paths.get("build/audit/request-a").toAbsolutePath();
        Path secondRequest = Paths.get("build/audit/request-b").toAbsolutePath();

        storagePathField.set(service, firstRequest);
        String pathAfterFirstRequest = service.getStoragePath();
        storagePathField.set(service, secondRequest);

        assertEquals(firstRequest.toString(), pathAfterFirstRequest);
        assertEquals(secondRequest.toString(), service.getStoragePath());
    }

    static class PayloadTarget {
        @SuppressWarnings("unused")
        public void handle(Map<String, Object> body) {
            // no-op: used only to build a MethodParameter
        }
    }
}
