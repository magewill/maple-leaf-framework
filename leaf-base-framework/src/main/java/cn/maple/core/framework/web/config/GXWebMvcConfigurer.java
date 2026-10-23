package cn.maple.core.framework.web.config;

import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.core.framework.web.interceptor.GXAuthorizationInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.*;

@Configuration
@EnableSpringDataWebSupport
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
                .sorted(Comparator
                        .<Map.Entry<String, GXAuthorizationInterceptor>>comparingInt(
                                entry -> resolveOrder(entry.getValue()))
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> {
                    GXAuthorizationInterceptor interceptor = entry.getValue();
                    if (interceptor != null && registeredInterceptors.add(interceptor)) {
                        registry.addInterceptor(interceptor);
                    }
                });
    }

    /**
     * Resolve the order of one interceptor.
     *
     * <p>{@code @Order} and the {@link Ordered} contract are both honoured; interceptors without
     * either fall back to {@link Ordered#LOWEST_PRECEDENCE}, which keeps the historical bean name
     * order as the tie breaker instead of throwing on a {@code null} order.</p>
     *
     * @param interceptor interceptor instance, never {@code null}
     * @return order value, never {@code null}
     */
    private static int resolveOrder(@Nullable GXAuthorizationInterceptor interceptor) {
        if (interceptor == null) {
            return Ordered.LOWEST_PRECEDENCE;
        }
        if (interceptor instanceof Ordered ordered) {
            return ordered.getOrder();
        }
        Integer annotatedOrder = OrderUtils.getOrder(interceptor.getClass());
        return annotatedOrder != null ? annotatedOrder : Ordered.LOWEST_PRECEDENCE;
    }
}
