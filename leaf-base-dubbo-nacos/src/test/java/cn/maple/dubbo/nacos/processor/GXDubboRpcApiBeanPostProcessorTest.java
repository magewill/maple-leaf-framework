package cn.maple.dubbo.nacos.processor;

import cn.maple.core.framework.util.GXSpringContextUtils;
import org.apache.dubbo.config.spring.ServiceBean;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GXDubboRpcApiBeanPostProcessorTest {
    private final GXDubboRpcApiBeanPostProcessor processor = new GXDubboRpcApiBeanPostProcessor();

    @Test
    void bindsServiceBeanRefToItsGenericServiceClass() {
        TestRpcApi api = new TestRpcApi();
        ServiceBean<TestRpcApi> serviceBean = serviceBean(api);
        ApplicationContext applicationContext = applicationContextWithBeanNames("testService");

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(GXSpringContextUtils::getApplicationContext).thenReturn(applicationContext);

            Object result = processor.postProcessAfterInitialization(serviceBean, "testServiceBean");

            assertSame(serviceBean, result);
            assertEquals(TestService.class, api.getBoundServiceClass());
            verify(applicationContext, never()).getBean(TestService.class);
        }
    }

    @Test
    void skipsBindingWhenTargetSpringBeanIsMissing() {
        TestRpcApi api = new TestRpcApi();
        ServiceBean<TestRpcApi> serviceBean = serviceBean(api);
        ApplicationContext applicationContext = applicationContextWithBeanNames();

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(GXSpringContextUtils::getApplicationContext).thenReturn(applicationContext);

            Object result = processor.postProcessAfterInitialization(serviceBean, "testServiceBean");

            assertSame(serviceBean, result);
            assertNull(api.getBoundServiceClass());
        }
    }

    @Test
    void bindsWhenTargetServiceHasMultipleBeanCandidates() {
        TestRpcApi api = new TestRpcApi();
        ServiceBean<TestRpcApi> serviceBean = serviceBean(api);
        ApplicationContext applicationContext = applicationContextWithBeanNames("testServiceOne", "testServiceTwo");

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(GXSpringContextUtils::getApplicationContext).thenReturn(applicationContext);

            Object result = processor.postProcessAfterInitialization(serviceBean, "testServiceBean");

            assertSame(serviceBean, result);
            assertEquals(TestService.class, api.getBoundServiceClass());
        }
    }

    @Test
    void skipsBindingWhenRefIsNull() {
        ServiceBean<TestRpcApi> serviceBean = serviceBean(null);

        Object result = processor.postProcessAfterInitialization(serviceBean, "testServiceBean");

        assertSame(serviceBean, result);
    }

    private ServiceBean<TestRpcApi> serviceBean(TestRpcApi api) {
        ServiceBean<TestRpcApi> serviceBean = mock(ServiceBean.class);
        when(serviceBean.getRef()).thenReturn(api);
        return serviceBean;
    }

    private ApplicationContext applicationContextWithBeanNames(String... beanNames) {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBeanNamesForType(TestService.class, false, false)).thenReturn(beanNames);
        return applicationContext;
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
    }

    static class TestService {
    }
}
