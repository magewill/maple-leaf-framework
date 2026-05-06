package cn.maple.core.framework.aspect;

import cn.maple.core.framework.annotation.GXBusinessLog;
import cn.maple.core.framework.dto.inner.GXBusinessLogDto;
import cn.maple.core.framework.service.GXBusinessLogService;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GXBusinessLogAspectTest {
    private final GXBusinessLogAspect aspect = new GXBusinessLogAspect();

    @Test
    void aroundSavesBusinessLogAfterSuccessfulInvocation() throws Throwable {
        BusinessTarget target = new BusinessTarget();
        Method method = BusinessTarget.class.getMethod("create", String.class);
        ProceedingJoinPoint point = point(target, method, new Object[]{"alpha"});
        GXBusinessLogService businessLogService = mock(GXBusinessLogService.class);
        when(point.proceed()).thenReturn("ok");
        when(businessLogService.getUserName()).thenReturn("tester");

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class);
             MockedStatic<GXCurrentRequestContextUtils> requestContext = Mockito.mockStatic(GXCurrentRequestContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(GXBusinessLogService.class)).thenReturn(businessLogService);
            requestContext.when(GXCurrentRequestContextUtils::getClientIP).thenReturn("127.0.0.1");

            assertEquals("ok", aspect.around(point));
        }

        ArgumentCaptor<GXBusinessLogDto> captor = ArgumentCaptor.forClass(GXBusinessLogDto.class);
        verify(businessLogService).saveBusinessLog(captor.capture());
        GXBusinessLogDto dto = captor.getValue();
        assertEquals("create", dto.getBusinessName());
        assertEquals("create item", dto.getBusinessDescription());
        assertEquals("tester", dto.getUserName());
        assertEquals("127.0.0.1", dto.getIp());
        assertEquals(BusinessTarget.class.getName() + ".create()", dto.getMethodName());
    }

    @Test
    void aroundPropagatesCheckedExceptionWithoutWrapping() throws Throwable {
        BusinessTarget target = new BusinessTarget();
        Method method = BusinessTarget.class.getMethod("create", String.class);
        ProceedingJoinPoint point = point(target, method, new Object[]{"alpha"});
        IOException failure = new IOException("failed");
        when(point.proceed()).thenThrow(failure);

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class);
             MockedStatic<GXCurrentRequestContextUtils> requestContext = Mockito.mockStatic(GXCurrentRequestContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(GXBusinessLogService.class)).thenReturn(null);
            requestContext.when(GXCurrentRequestContextUtils::getClientIP).thenReturn("127.0.0.1");

            IOException exception = assertThrows(IOException.class, () -> aspect.around(point));

            assertSame(failure, exception);
        }
    }

    @Test
    void classLevelBusinessLogAnnotationProvidesDefaults() throws Throwable {
        ClassLevelTarget target = new ClassLevelTarget();
        Method method = ClassLevelTarget.class.getMethod("run");
        ProceedingJoinPoint point = point(target, method, new Object[0]);
        GXBusinessLogService businessLogService = mock(GXBusinessLogService.class);
        when(point.proceed()).thenReturn("done");

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class);
             MockedStatic<GXCurrentRequestContextUtils> requestContext = Mockito.mockStatic(GXCurrentRequestContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(GXBusinessLogService.class)).thenReturn(businessLogService);
            requestContext.when(GXCurrentRequestContextUtils::getClientIP).thenReturn("127.0.0.1");

            assertEquals("done", aspect.around(point));
        }

        ArgumentCaptor<GXBusinessLogDto> captor = ArgumentCaptor.forClass(GXBusinessLogDto.class);
        verify(businessLogService).saveBusinessLog(captor.capture());
        assertEquals("class", captor.getValue().getBusinessName());
        assertEquals("class level", captor.getValue().getBusinessDescription());
    }

    private ProceedingJoinPoint point(Object target, Method method, Object[] args) {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getTarget()).thenReturn(target);
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(args);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getName()).thenReturn(method.getName());
        return point;
    }

    static class BusinessTarget {
        @GXBusinessLog(name = "create", description = "create item")
        public String create(String name) {
            return name;
        }
    }

    @GXBusinessLog(name = "class", description = "class level")
    static class ClassLevelTarget {
        public String run() {
            return "done";
        }
    }
}
