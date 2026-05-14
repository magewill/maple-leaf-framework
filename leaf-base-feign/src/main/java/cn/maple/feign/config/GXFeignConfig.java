package cn.maple.feign.config;

import cn.maple.feign.aspect.GXFeignAuthTokenAspect;
import cn.maple.feign.codec.GXFeignCustomErrorDecoder;
import cn.maple.feign.interceptor.GXFeignRequestInterceptor;
import cn.maple.feign.service.GXFeignService;
import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FeignClientSpecification;
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
    @ConditionalOnProperty(prefix = "maple.feign.logger-level", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    @ConditionalOnProperty(prefix = "maple.feign.error-decoder", name = "enabled", havingValue = "true")
    public ErrorDecoder errorDecoder() {
        return new GXFeignCustomErrorDecoder();
    }

    @Bean(name = "default.mapleFeignFallback.FeignClientSpecification")
    @ConditionalOnMissingBean(name = "default.mapleFeignFallback.FeignClientSpecification")
    public FeignClientSpecification mapleFeignFallbackSpecification() {
        return new FeignClientSpecification(
                "default.mapleFeignFallback",
                "default",
                new Class<?>[]{GXFeignFallbackConfig.class}
        );
    }

    @Bean
    @ConditionalOnMissingBean(GXFeignRequestInterceptor.class)
    @ConditionalOnProperty(
            prefix = "maple.feign.request-interceptor",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public GXFeignRequestInterceptor requestInterceptor(ObjectProvider<@NonNull GXFeignService> feignServiceProvider) {
        return new GXFeignRequestInterceptor(feignServiceProvider);
    }

    @Bean
    @ConditionalOnMissingBean(GXFeignAuthTokenAspect.class)
    @ConditionalOnProperty(
            prefix = "maple.feign.auth-token-aspect",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public GXFeignAuthTokenAspect feignAuthTokenAspect() {
        return new GXFeignAuthTokenAspect();
    }
}
