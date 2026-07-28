package cn.maple.core.framework.util;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.inner.GXMantiCoreResDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GXMantiCoreResDtoTest {

    @Test
    void successResponseRetainsStatusParsedDataAndRawBody() {
        JSON data = JSONUtil.parseObj("{\"id\":7}");

        GXMantiCoreResDto<JSON> response = GXMantiCoreResDto.success(201, data);

        assertTrue(response.isSuccess());
        assertEquals(201, response.getStatusCode());
        assertSame(data, response.getData());
        assertNull(response.getErrorMessage());
    }

    @Test
    void failureResponseRetainsAvailableDiagnostics() {
        GXMantiCoreResDto<JSON> response =
                GXMantiCoreResDto.failure(400, null, "{\"error\":\"bad\"}");

        assertFalse(response.isSuccess());
        assertEquals(400, response.getStatusCode());
        assertNull(response.getData());
        assertEquals("HTTP 400", response.getErrorMessage());
    }
}
