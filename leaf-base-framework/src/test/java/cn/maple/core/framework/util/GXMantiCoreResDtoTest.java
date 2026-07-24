package cn.maple.core.framework.util;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.inner.GXMantiCoreResDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXMantiCoreResDtoTest {

    @Test
    void successResponseRetainsStatusParsedDataAndRawBody() {
        JSON data = JSONUtil.parseObj("{\"id\":7}");

        GXMantiCoreResDto<JSON> response = GXMantiCoreResDto.success(201, data, "{\"id\":7}");

        assertTrue(response.isSuccess());
        assertEquals(201, response.getStatusCode());
        assertSame(data, response.getData());
        assertEquals("{\"id\":7}", response.getRawBody());
        assertNull(response.getErrorMessage());
    }

    @Test
    void failureResponseRetainsAvailableDiagnostics() {
        GXMantiCoreResDto<JSON> response =
                GXMantiCoreResDto.failure(400, null, "{\"error\":\"bad\"}", "HTTP 400");

        assertFalse(response.isSuccess());
        assertEquals(400, response.getStatusCode());
        assertNull(response.getData());
        assertEquals("{\"error\":\"bad\"}", response.getRawBody());
        assertEquals("HTTP 400", response.getErrorMessage());
    }
}
