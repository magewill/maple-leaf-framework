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

/**
 * 跨域资源共享(CORS)配置类
 * <p>
 * 该配置类提供了Web应用的跨域资源共享(CORS)支持，允许从不同源的客户端安全地访问API。
 * 通过配置允许的源、HTTP方法、请求头以及是否允许携带凭证等参数，实现细粒度的跨域访问控制。
 * <p>
 * 安全说明：
 * 1. 当allowCredentials设置为true时，不允许将allowOrigins设置为"*"，这符合CORS安全规范
 * 2. 暴露必要的响应头，确保客户端能够正确处理跨域请求
 * 3. 所有配置参数都可通过外部属性文件进行定制，提高灵活性和安全性
 * <p>
 * 使用示例：
 * <pre>
 * # 在application.yml中配置
 * cors:
 *   allow:
 *     credentials: true
 *     origins: https://example.com,https://api.example.com
 *     methods: GET,POST,PUT,DELETE
 *     headers: Content-Type,Authorization
 * </pre>
 *
 * @author britton chen <britton@126.com>
 */
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

    /**
     * 构建CORS配置
     * <p>
     * 该方法创建并配置CorsConfiguration实例，设置允许的源、方法、请求头等CORS参数。
     * 特别注意：当allowCredentials为true时，不允许将allowOrigins设置为"*"，
     * 这是因为浏览器的同源策略安全限制。
     * <p>
     * 安全说明：
     * 1. 当allowCredentials为true时，必须明确指定允许的源，不能使用通配符
     * 2. 设置适当的maxAge减少预检请求频率，提高性能
     * 3. 只暴露必要的响应头，遵循最小权限原则
     *
     * @return 配置好的CorsConfiguration实例
     * @throws GXCorsConfigException 当配置违反CORS安全规范时抛出异常
     */
    private CorsConfiguration buildConfig() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        
        // 安全检查：当允许凭证时，不能使用通配符源
        if (allowCredentials && CharSequenceUtil.equals(allowOrigins, "*")) {
            String errorMsg = "安全错误: 当allowCredentials为true时，Access-Control-Allow-Origin不能设置为'*',否则cookie不会出现在http的请求头里!!";
            log.error(errorMsg);
            throw new GXCorsConfigException(errorMsg, HttpStatus.HTTP_INTERNAL_ERROR);
        }
        
        // 设置是否允许凭证
        corsConfiguration.setAllowCredentials(allowCredentials);
        
        // 处理允许的源
        if (allowCredentials && CharSequenceUtil.equals(allowOrigins, "*")) {
            // 使用allowedOriginPatterns替代allowedOrigins，以支持通配符
            corsConfiguration.addAllowedOriginPattern("*");
        } else {
            corsConfiguration.setAllowedOrigins(Arrays.asList(allowOrigins.split(",")));
        }
        
        // 设置允许的HTTP方法
        corsConfiguration.setAllowedMethods(Arrays.asList(allowMethods.split(",")));
        
        // 设置允许的请求头
        corsConfiguration.setAllowedHeaders(Arrays.asList(allowHeaders.split(",")));
        
        // 设置预检请求的有效期，减少预检请求频率
        corsConfiguration.setMaxAge(maxAge);
        
        // 设置暴露的响应头
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

    /**
     * 创建CORS过滤器Bean
     * <p>
     * 该方法创建并配置CorsFilter实例，应用于所有URL路径。
     * CorsFilter会拦截所有HTTP请求，并根据配置添加适当的CORS响应头。
     * <p>
     * 线程安全说明：
     * - CorsFilter是线程安全的，可以在多线程环境中共享使用
     * - 所有配置在应用启动时完成，运行时不会改变
     *
     * @return 配置好的CorsFilter实例
     */
    @Bean
    public CorsFilter corsFilter() {
        log.info("初始化CORS过滤器，allowCredentials={}, allowOrigins={}", allowCredentials, allowOrigins);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", buildConfig());
        return new CorsFilter(source);
    }
}
