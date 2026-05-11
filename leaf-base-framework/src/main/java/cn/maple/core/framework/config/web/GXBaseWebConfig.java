package cn.maple.core.framework.config.web;

import cn.maple.core.framework.filter.GXBaseRequestLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

@Configuration
public class GXBaseWebConfig {
    @Bean
    public GXBaseRequestLoggingFilter requestLoggingFilter() {
        return new GXBaseRequestLoggingFilter();
    }

    @Bean
    public FilterRegistrationBean<GXBaseRequestLoggingFilter> requestLoggingFilterRegistration(GXBaseRequestLoggingFilter requestLoggingFilter) {
        FilterRegistrationBean<GXBaseRequestLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(requestLoggingFilter);
        registration.setName("requestLoggingFilter");
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        registration.setAsyncSupported(true);
        return registration;
    }
}
