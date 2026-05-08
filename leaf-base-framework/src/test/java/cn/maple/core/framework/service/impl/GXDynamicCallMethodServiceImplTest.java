package cn.maple.core.framework.service.impl;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXDynamicCallMethodServiceImplTest {
    private final GXDynamicCallMethodServiceImpl service = new GXDynamicCallMethodServiceImpl();

    @Test
    void callByClassNameUsesSpringBeanAndSupportsPrimitiveParameters() {
        TestTarget target = new TestTarget();

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(TestTarget.class)).thenReturn(target);

            assertEquals(3, service.call(TestTarget.class.getName(), "add", 1, 2));
        }
    }

    @Test
    void callTargetSupportsNullParameterAndConcurrentAccess() throws Exception {
        TestTarget target = new TestTarget();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            tasks.add(() -> service.call(target, "echo", (Object) null));
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Object>> futures = executor.invokeAll(tasks);
            for (Future<Object> future : futures) {
                assertEquals("null-value", future.get());
            }
        }
    }

    @Test
    void callReturnsNullWhenInputIsInvalidOrMethodMissing() {
        TestTarget target = new TestTarget();

        assertNull(service.call((Object) null, "echo"));
        assertNull(service.call(target, ""));
        assertNull(service.call(target, "missingMethod"));
        assertNull(service.call("", "echo"));
    }

    @Test
    void callRethrowsBusinessExceptionFromTargetMethod() {
        TestTarget target = new TestTarget();

        assertThrows(GXBusinessException.class, () -> service.call(target, "fail"));
    }

    static class TestTarget {
        public int add(int left, int right) {
            return left + right;
        }

        public String echo(String value) {
            return value == null ? "null-value" : value;
        }

        public void fail() {
            throw new GXBusinessException("failed");
        }
    }
}
