package cn.maple.core.framework.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GXLoggerUtilsTest {
    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void logInfoWritesMessageWithoutArguments() {
        Logger logger = mock(Logger.class);
        when(logger.isInfoEnabled()).thenReturn(true);
        MDC.put(GXTraceIdContextUtils.TRACE_ID_KEY, "trace-1");

        GXLoggerUtils.logInfo(logger, "config file missing");

        verify(logger).info(startsWith(GXTraceIdContextUtils.TRACE_ID_KEY + " : trace-1 --> thread : "));
    }

    @Test
    void logInfoWritesMessageWithArguments() {
        Logger logger = mock(Logger.class);
        when(logger.isInfoEnabled()).thenReturn(true);
        MDC.put(GXTraceIdContextUtils.TRACE_ID_KEY, "trace-2");

        GXLoggerUtils.logInfo(logger, "load config", "demo.yml", 2);

        boolean loggedWithArguments = mockingDetails(logger).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("info")
                        && invocation.getArguments().length == 3
                        && "demo.yml".equals(invocation.getArguments()[1])
                        && Integer.valueOf(2).equals(invocation.getArguments()[2]));
        assertTrue(loggedWithArguments);
    }

    @Test
    void logMethodsSkipWhenLevelDisabledOrLoggerIsNull() {
        Logger logger = mock(Logger.class);

        GXLoggerUtils.logDebug(logger, "debug", "value");
        GXLoggerUtils.logWarn(logger, "warn", "value");
        GXLoggerUtils.logError(logger, "error", "value");
        GXLoggerUtils.logInfo(null, "info");

        verify(logger, never()).debug(any(String.class), any(Object[].class));
        verify(logger, never()).warn(any(String.class), any(Object[].class));
        verify(logger, never()).error(any(String.class), any(Object[].class));
    }

    @Test
    void logErrorWithThrowableHandlesNullThrowable() {
        Logger logger = mock(Logger.class);
        when(logger.isErrorEnabled()).thenReturn(true);

        GXLoggerUtils.logError(logger, (Throwable) null);

        verify(logger).error(startsWith(GXTraceIdContextUtils.TRACE_ID_KEY + " : "));
    }

    @Test
    void logErrorWithDescAndThrowableWritesUnifiedFormat() {
        Logger logger = mock(Logger.class);
        RuntimeException exception = new RuntimeException("failed");
        when(logger.isErrorEnabled()).thenReturn(true);

        GXLoggerUtils.logErrorWithThrowable(logger, "save failed", exception, "order-1");

        boolean loggedWithThrowable = mockingDetails(logger).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("error")
                        && invocation.getArguments().length == 3
                        && invocation.getArguments()[0].toString().contains("desc : save failed")
                        && invocation.getArguments()[0].toString().contains("detail : {}")
                        && "order-1".equals(invocation.getArguments()[1])
                        && exception.equals(invocation.getArguments()[2]));
        assertTrue(loggedWithThrowable);
    }

    @Test
    void logErrorTreatsTrailingThrowableAsExceptionNotDetail() {
        Logger logger = mock(Logger.class);
        RuntimeException exception = new RuntimeException("failed");
        when(logger.isErrorEnabled()).thenReturn(true);

        GXLoggerUtils.logError(logger, "save failed", "order-1", exception);

        Object[] arguments = mockingDetails(logger).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("error"))
                .findFirst()
                .orElseThrow()
                .getArguments();
        assertEquals(3, arguments.length);
        assertTrue(arguments[0].toString().contains("detail : {}"));
        assertTrue(!arguments[0].toString().contains("detail : {},{}"));
        assertEquals("order-1", arguments[1]);
        assertEquals(exception, arguments[2]);
    }
}
