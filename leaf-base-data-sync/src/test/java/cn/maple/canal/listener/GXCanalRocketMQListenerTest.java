package cn.maple.canal.listener;

import cn.hutool.core.lang.Dict;
import cn.maple.canal.dto.GXCanalDataDto;
import cn.maple.canal.dto.inner.GXCanalParseResultInnerDto;
import cn.maple.canal.exception.GXCanalMessageRetryRequestedException;
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

class GXCanalRocketMQListenerTest {
    private final GXCanalMessageParseService parseService = Mockito.mock(GXCanalMessageParseService.class);

    private final GXCanalFailureHandler failureHandler = Mockito.mock(GXCanalFailureHandler.class);

    private final GXCanalConsumeStatistics statistics = new GXCanalConsumeStatistics();

    private final GXCanalRocketMQListener listener = new GXCanalRocketMQListener(
            parseService, failureHandler, statistics, new GXCanalLogThrottle());

    @Test
    void shouldDelegateSuccessToParseService() {
        Mockito.when(parseService.parse("payload")).thenReturn(List.of(successResult()));

        listener.onMessage("payload");

        assertEquals(1, statistics.successCount());
        assertEquals(1, statistics.batchEventsCount());
        Mockito.verifyNoInteractions(failureHandler);
    }

    @Test
    void shouldForwardNonRetryableFailure() {
        GXCanalParseResultInnerDto failure = GXCanalParseResultInnerDto.nonRetryableFailure("bad payload", 0);
        Mockito.when(parseService.parse("payload")).thenReturn(List.of(failure));

        listener.onMessage("payload");

        Mockito.verify(failureHandler).handle(eq(failure), eq("payload"), eq(0), anyString());
    }

    @Test
    void shouldTreatParseExceptionAsRetryable() {
        Mockito.when(parseService.parse("payload")).thenThrow(new IllegalStateException("boom"));

        listener.onMessage("payload");

        Mockito.verify(failureHandler).handle(any(GXCanalParseResultInnerDto.class), eq("payload"), eq(0),
                anyString());
    }

    @Test
    void shouldPropagateRetryRequestSoContainerReconsumes() {
        Mockito.when(parseService.parse("payload")).thenThrow(new IllegalStateException("boom"));
        Mockito.doThrow(new GXCanalMessageRetryRequestedException("retry"))
                .when(failureHandler)
                .handle(any(GXCanalParseResultInnerDto.class), eq("payload"), anyInt(), anyString());

        assertThrows(GXCanalMessageRetryRequestedException.class, () -> listener.onMessage("payload"));
    }

    private GXCanalParseResultInnerDto successResult() {
        GXCanalDataDto event = new GXCanalDataDto();
        event.setDatabase("db");
        event.setTable("user");
        return GXCanalParseResultInnerDto.success(event, "update", Dict.create(), 0);
    }
}
