package cn.maple.core.framework.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GXXssHttpServletRequestWrapper")
class GXXssHttpServletRequestWrapperTest {

    @Test
    @DisplayName("filters parameter values")
    void testParameterXssFiltering() throws IOException {
        HttpServletRequest request = jsonRequest("");
        when(request.getParameter("test")).thenReturn("<script>alert(1)</script>");

        GXXssHttpServletRequestWrapper wrapper = new GXXssHttpServletRequestWrapper(request);

        assertEquals("alert(1)", wrapper.getParameter("test"));
    }

    @Test
    @DisplayName("filters parameter arrays without mutating the source array")
    void testParameterValuesXssFiltering() throws IOException {
        HttpServletRequest request = jsonRequest("");
        String[] values = {"<script>alert(1)</script>", "normal text", null};
        when(request.getParameterValues("test")).thenReturn(values);

        GXXssHttpServletRequestWrapper wrapper = new GXXssHttpServletRequestWrapper(request);

        assertArrayEquals(new String[]{"alert(1)", "normal text", null}, wrapper.getParameterValues("test"));
        assertArrayEquals(new String[]{"<script>alert(1)</script>", "normal text", null}, values);
    }

    @Test
    @DisplayName("filters parameter map values without mutating the source map")
    void testParameterMapXssFiltering() throws IOException {
        HttpServletRequest request = jsonRequest("");
        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("param1", new String[]{"<script>alert(1)</script>", "normal text"});
        when(request.getParameterMap()).thenReturn(parameterMap);

        GXXssHttpServletRequestWrapper wrapper = new GXXssHttpServletRequestWrapper(request);

        assertArrayEquals(new String[]{"alert(1)", "normal text"}, wrapper.getParameterMap().get("param1"));
        assertArrayEquals(new String[]{"<script>alert(1)</script>", "normal text"}, parameterMap.get("param1"));
    }

    @Test
    @DisplayName("filters header values")
    void testHeaderXssFiltering() throws IOException {
        HttpServletRequest request = jsonRequest("");
        when(request.getHeader("User-Agent")).thenReturn("<script>alert('XSS')</script>");

        GXXssHttpServletRequestWrapper wrapper = new GXXssHttpServletRequestWrapper(request);

        assertEquals("alert('XSS')", wrapper.getHeader("User-Agent"));
    }

    @Test
    @DisplayName("filters JSON body once and serves cached bytes repeatedly")
    void testJsonBodyXssFiltering() throws IOException {
        String jsonWithXss = "{\"name\":\"<script>alert(1)</script>\",\"desc\":\"<img src=x onerror=alert(2)>\"}";
        String expectedJson = "{\"name\":\"alert(1)\",\"desc\":\"<img src=\\\"x\\\" />\"}";
        HttpServletRequest request = jsonRequest(jsonWithXss);

        GXXssHttpServletRequestWrapper wrapper = new GXXssHttpServletRequestWrapper(request);

        assertEquals(expectedJson, readInputStream(wrapper.getInputStream()));
        assertEquals(expectedJson, readInputStream(wrapper.getInputStream()));
        verify(request, times(1)).getInputStream();
    }

    @Test
    @DisplayName("limits JSON body even when Content-Length is absent")
    void testJsonBodyWithoutContentLengthIsLimitedWhileReading() throws IOException {
        byte[] oversizedBody = new byte[50 * 1024 * 1024 + 1];
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(MediaType.APPLICATION_JSON_VALUE);
        when(request.getContentLengthLong()).thenReturn(-1L);
        when(request.getInputStream()).thenReturn(createServletInputStream(oversizedBody));

        assertThrows(IllegalArgumentException.class, () -> new GXXssHttpServletRequestWrapper(request));
    }

    @Test
    @DisplayName("rejects JSON body when Content-Length exceeds the limit")
    void testJsonBodyContentLengthIsCheckedBeforeReading() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(MediaType.APPLICATION_JSON_VALUE);
        when(request.getContentLengthLong()).thenReturn(50 * 1024 * 1024 + 1L);

        assertThrows(IllegalArgumentException.class, () -> new GXXssHttpServletRequestWrapper(request));
    }

    @Test
    @DisplayName("does not consume non JSON body")
    void testNonJsonBodyNoFiltering() throws IOException {
        String bodyWithXss = "<script>alert(1)</script>";
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletInputStream originalStream = createServletInputStream(bodyWithXss);
        when(request.getHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(MediaType.TEXT_PLAIN_VALUE);
        when(request.getInputStream()).thenReturn(originalStream);

        GXXssHttpServletRequestWrapper wrapper = new GXXssHttpServletRequestWrapper(request);

        assertSame(originalStream, wrapper.getInputStream());
        verify(request, times(1)).getInputStream();
    }

    @Test
    @DisplayName("returns original request from wrapper")
    void testGetOrgRequest() throws IOException {
        HttpServletRequest request = jsonRequest("");
        GXXssHttpServletRequestWrapper wrapper = new GXXssHttpServletRequestWrapper(request);

        assertEquals(request, wrapper.getOrgRequest());
        assertEquals(request, GXXssHttpServletRequestWrapper.getOrgRequest(wrapper));
        assertEquals(request, GXXssHttpServletRequestWrapper.getOrgRequest(request));
    }

    private HttpServletRequest jsonRequest(String body) throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(MediaType.APPLICATION_JSON_VALUE);
        when(request.getContentLengthLong()).thenReturn((long) body.getBytes(StandardCharsets.UTF_8).length);
        when(request.getInputStream()).thenReturn(createServletInputStream(body));
        return request;
    }

    private ServletInputStream createServletInputStream(String content) {
        return createServletInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private ServletInputStream createServletInputStream(byte[] content) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content);

        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return inputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // Test stream is synchronous.
            }

            @Override
            public int read() {
                return inputStream.read();
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return inputStream.read(b, off, len);
            }
        };
    }

    private String readInputStream(ServletInputStream inputStream) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        byte[] buffer = new byte[1024];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            stringBuilder.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }

        return stringBuilder.toString();
    }
}
