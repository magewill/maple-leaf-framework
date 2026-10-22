package cn.maple.core.framework.audit;

import cn.maple.core.framework.filter.GXXssHttpServletRequestWrapper;
import cn.maple.core.framework.handler.GXExceptionHandler;
import cn.maple.core.framework.util.GXResultUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MultipartException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CURRENT BEHAVIOR - flip when fixed.
 *
 * <p>Characterizes the JSON body rewrite of the XSS wrapper (including the stale
 * Content-Length header) and the information disclosure of the multipart exception handler.</p>
 */
class GXWebBoundaryAuditTest {
    @Test
    void jsonBodyValuesAreHtmlRewritten() throws Exception {
        String json = "{\"note\":\"a&b<c\"}";
        MockHttpServletRequest request = jsonRequest(json);

        GXXssHttpServletRequestWrapper wrapper = new GXXssHttpServletRequestWrapper(request);
        String rewritten = new String(wrapper.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(rewritten.contains("a&amp;b"));
        assertFalse(rewritten.contains("<c"));
    }

    @Test
    void contentLengthHeaderStaysStaleAfterRewrite() throws Exception {
        String json = "{\"note\":\"a&b<c\"}";
        MockHttpServletRequest request = jsonRequest(json);
        int originalLength = json.getBytes(StandardCharsets.UTF_8).length;

        GXXssHttpServletRequestWrapper wrapper = new GXXssHttpServletRequestWrapper(request);

        assertNotEquals(originalLength, wrapper.getContentLength());
        assertEquals(String.valueOf(originalLength), wrapper.getHeader("Content-Length"));
    }

    @Test
    void multipartExceptionEchoesCauseMessage() {
        GXExceptionHandler handler = new GXExceptionHandler();
        MultipartException exception = new MultipartException(
                "upload failed", new IllegalStateException("/srv/internal/upload-dir is not writable"));

        GXResultUtils<String> result = handler.handleMultipartException(exception, null);

        assertEquals("/srv/internal/upload-dir is not writable", result.getMsg());
    }

    private MockHttpServletRequest jsonRequest(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/audit/xss");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body);
        request.addHeader("Content-Length", String.valueOf(body.length));
        return request;
    }
}
