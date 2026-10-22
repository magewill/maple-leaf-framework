package cn.maple.feign.aspect;

import cn.maple.core.framework.annotation.GXHttpInvokerAuthToken;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.constant.GXHttpInvokerConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXFeignAuthTokenException;
import cn.maple.feign.service.GXFeignService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GXFeignAuthTokenAspectTest {
    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void setUp() {
        GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
    }

    @AfterEach
    void tearDown() {
        GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
        if (context != null) {
            context.close();
            context = null;
        }
    }

    @Test
    void methodLevelAnnotationRejectsInvalidToken() {
        startContext(true, false, MethodLevelService.class);

        assertThatThrownBy(() -> context.getBean(MethodLevelService.class).call())
                .isInstanceOf(GXFeignAuthTokenException.class)
                .hasMessageContaining("MethodLevelService.call");
    }

    @Test
    void methodLevelAnnotationAllowsValidToken() {
        startContext(true, true, MethodLevelService.class);

        assertThat(context.getBean(MethodLevelService.class).call()).isEqualTo("method-level");
    }

    @Test
    void typeLevelAnnotationIsEnforced() {
        startContext(true, false, TypeLevelService.class);

        assertThatThrownBy(() -> context.getBean(TypeLevelService.class).call())
                .isInstanceOf(GXFeignAuthTokenException.class);
    }

    @Test
    void mismatchedInvokerValueIsSkipped() {
        startContext(false, false, OtherInvokerService.class);

        assertThat(context.getBean(OtherInvokerService.class).call()).isEqualTo("other-invoker");
    }

    @Test
    void missingFeignServiceBeanThrowsBusinessException() {
        startContext(false, true, MethodLevelService.class);

        assertThatThrownBy(() -> context.getBean(MethodLevelService.class).call())
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining(GXFeignService.class.getName());
    }

    @Test
    void interfaceLevelAnnotationIsNotInterceptedOnImplementation() {
        startContext(true, false, AnnotatedApiImpl.class);

        assertThatCode(() -> context.getBean(AnnotatedApiInterface.class).call())
                .doesNotThrowAnyException();
    }

    private void startContext(boolean withFeignService, boolean tokenValid, Class<?> beanType) {
        context = new AnnotationConfigApplicationContext();
        context.register(AspectProxyConfig.class);
        context.registerBean("gxFeignAuthTokenAspect", GXFeignAuthTokenAspect.class);
        context.registerBean(beanType);
        if (withFeignService) {
            context.registerBean("gxFeignService", GXFeignService.class, () -> new GXFeignService() {
                @Override
                public boolean checkTokenValidity() {
                    return tokenValid;
                }
            });
        }
        context.refresh();
        GXApplicationContextSingleton.INSTANCE.setApplicationContext(context);
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class AspectProxyConfig {
    }

    static class MethodLevelService {
        @GXHttpInvokerAuthToken
        public String call() {
            return "method-level";
        }
    }

    @GXHttpInvokerAuthToken
    static class TypeLevelService {
        public String call() {
            return "type-level";
        }
    }

    static class OtherInvokerService {
        @GXHttpInvokerAuthToken(GXHttpInvokerConstant.WEB_CLIENT_INVOKER)
        public String call() {
            return "other-invoker";
        }
    }

    @GXHttpInvokerAuthToken
    interface AnnotatedApiInterface {
        String call();
    }

    static class AnnotatedApiImpl implements AnnotatedApiInterface {
        @Override
        public String call() {
            return "interface-level";
        }
    }
}
