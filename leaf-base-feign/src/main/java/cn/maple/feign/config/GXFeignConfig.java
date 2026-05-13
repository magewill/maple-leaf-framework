package cn.maple.feign.config;

import cn.maple.feign.codec.GXFeignCustomErrorDecoder;
import cn.maple.feign.interceptor.GXFeignRequestInterceptor;
import cn.maple.feign.service.GXFeignService;
import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for Feign defaults.
 *
 * <p>All beans can be overridden by user-defined beans.</p>
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
public class GXFeignConfig {
    /**
     * Uses BASIC by default to avoid logging request bodies and sensitive headers.
     */
    @Bean
    @ConditionalOnMissingBean(Logger.Level.class)
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    public ErrorDecoder errorDecoder() {
        return new GXFeignCustomErrorDecoder();
    }

    @Bean
    @ConditionalOnMissingBean(GXFeignRequestInterceptor.class)
    public GXFeignRequestInterceptor requestInterceptor(ObjectProvider<GXFeignService> feignServiceProvider) {
        return new GXFeignRequestInterceptor(feignServiceProvider);
    }
}
