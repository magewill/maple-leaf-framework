package cn.maple.core.framework.util;

import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.code.GXDefaultResultStatusCode;
import cn.maple.core.framework.exception.GXBusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GXResultUtilsTest {
    @Test
    void okBuildsDefaultAndCustomResult() {
        GXResultUtils<Object> defaultResult = GXResultUtils.ok();
        GXResultUtils<String> dataResult = GXResultUtils.ok("created", "payload");
        GXResultUtils<Object> enumResult = GXResultUtils.ok(GXDefaultResultStatusCode.OK);

        assertEquals(HttpStatus.HTTP_OK, defaultResult.getCode());
        assertEquals("success", defaultResult.getMsg());
        assertNull(defaultResult.getData());
        assertEquals(HttpStatus.HTTP_OK, dataResult.getCode());
        assertEquals("created", dataResult.getMsg());
        assertEquals("payload", dataResult.getData());
        assertEquals(GXDefaultResultStatusCode.OK.getMsg(), enumResult.getMsg());
    }

    @Test
    void errorBuildsDefaultThrowableAndBusinessResults() {
        GXResultUtils<Object> defaultResult = GXResultUtils.error();
        GXResultUtils<Object> throwableResult = GXResultUtils.error(499, new IllegalStateException("boom"));
        GXResultUtils<Object> businessResult = GXResultUtils.error(new GXBusinessException("bad", 488));

        assertEquals(HttpStatus.HTTP_INTERNAL_ERROR, defaultResult.getCode());
        assertEquals("未知异常，请联系管理员", defaultResult.getMsg());
        assertEquals(499, throwableResult.getCode());
        assertEquals("boom", throwableResult.getMsg());
        assertEquals(488, businessResult.getCode());
        assertEquals("bad", businessResult.getMsg());
    }

    @Test
    void nullInputsUseCompatibleFallbackResults() {
        GXResultUtils<String> okCode = GXResultUtils.ok((GXDefaultResultStatusCode) null, "payload");
        GXResultUtils<Object> errorCode = GXResultUtils.error((GXDefaultResultStatusCode) null);
        GXResultUtils<Object> throwable = GXResultUtils.error(500, (Throwable) null);
        GXResultUtils<Object> business = GXResultUtils.error((GXBusinessException) null);

        assertEquals(HttpStatus.HTTP_OK, okCode.getCode());
        assertEquals("payload", okCode.getData());
        assertEquals(HttpStatus.HTTP_INTERNAL_ERROR, errorCode.getCode());
        assertEquals("fail", throwable.getMsg());
        assertEquals(HttpStatus.HTTP_INTERNAL_ERROR, business.getCode());
    }
}
