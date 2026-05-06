package cn.maple.core.framework.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

public class GXXssFilter implements Filter {
    @Override
    public void init(FilterConfig config) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }
        if (request instanceof GXXssHttpServletRequestWrapper) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new GXXssHttpServletRequestWrapper(httpServletRequest), response);
    }

    @Override
    public void destroy() {
    }
}
