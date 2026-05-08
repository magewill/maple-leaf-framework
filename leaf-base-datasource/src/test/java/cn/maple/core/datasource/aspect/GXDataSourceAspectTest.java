package cn.maple.core.datasource.aspect;

import cn.maple.core.datasource.annotation.GXDataSource;
import cn.maple.core.datasource.config.GXDynamicContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GXDataSourceAspectTest {
    private final GXDataSourceAspect aspect = new GXDataSourceAspect();

    @AfterEach
    void clearContext() {
        GXDynamicContextHolder.clear();
    }

    @Test
    void switchesDatasourceFromInterfaceMethodAnnotationAndRestoresPreviousContext() throws Throwable {
        GXDynamicContextHolder.push("outer");
        InterfaceAnnotatedService target = new InterfaceAnnotatedServiceImpl();
        Method method = InterfaceAnnotatedService.class.getMethod("query");
        ProceedingJoinPoint point = mockJoinPoint(target, method);

        Mockito.when(point.proceed()).thenAnswer(invocation -> {
            assertEquals("interface_ds", GXDynamicContextHolder.peek());
            return "ok";
        });

        Object result = aspect.around(point);

        assertEquals("ok", result);
        assertEquals("outer", GXDynamicContextHolder.peek());
    }

    @Test
    void switchesDatasourceFromClassAnnotationAndClearsContextAfterExecution() throws Throwable {
        ClassAnnotatedService target = new ClassAnnotatedService();
        Method method = ClassAnnotatedService.class.getMethod("query");
        ProceedingJoinPoint point = mockJoinPoint(target, method);

        Mockito.when(point.proceed()).thenAnswer(invocation -> {
            assertEquals("class_ds", GXDynamicContextHolder.peek());
            return "ok";
        });

        Object result = aspect.around(point);

        assertEquals("ok", result);
        assertNull(GXDynamicContextHolder.peek());
    }

    private ProceedingJoinPoint mockJoinPoint(Object target, Method method) {
        ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        Mockito.when(point.getTarget()).thenReturn(target);
        Mockito.when(point.getSignature()).thenReturn(signature);
        Mockito.when(signature.getMethod()).thenReturn(method);
        return point;
    }

    private interface InterfaceAnnotatedService {
        @GXDataSource("interface_ds")
        String query();
    }

    private static class InterfaceAnnotatedServiceImpl implements InterfaceAnnotatedService {
        @Override
        public String query() {
            return "ok";
        }
    }

    @GXDataSource("class_ds")
    private static class ClassAnnotatedService {
        public String query() {
            return "ok";
        }
    }
}
