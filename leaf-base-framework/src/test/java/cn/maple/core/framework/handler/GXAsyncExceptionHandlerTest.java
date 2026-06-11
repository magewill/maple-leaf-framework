package cn.maple.core.framework.handler;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXBusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GXAsyncExceptionHandlerTest {
    @Test
    void appendParamsIncludesBusinessExceptionData() throws Exception {
        ObjectProvider<ThreadPoolTaskExecutor> provider = mock(ObjectProvider.class);
        GXAsyncExceptionHandler handler = new GXAsyncExceptionHandler(provider);
        Dict data = Dict.create().set("name", "britton").set("age", 18);
        GXBusinessException exception = new GXBusinessException("async failed", 500, data);
        StringBuilder errorMsg = new StringBuilder();
        Method appendParams = assertDoesNotThrow(() -> GXAsyncExceptionHandler.class
                .getDeclaredMethod("appendParams", StringBuilder.class, Throwable.class, Object[].class));
        appendParams.setAccessible(true);

        appendParams.invoke(handler, errorMsg, exception, new Object[0]);

        String logMessage = errorMsg.toString();
        assertTrue(logMessage.contains("Exception data:"));
        assertTrue(logMessage.contains("name"));
        assertTrue(logMessage.contains("britton"));
        assertTrue(logMessage.contains("age"));
        assertTrue(logMessage.contains("18"));
    }

    @Test
    void appendParamsIncludesAnyExceptionData() throws Exception {
        ObjectProvider<ThreadPoolTaskExecutor> provider = mock(ObjectProvider.class);
        GXAsyncExceptionHandler handler = new GXAsyncExceptionHandler(provider);
        Dict data = Dict.create().set("name", "britton").set("age", 18);
        RuntimeException exception = new DataRuntimeException(data);
        StringBuilder errorMsg = new StringBuilder();
        Method appendParams = assertDoesNotThrow(() -> GXAsyncExceptionHandler.class
                .getDeclaredMethod("appendParams", StringBuilder.class, Throwable.class, Object[].class));
        appendParams.setAccessible(true);

        appendParams.invoke(handler, errorMsg, exception, new Object[0]);

        String logMessage = errorMsg.toString();
        assertTrue(logMessage.contains("Exception data:"));
        assertTrue(logMessage.contains("name"));
        assertTrue(logMessage.contains("britton"));
        assertTrue(logMessage.contains("age"));
        assertTrue(logMessage.contains("18"));
    }

    private static class DataRuntimeException extends RuntimeException {
        private final Dict data;

        private DataRuntimeException(Dict data) {
            super("async failed");
            this.data = data;
        }

        public Dict getData() {
            return data;
        }
    }
}
