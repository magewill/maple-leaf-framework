package cn.maple.core.framework.config;

import cn.maple.core.framework.filter.GXXssFilter;
import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.web.filter.ServletRequestPathFilter;

@Configuration
@Slf4j
public class GXFilterConfig {
    @Bean
    @ConditionalOnExpression("${maple.framework.web.filter.xss-filter.enabled:true}")
    public FilterRegistrationBean<GXXssFilter> xssFilterRegistration() {
        FilterRegistrationBean<GXXssFilter> registration = new FilterRegistrationBean<>();
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setFilter(new GXXssFilter());
        registration.addUrlPatterns("/*");
        registration.setName("xssFilter");
        registration.setOrder(Integer.MAX_VALUE);

        log.info("XSS防护过滤器已配置，应用于所有请求路径");
        return registration;
    }

    @Bean
    @ConditionalOnExpression("${maple.framework.web.filter.request-context-filter.enabled:false}")
    RequestContextFilter requestContextFilter() {
        log.info("RequestContextFilter已配置，子线程也可以获取上下文对象");
        RequestContextFilter filter = new RequestContextFilter();
        filter.setThreadContextInheritable(true);
        return filter;
    }

    @Bean
    @ConditionalOnExpression("${maple.framework.web.filter.servlet-request-path-filter.enabled:false}")
    ServletRequestPathFilter servletRequestPathFilter() {
        log.info("ServletRequestPathFilter已配置，子线程也可以获取请求路径");
        ServletRequestPathFilter filter = new ServletRequestPathFilter();
        return filter;
    }

    @Bean
    @ConditionalOnExpression("${maple.framework.web.filter.commons-request-logging-filter.enabled:false}")
    CommonsRequestLoggingFilter commonsRequestLoggingFilter() {
        log.info("CommonsRequestLoggingFilter已配置，请求日志会被记录");
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludeHeaders(true);
        return filter;
    }
}
