package cn.maple.extension.exception;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXBusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class GXExtensionExceptionTest {
    @Test
    void constructorWithDataKeepsCallerMessage() {
        GXExtensionException exception = new GXExtensionException("extension not found",
                HttpStatus.HTTP_NOT_FOUND, Dict.create());

        assertEquals("extension not found", exception.getMsg());
        assertEquals("extension not found", exception.getMessage());
    }

    @Test
    void messageOnlyConstructorReportsNotFound() {
        GXExtensionException exception = new GXExtensionException("extension not found");

        assertEquals(HttpStatus.HTTP_NOT_FOUND, exception.getCode());
        assertEquals("extension not found", exception.getMessage());
        assertInstanceOf(GXBusinessException.class, exception);
    }

    @Test
    void messageAndCauseConstructorReportsNotFound() {
        RuntimeException cause = new RuntimeException("cause");

        GXExtensionException exception = new GXExtensionException("extension not found", cause);

        assertEquals(HttpStatus.HTTP_NOT_FOUND, exception.getCode());
        assertEquals("extension not found", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertInstanceOf(GXBusinessException.class, exception);
    }
}
