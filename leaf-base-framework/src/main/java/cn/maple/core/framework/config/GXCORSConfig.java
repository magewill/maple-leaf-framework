package cn.maple.core.framework.config;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXCorsConfigException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@Slf4j
public class GXCORSConfig {
    @Value("${cors.allow.credentials:false}")
    private boolean allowCredentials;

    @Value("${cors.allow.origins:*}")
    private String allowOrigins;

    @Value("${cors.allow.methods:*}")
    private String allowMethods;

    @Value("${cors.allow.headers:*}")
    private String allowHeaders;

    @Value("${cors.max.age:3600}")
    private long maxAge;

    private CorsConfiguration buildConfig() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();

        if (allowCredentials && CharSequenceUtil.equals(allowOrigins, "*")) {
            String errorMsg = "安全错误: 当allowCredentials为true时，Access-Control-Allow-Origin不能设置为'*',否则cookie不会出现在http的请求头里!!";
            log.error(errorMsg);
            throw new GXCorsConfigException(errorMsg, HttpStatus.HTTP_INTERNAL_ERROR);
        }

        corsConfiguration.setAllowCredentials(allowCredentials);

        if (allowCredentials && CharSequenceUtil.equals(allowOrigins, "*")) {
            corsConfiguration.addAllowedOriginPattern("*");
        } else {
            corsConfiguration.setAllowedOrigins(Arrays.asList(allowOrigins.split(",")));
        }

        corsConfiguration.setAllowedMethods(Arrays.asList(allowMethods.split(",")));

        corsConfiguration.setAllowedHeaders(Arrays.asList(allowHeaders.split(",")));

        corsConfiguration.setMaxAge(maxAge);

        List<String> exposedHeaders = CollUtil.newArrayList(
                "Access-Control-Allow-Headers",
                "Access-Control-Expose-Headers",
                "Access-Control-Allow-Origin"
        );

        if (allowCredentials) {
            exposedHeaders.add("Access-Control-Allow-Credentials");
        }

        corsConfiguration.setExposedHeaders(exposedHeaders);
        return corsConfiguration;
    }

    @Bean
    public CorsFilter corsFilter() {
        log.info("初始化CORS过滤器，allowCredentials={}, allowOrigins={}", allowCredentials, allowOrigins);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", buildConfig());
        return new CorsFilter(source);
    }
}
