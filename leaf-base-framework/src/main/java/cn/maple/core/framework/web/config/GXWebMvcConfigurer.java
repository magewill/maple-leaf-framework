package cn.maple.core.framework.web.config;

import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.core.framework.web.interceptor.GXAuthorizationInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

/**
 * Web MVC全局配置类
 * <p>
 * 该配置类实现了Spring MVC的WebMvcConfigurer接口，用于自定义框架的Web MVC配置。
 * 主要功能包括：
 * 1. 配置跨域资源共享(CORS)策略，允许跨域请求访问API
 * 2. 注册所有实现了GXAuthorizationInterceptor接口的授权拦截器
 */
@Configuration
@Slf4j
public class GXWebMvcConfigurer implements WebMvcConfigurer {
    /**
     * 配置跨域资源共享(CORS)策略
     * <p>
     * 该方法配置了全局的CORS策略，允许来自任何源的跨域请求访问API。
     * 支持常用的HTTP方法，并设置了预检请求的缓存时间。
     *
     * @param registry CORS注册表，用于配置CORS策略
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
                .addMapping("/**")              // 应用于所有路径
                .allowedOrigins("*")           // 允许所有来源
                .allowCredentials(false)       // 不允许发送凭证信息
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的HTTP方法
                .allowedHeaders("*")           // 允许所有请求头
                .maxAge(3600);                 // 预检请求的缓存时间(秒)
    }

    /**
     * 注册拦截器
     * <p>
     * 该方法从Spring容器中获取所有实现了GXAuthorizationInterceptor接口的Bean，
     * 并将它们注册到Spring MVC的拦截器链中。这些拦截器主要用于处理授权验证等功能。
     *
     * @param registry 拦截器注册表，用于注册拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 从Spring容器中获取所有实现了GXAuthorizationInterceptor接口的Bean
        Map<String, GXAuthorizationInterceptor> authorizationInterceptor = GXSpringContextUtils.getBeans(GXAuthorizationInterceptor.class);
        // 将所有授权拦截器注册到拦截器链中
        authorizationInterceptor.forEach((beanName, interceptor) -> registry.addInterceptor(interceptor));
    }
}
