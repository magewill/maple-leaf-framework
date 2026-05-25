package cn.maple.core.datasource.util;

import cn.maple.core.datasource.annotation.GXDataFilter;
import cn.maple.core.datasource.dto.GXDataFilterContext;
import cn.maple.core.datasource.dto.GXDataFilterInnerDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GXDataFilterThreadLocalUtilsTest {
    @AfterEach
    void tearDown() {
        GXDataFilterThreadLocalUtils.cleanDataFilterInnerDto();
    }

    @Test
    void getAndSetUseSnapshots() {
        GXDataFilterInnerDto source = new GXDataFilterInnerDto("tenant_id = 1");
        GXDataFilterThreadLocalUtils.setDataFilterInnerDto(source);

        source.setSqlFilter("tenant_id = 2");
        GXDataFilterInnerDto snapshot = GXDataFilterThreadLocalUtils.getDataFilterInnerDto();
        snapshot.setSqlFilter("tenant_id = 3");

        assertEquals("tenant_id = 1", GXDataFilterThreadLocalUtils.getDataFilterInnerDto().getSqlFilter());
    }

    @Test
    void wrappedRunnableRestoresPreviousContext() {
        GXDataFilterThreadLocalUtils.setDataFilterInnerDto(new GXDataFilterInnerDto("outer_filter"));
        Runnable wrapped = GXDataFilterThreadLocalUtils.wrap(() -> {
            assertEquals("outer_filter", GXDataFilterThreadLocalUtils.getDataFilterInnerDto().getSqlFilter());
            GXDataFilterThreadLocalUtils.setDataFilterInnerDto(new GXDataFilterInnerDto("inner_filter"));
        });

        GXDataFilterThreadLocalUtils.setDataFilterInnerDto(new GXDataFilterInnerDto("worker_filter"));
        wrapped.run();

        assertEquals("worker_filter", GXDataFilterThreadLocalUtils.getDataFilterInnerDto().getSqlFilter());
    }

    @Test
    void wrappedCallableRestoresEmptyContext() throws Exception {
        GXDataFilterThreadLocalUtils.setDataFilterInnerDto(new GXDataFilterInnerDto("captured_filter"));
        Callable<String> wrapped = GXDataFilterThreadLocalUtils.wrap(() -> {
            assertEquals("captured_filter", GXDataFilterThreadLocalUtils.getDataFilterInnerDto().getSqlFilter());
            GXDataFilterThreadLocalUtils.cleanDataFilterInnerDto();
            return "ok";
        });

        GXDataFilterThreadLocalUtils.cleanDataFilterInnerDto();

        assertEquals("ok", wrapped.call());
        assertNull(GXDataFilterThreadLocalUtils.getDataFilterInnerDto());
    }

    @Test
    void contextMethodsPreserveFullContextSnapshots() throws Exception {
        GXDataFilter annotation = getAnnotation();
        GXDataFilterContext source = new GXDataFilterContext("tenant_id = 1", annotation, "testMethod", false);
        GXDataFilterThreadLocalUtils.setDataFilterContext(source);

        source.setMethodName("mutated");
        GXDataFilterContext snapshot = GXDataFilterThreadLocalUtils.getDataFilterContext();
        snapshot.setIgnored(true);

        GXDataFilterContext actual = GXDataFilterThreadLocalUtils.getDataFilterContext();
        assertEquals("testMethod", actual.getMethodName());
        assertEquals(annotation, actual.getDataFilter());
        assertEquals(false, actual.isIgnored());
    }

    @GXDataFilter
    void dataFilterMethod() {
    }

    private GXDataFilter getAnnotation() throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("dataFilterMethod");
        return method.getAnnotation(GXDataFilter.class);
    }
}
