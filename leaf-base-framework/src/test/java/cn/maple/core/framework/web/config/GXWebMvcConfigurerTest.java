package cn.maple.core.framework.web.config;

import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.core.framework.web.interceptor.GXAuthorizationInterceptor;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GXWebMvcConfigurerTest {
    private final GXWebMvcConfigurer configurer = new GXWebMvcConfigurer();

    @Test
    void addInterceptorsRegistersUniqueInterceptorInstancesByBeanNameOrder() {
        GXAuthorizationInterceptor first = mock(GXAuthorizationInterceptor.class);
        GXAuthorizationInterceptor second = mock(GXAuthorizationInterceptor.class);
        Map<String, GXAuthorizationInterceptor> interceptors = new LinkedHashMap<>();
        interceptors.put("bSecond", second);
        interceptors.put("aFirst", first);
        interceptors.put("cAliasOfFirst", first);
        InterceptorRegistry registry = mock(InterceptorRegistry.class);

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBeans(GXAuthorizationInterceptor.class)).thenReturn(interceptors);

            configurer.addInterceptors(registry);

            var inOrder = Mockito.inOrder(registry);
            inOrder.verify(registry).addInterceptor(first);
            inOrder.verify(registry).addInterceptor(second);
            verify(registry, times(2)).addInterceptor(Mockito.any(GXAuthorizationInterceptor.class));
        }
    }

    @Test
    void addInterceptorsDoesNothingWhenNoInterceptorBeanExists() {
        InterceptorRegistry registry = mock(InterceptorRegistry.class);

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBeans(GXAuthorizationInterceptor.class)).thenReturn(Map.of());

            configurer.addInterceptors(registry);

            verify(registry, never()).addInterceptor(Mockito.any(GXAuthorizationInterceptor.class));
        }
    }

    @Test
    void addCorsMappingsRegistersDefaultCorsPolicy() {
        CorsRegistry registry = mock(CorsRegistry.class);
        CorsRegistration registration = mock(CorsRegistration.class);
        when(registry.addMapping("/**")).thenReturn(registration);
        when(registration.allowedOrigins("*")).thenReturn(registration);
        when(registration.allowCredentials(false)).thenReturn(registration);
        when(registration.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")).thenReturn(registration);
        when(registration.allowedHeaders("*")).thenReturn(registration);
        when(registration.maxAge(3600)).thenReturn(registration);

        configurer.addCorsMappings(registry);

        verify(registry).addMapping("/**");
        verify(registration).allowedOrigins("*");
        verify(registration).allowCredentials(false);
        verify(registration).allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
        verify(registration).allowedHeaders("*");
        verify(registration).maxAge(3600);
    }
}
