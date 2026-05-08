package cn.maple.core.datasource.util;

import cn.maple.core.datasource.dto.GXDataFilterInnerDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
}
