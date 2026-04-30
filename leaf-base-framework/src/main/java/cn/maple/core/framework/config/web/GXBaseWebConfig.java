package cn.maple.core.framework.config.web;

import cn.maple.core.framework.filter.GXBaseRequestLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GXBaseWebConfig {
    @Bean
    public GXBaseRequestLoggingFilter requestLoggingFilter() {
        return new GXBaseRequestLoggingFilter();
    }
}