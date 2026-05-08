package cn.maple.dubbo.nacos.processor;

import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import org.apache.dubbo.config.spring.ServiceBean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GXDubboRpcApiBeanPostProcessorSpringBootTest {
    private static final TestRpcApi API = new TestRpcApi();

    @Test
    void beanPostProcessorBindsServiceBeanDuringSpringBootStartup() {
        API.reset();
        SpringApplication application = new SpringApplicationBuilder(TestApp.class)
                .web(WebApplicationType.NONE)
                .initializers(new ContextInitializer())
                .properties("spring.cloud.nacos.config.import-check.enabled=false")
                .build();

        try (ConfigurableApplicationContext ignored = application.run()) {
            assertEquals(TestService.class, API.getBoundServiceClass());
        } finally {
            GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
            API.reset();
        }
    }

    static class ContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(applicationContext);
        }
    }

    @SpringBootConfiguration
    @Import(GXDubboRpcApiBeanPostProcessor.class)
    static class TestApp {
        @Bean
        ServiceBean<TestRpcApi> testServiceBean() {
            ServiceBean<TestRpcApi> serviceBean = mock(ServiceBean.class);
            when(serviceBean.getRef()).thenReturn(API);
            return serviceBean;
        }

        @Bean
        TestService testService() {
            return new TestService();
        }
    }

    static class TestRpcApi extends BaseRpcApi<TestService> {
    }

    static class BaseRpcApi<T> {
        private Class<?> boundServiceClass;

        public void staticBindServeServiceClass(Class<?> serviceClass) {
            this.boundServiceClass = serviceClass;
        }

        public Class<?> getBoundServiceClass() {
            return boundServiceClass;
        }

        public void reset() {
            boundServiceClass = null;
        }
    }

    static class TestService {
    }
}
