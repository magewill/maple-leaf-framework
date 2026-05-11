package cn.maple.core.framework.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GXXssFilterTest {
    private final GXXssFilter filter = new GXXssFilter();

    @Test
    void doFilterWrapsHttpRequest() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(MediaType.TEXT_PLAIN_VALUE);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(GXXssHttpServletRequestWrapper.class), same(response));
    }

    @Test
    void doFilterPassesThroughNonHttpRequest() throws Exception {
        ServletRequest request = mock(ServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(same(request), same(response));
    }

    @Test
    void doFilterDoesNotWrapTwice() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(MediaType.TEXT_PLAIN_VALUE);
        GXXssHttpServletRequestWrapper wrapper = new GXXssHttpServletRequestWrapper(request);

        filter.doFilter(wrapper, response, chain);

        verify(chain).doFilter(same(wrapper), same(response));
        assertSame(request, wrapper.getOrgRequest());
        assertInstanceOf(GXXssHttpServletRequestWrapper.class, wrapper);
    }

    @Test
    void doFilterReturns413ForOversizedJsonBody() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(MediaType.APPLICATION_JSON_VALUE);
        when(request.getContentLengthLong()).thenReturn(50 * 1024 * 1024 + 1L);

        filter.doFilter(request, response, chain);

        verify(response).sendError(eq(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE), any(String.class));
    }
}
