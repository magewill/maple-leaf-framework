package cn.maple.canal.listener;

import cn.hutool.core.lang.Dict;
import cn.maple.canal.dto.GXCanalDataDto;
import cn.maple.canal.dto.inner.GXCanalParseResultInnerDto;
import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.service.GXCanalMessageParseService;
import cn.maple.canal.service.support.GXCanalFailureHandler;
import cn.maple.canal.service.support.GXCanalLogThrottle;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class GXCanalRabbitMQListenerTest {
    private final GXCanalMessageParseService parseService = Mockito.mock(GXCanalMessageParseService.class);

    private final GXCanalFailureHandler failureHandler = Mockito.mock(GXCanalFailureHandler.class);

    private final GXCanalConsumeStatistics statistics = new GXCanalConsumeStatistics();

    private final GXCanalRabbitMQListener listener = new GXCanalRabbitMQListener(
            parseService, failureHandler, statistics, new GXCanalLogThrottle());

    @Test
    void shouldCountSuccessWithoutCallingFailureHandler() {
        Mockito.when(parseService.parse("payload")).thenReturn(List.of(successResult()));

        listener.listener("payload", "trace-1", null);

        assertEquals(1, statistics.successCount());
        assertEquals(0, statistics.skippedCount());
        Mockito.verifyNoInteractions(failureHandler);
    }

    @Test
    void shouldCountSkippedWithoutCallingFailureHandler() {
        Mockito.when(parseService.parse("payload"))
                .thenReturn(List.of(GXCanalParseResultInnerDto.skipped("ddl event: ALTER", 0)));

        listener.listener("payload", "trace-1", 0);

        assertEquals(1, statistics.skippedCount());
        Mockito.verifyNoInteractions(failureHandler);
    }

    @Test
    void shouldDelegateNonRetryableFailureToFailureHandler() {
        GXCanalParseResultInnerDto failure = GXCanalParseResultInnerDto.nonRetryableFailure("bad payload", 0);
        Mockito.when(parseService.parse("payload")).thenReturn(List.of(failure));

        listener.listener("payload", "trace-1", 2);

        Mockito.verify(failureHandler).handle(eq(failure), eq("payload"), eq(2), eq("trace-1"));
    }

    @Test
    void shouldTreatParseExceptionAsRetryable() {
        Mockito.when(parseService.parse("payload")).thenThrow(new IllegalStateException("boom"));

        listener.listener("payload", null, null);

        Mockito.verify(failureHandler).handle(any(GXCanalParseResultInnerDto.class), eq("payload"), eq(0),
                anyString());
        assertEquals(0, statistics.successCount());
    }

    @Test
    void shouldPropagateFailureHandlerRejection() {
        GXCanalParseResultInnerDto failure = GXCanalParseResultInnerDto.nonRetryableFailure("bad payload", 0);
        Mockito.when(parseService.parse("payload")).thenReturn(List.of(failure));
        Mockito.doThrow(new IllegalStateException("rejected")).when(failureHandler)
                .handle(any(GXCanalParseResultInnerDto.class), eq("payload"), anyInt(), anyString());

        assertThrows(IllegalStateException.class, () -> listener.listener("payload", "trace-1", 0));
    }

    @Test
    void shouldHandleMixedBatchAndReportSingleFailure() {
        Mockito.when(parseService.parse("payload")).thenReturn(List.of(
                successResult(),
                GXCanalParseResultInnerDto.skipped("ddl event: ALTER", 1),
                GXCanalParseResultInnerDto.nonRetryableFailure("database or table is blank", 2)));

        listener.listener("payload", "trace-1", 0);

        assertEquals(1, statistics.successCount());
        assertEquals(1, statistics.skippedCount());
        assertEquals(3, statistics.batchEventsCount());
        Mockito.verify(failureHandler, Mockito.times(1))
                .handle(any(GXCanalParseResultInnerDto.class), eq("payload"), eq(0), eq("trace-1"));
    }

    @Test
    void shouldCountEveryFailedBatchElementButActOnce() {
        Mockito.when(parseService.parse("payload")).thenReturn(List.of(
                successResult(),
                GXCanalParseResultInnerDto.nonRetryableFailure("bad-1", 1),
                GXCanalParseResultInnerDto.nonRetryableFailure("bad-2", 2)));

        listener.listener("payload", "trace-1", 0);

        assertEquals(2, statistics.nonRetryableFailureCount(), "every failed element must be counted");
        assertEquals(1, statistics.successCount());
        assertEquals(3, statistics.batchEventsCount());
        Mockito.verify(failureHandler, Mockito.times(1))
                .handle(any(GXCanalParseResultInnerDto.class), eq("payload"), eq(0), eq("trace-1"));
    }

    @Test
    void shouldPreferRetryableFailureForTheTransportAction() {
        GXCanalParseResultInnerDto permanent = GXCanalParseResultInnerDto.nonRetryableFailure("bad payload", 0);
        GXCanalParseResultInnerDto transientFailure = GXCanalParseResultInnerDto.retryableFailure("boom", 1);
        Mockito.when(parseService.parse("payload")).thenReturn(List.of(permanent, transientFailure));

        listener.listener("payload", "trace-1", 0);

        assertEquals(1, statistics.nonRetryableFailureCount());
        assertEquals(1, statistics.retryableFailureCount());
        Mockito.verify(failureHandler)
                .handle(eq(transientFailure), eq("payload"), eq(0), eq("trace-1"));
    }

    @Test
    void shouldRouteFailureLogsThroughThrottle() {
        GXCanalLogThrottle throttle = Mockito.mock(GXCanalLogThrottle.class);
        GXCanalRabbitMQListener throttledListener = new GXCanalRabbitMQListener(
                parseService, failureHandler, statistics, throttle);
        Mockito.when(parseService.parse("payload")).thenReturn(List.of(
                GXCanalParseResultInnerDto.nonRetryableFailure("bad payload", 0)));

        throttledListener.listener("payload", "trace-1", 0);

        Mockito.verify(throttle).warn(Mockito.startsWith("rabbit-failed-events"), Mockito.anyString(),
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private GXCanalParseResultInnerDto successResult() {
        GXCanalDataDto event = new GXCanalDataDto();
        event.setDatabase("db");
        event.setTable("user");
        return GXCanalParseResultInnerDto.success(event, "insert", Dict.create().set("status", "success"), 0);
    }
}
