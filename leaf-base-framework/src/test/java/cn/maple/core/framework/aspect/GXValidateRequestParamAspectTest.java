package cn.maple.core.framework.aspect;

import cn.maple.core.framework.exception.GXBeanValidateException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import jakarta.validation.constraints.NotBlank;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GXValidateRequestParamAspectTest {
    private final GXValidateRequestParamAspect aspect = new GXValidateRequestParamAspect();

    @AfterEach
    void tearDown() {
        aspect.closeValidatorFactory();
    }

    @Test
    void aroundRejectsInvalidGetMappingParameter() throws Throwable {
        ValidateTarget target = new ValidateTarget();
        Method method = ValidateTarget.class.getMethod("find", String.class);
        ProceedingJoinPoint point = point(target, method, new Object[]{""});

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(ValidateTarget.class)).thenReturn(target);

            GXBeanValidateException exception = assertThrows(GXBeanValidateException.class, () -> aspect.around(point));

            assertEquals(500, exception.getCode());
            verify(point, Mockito.never()).proceed(Mockito.any(Object[].class));
        }
    }

    @Test
    void aroundUsesJoinPointTargetWhenSpringBeanIsUnavailable() throws Throwable {
        ValidateTarget target = new ValidateTarget();
        Method method = ValidateTarget.class.getMethod("find", String.class);
        ProceedingJoinPoint point = point(target, method, new Object[]{"ok"});
        when(point.proceed(new Object[]{"ok"})).thenReturn("value");

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(ValidateTarget.class)).thenReturn(null);

            assertEquals("value", aspect.around(point));
        }
    }

    @Test
    void closeValidatorFactoryIsIdempotentForSingleLifecycle() {
        GXValidateRequestParamAspect localAspect = new GXValidateRequestParamAspect();

        localAspect.closeValidatorFactory();
    }

    private ProceedingJoinPoint point(Object target, Method method, Object[] args) {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(point.getTarget()).thenReturn(target);
        when(point.getArgs()).thenReturn(args);
        when(signature.getMethod()).thenReturn(method);
        return point;
    }

    static class ValidateTarget {
        @GetMapping("/find")
        public String find(@NotBlank String name) {
            return name;
        }
    }
}
