package cn.maple.core.framework.web.config;

import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.core.framework.web.interceptor.GXAuthorizationInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

@Configuration
@Slf4j
public class GXWebMvcConfigurer implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
                .addMapping("/**")
                .allowedOrigins("*")
                .allowCredentials(false)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        Map<String, GXAuthorizationInterceptor> authorizationInterceptors =
                GXSpringContextUtils.getBeans(GXAuthorizationInterceptor.class);
        if (authorizationInterceptors.isEmpty()) {
            log.debug("No GXAuthorizationInterceptor beans found");
            return;
        }

        Set<GXAuthorizationInterceptor> registeredInterceptors =
                Collections.newSetFromMap(new IdentityHashMap<>());
        authorizationInterceptors.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> {
                    GXAuthorizationInterceptor interceptor = entry.getValue();
                    if (interceptor != null && registeredInterceptors.add(interceptor)) {
                        registry.addInterceptor(interceptor);
                    }
                });
    }
}
