package cn.maple.dubbo.zk.processor;

import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import org.apache.dubbo.config.spring.ServiceBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GXDubboRpcApiBeanPostProcessorTest {
    private final GXDubboRpcApiBeanPostProcessor processor = new GXDubboRpcApiBeanPostProcessor();

    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void setUp() {
        GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
            context = null;
        }
        GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
    }

    @Test
    void skipsWhenRefIsNull() {
        ServiceBean<Object> serviceBean = serviceBean(null);

        Object result = processor.postProcessAfterInitialization(serviceBean, "testService");

        assertSame(serviceBean, result);
    }

    @Test
    void skipsWhenServiceClassHasNoGenericType() {
        ServiceBean<Object> serviceBean = serviceBean(new Object());

        Object result = processor.postProcessAfterInitialization(serviceBean, "testService");

        assertSame(serviceBean, result);
    }

    @Test
    void skipsBindingWhenNoSpringBeanMatches() {
        TestRpcApi api = new TestRpcApi();
        ServiceBean<Object> serviceBean = serviceBean(api);

        Object result = processor.postProcessAfterInitialization(serviceBean, "testService");

        assertSame(serviceBean, result);
        assertEquals(0, api.bindCalls);
        assertNull(api.boundType);
    }

    @Test
    void bindsWhenSpringBeanMatches() {
        TestRpcApi api = new TestRpcApi();
        ServiceBean<Object> serviceBean = serviceBean(api);
        registerTestServiceBeans(1);

        Object result = processor.postProcessAfterInitialization(serviceBean, "testService");

        assertSame(serviceBean, result);
        assertEquals(1, api.bindCalls);
        assertEquals(TestService.class, api.boundType);
    }

    @Test
    void bindsOnceWhenMultipleCandidatesExist() {
        TestRpcApi api = new TestRpcApi();
        ServiceBean<Object> serviceBean = serviceBean(api);
        registerTestServiceBeans(2);

        Object result = processor.postProcessAfterInitialization(serviceBean, "testService");

        assertSame(serviceBean, result);
        assertEquals(1, api.bindCalls);
        assertEquals(TestService.class, api.boundType);
    }

    @Test
    void keepsStartupAliveWhenBindingIsNotEffective() {
        MismatchRpcApi api = new MismatchRpcApi();
        ServiceBean<Object> serviceBean = serviceBean(api);
        registerTestServiceBeans(1);

        Object result = processor.postProcessAfterInitialization(serviceBean, "testService");

        assertSame(serviceBean, result);
        assertEquals(1, api.bindCalls);
        assertEquals(TestService.class, api.boundType);
    }

    @SuppressWarnings("unchecked")
    private ServiceBean<Object> serviceBean(Object ref) {
        ServiceBean<Object> serviceBean = mock(ServiceBean.class);
        when(serviceBean.getRef()).thenReturn(ref);
        return serviceBean;
    }

    private void registerTestServiceBeans(int count) {
        context = new AnnotationConfigApplicationContext();
        for (int i = 0; i < count; i++) {
            String beanName = "testService" + i;
            context.registerBean(beanName, TestService.class, TestServiceImpl::new);
        }
        context.refresh();
        GXApplicationContextSingleton.INSTANCE.setApplicationContext(context);
    }

    interface RpcApi<T> {
    }

    interface TestService {
        void call();
    }

    static final class TestServiceImpl implements TestService {
        @Override
        public void call() {
        }
    }

    static class TestRpcApi implements RpcApi<TestService> {
        Class<?> boundType;
        int bindCalls;

        public void staticBindServeServiceClass(Class<?> serveServiceClass) {
            this.boundType = serveServiceClass;
            this.bindCalls++;
        }

        public Class<?> getServeServiceClass() {
            return boundType;
        }
    }

    static final class MismatchRpcApi extends TestRpcApi {
        @Override
        public Class<?> getServeServiceClass() {
            return String.class;
        }
    }
}
