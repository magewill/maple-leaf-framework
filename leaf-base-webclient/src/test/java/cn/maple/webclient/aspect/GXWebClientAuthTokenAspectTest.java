package cn.maple.webclient.aspect;

import cn.maple.core.framework.annotation.GXHttpInvokerAuthToken;
import cn.maple.core.framework.config.aware.GXApplicationContextAware;
import cn.maple.core.framework.constant.GXHttpInvokerConstant;
import cn.maple.core.framework.exception.GXWebClientAuthTokenException;
import cn.maple.webclient.service.GXWebClientService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GXWebClientAuthTokenAspectTest {
    @Test
    void validatesMethodLevelWebClientToken() {
        contextRunner(true).run(context -> {
            ProtectedService service = context.getBean(ProtectedService.class);

            assertThatCode(service::methodLevelWebClient).doesNotThrowAnyException();
            assertThat(service.publicCall()).isEqualTo("public");
        });
    }

    @Test
    void rejectsInvalidMethodLevelWebClientToken() {
        contextRunner(false).run(context -> {
            ProtectedService service = context.getBean(ProtectedService.class);

            assertThatThrownBy(service::methodLevelWebClient)
                    .isInstanceOf(GXWebClientAuthTokenException.class)
                    .hasMessageContaining("WebClient auth token validation failed");
        });
    }

    @Test
    void skipsNonWebClientAnnotationValue() {
        contextRunner(false).run(context -> {
            ProtectedService service = context.getBean(ProtectedService.class);

            assertThatCode(service::methodLevelFeign).doesNotThrowAnyException();
        });
    }

    @Test
    void validatesClassLevelWebClientToken() {
        contextRunner(true).run(context -> {
            ClassLevelProtectedService service = context.getBean(ClassLevelProtectedService.class);

            assertThatCode(service::classLevelCall).doesNotThrowAnyException();
        });
    }

    private ApplicationContextRunner contextRunner(boolean tokenValid) {
        return new ApplicationContextRunner()
                .withUserConfiguration(AopTestConfig.class)
                .withBean(GXWebClientService.class, () -> new GXWebClientService() {
                    @Override
                    public boolean checkTokenValidity() {
                        return tokenValid;
                    }
                });
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class AopTestConfig {
        @Bean
        GXApplicationContextAware gxApplicationContextAware() {
            return new GXApplicationContextAware();
        }

        @Bean
        GXWebClientAuthTokenAspect gxWebClientAuthTokenAspect() {
            return new GXWebClientAuthTokenAspect();
        }

        @Bean
        ProtectedService protectedService() {
            return new ProtectedService();
        }

        @Bean
        ClassLevelProtectedService classLevelProtectedService() {
            return new ClassLevelProtectedService();
        }
    }

    static class ProtectedService {
        @GXHttpInvokerAuthToken(GXHttpInvokerConstant.WEB_CLIENT_INVOKER)
        public String methodLevelWebClient() {
            return "webClient";
        }

        @GXHttpInvokerAuthToken
        public String methodLevelFeign() {
            return "feign";
        }

        public String publicCall() {
            return "public";
        }
    }

    @GXHttpInvokerAuthToken(GXHttpInvokerConstant.WEB_CLIENT_INVOKER)
    static class ClassLevelProtectedService {
        public String classLevelCall() {
            return "class";
        }
    }
}
