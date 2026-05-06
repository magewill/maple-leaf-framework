package cn.maple.core.framework.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GXStopWatchAspectTest {
    private final GXStopWatchAspect aspect = new GXStopWatchAspect();

    @Test
    void aroundReturnsBusinessResult() throws Throwable {
        StopWatchTarget target = new StopWatchTarget();
        Method method = StopWatchTarget.class.getMethod("call", String.class);
        ProceedingJoinPoint point = point(target, method, new Object[]{"alpha"});
        when(point.proceed()).thenReturn("ok");

        assertEquals("ok", aspect.around(point));
    }

    @Test
    void aroundPropagatesBusinessException() throws Throwable {
        StopWatchTarget target = new StopWatchTarget();
        Method method = StopWatchTarget.class.getMethod("call", String.class);
        ProceedingJoinPoint point = point(target, method, new Object[]{"alpha"});
        IOException failure = new IOException("failed");
        when(point.proceed()).thenThrow(failure);

        IOException exception = assertThrows(IOException.class, () -> aspect.around(point));

        assertSame(failure, exception);
    }

    @Test
    void aroundHandlesArgumentAndParameterCountMismatch() throws Throwable {
        StopWatchTarget target = new StopWatchTarget();
        Method method = StopWatchTarget.class.getMethod("call", String.class);
        ProceedingJoinPoint point = point(target, method, new Object[0]);
        when(point.proceed()).thenReturn("ok");

        assertEquals("ok", aspect.around(point));
    }

    private ProceedingJoinPoint point(Object target, Method method, Object[] args) {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getTarget()).thenReturn(target);
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(args);
        when(signature.getMethod()).thenReturn(method);
        return point;
    }

    static class StopWatchTarget {
        public String call(String name) {
            return name;
        }
    }
}
