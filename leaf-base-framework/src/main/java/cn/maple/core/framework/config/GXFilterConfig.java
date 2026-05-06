package cn.maple.core.framework.config;

import cn.maple.core.framework.filter.GXXssFilter;
import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.web.filter.ServletRequestPathFilter;

@Configuration
@Slf4j
public class GXFilterConfig {
    @Value("${maple.framework.web.filter.request-context-filter.thread-context-inheritable:true}")
    private boolean threadContextInheritable;

    @Bean
    @ConditionalOnExpression("${maple.framework.web.filter.xss-filter.enabled:true}")
    public FilterRegistrationBean<GXXssFilter> xssFilterRegistration() {
        FilterRegistrationBean<GXXssFilter> registration = new FilterRegistrationBean<>();
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setFilter(new GXXssFilter());
        registration.addUrlPatterns("/*");
        registration.setName("xssFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setAsyncSupported(true);

        log.info("XSS filter configured for all request paths");
        return registration;
    }

    @Bean
    @ConditionalOnExpression("${maple.framework.web.filter.request-context-filter.enabled:false}")
    RequestContextFilter requestContextFilter() {
        RequestContextFilter filter = new RequestContextFilter();
        filter.setThreadContextInheritable(threadContextInheritable);
        log.info("RequestContextFilter configured: threadContextInheritable={}", threadContextInheritable);
        return filter;
    }

    @Bean
    @ConditionalOnExpression("${maple.framework.web.filter.servlet-request-path-filter.enabled:false}")
    ServletRequestPathFilter servletRequestPathFilter() {
        log.info("ServletRequestPathFilter configured");
        return new ServletRequestPathFilter();
    }

    @Bean
    @ConditionalOnExpression("${maple.framework.web.filter.commons-request-logging-filter.enabled:false}")
    CommonsRequestLoggingFilter commonsRequestLoggingFilter() {
        log.info("CommonsRequestLoggingFilter configured");
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludeHeaders(true);
        return filter;
    }
}
