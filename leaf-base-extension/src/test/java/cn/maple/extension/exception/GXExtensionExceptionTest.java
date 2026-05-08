package cn.maple.extension.exception;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GXExtensionExceptionTest {
    @Test
    void constructorWithDataKeepsCallerMessage() {
        GXExtensionException exception = new GXExtensionException("extension not found",
                HttpStatus.HTTP_NOT_FOUND, Dict.create());

        assertEquals("extension not found", exception.getMsg());
        assertEquals("extension not found", exception.getMessage());
    }
}
