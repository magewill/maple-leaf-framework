package cn.maple.canal.service.impl;

import cn.hutool.core.lang.Dict;
import cn.maple.canal.dto.inner.GXCanalParseResultInnerDto;
import cn.maple.canal.enums.GXCanalOutcomeEnums;
import cn.maple.canal.service.GXProcessCanalDataService;
import cn.maple.canal.service.support.GXCanalHandlerResolver;
import cn.maple.canal.service.support.GXCanalLogThrottle;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXCanalMessageParseServiceImplTest {
    private static final String INSERT_EVENT = "{\"database\":\"db\",\"table\":\"user\",\"type\":\"INSERT\"}";

    private final GXCanalHandlerResolver resolver = Mockito.mock(GXCanalHandlerResolver.class);

    private final GXCanalMessageParseServiceImpl service =
            new GXCanalMessageParseServiceImpl(resolver, new GXCanalLogThrottle());

    @Test
    void shouldReturnNonRetryableWhenMessageBlank() {
        List<GXCanalParseResultInnerDto> results = service.parse("  ");

        assertEquals(1, results.size());
        assertEquals(GXCanalOutcomeEnums.NON_RETRYABLE_FAILURE, results.get(0).outcome());
        assertTrue(results.get(0).reason().contains("blank"));
    }

    @Test
    void shouldReturnNonRetryableWhenMessageNotJson() {
        GXCanalParseResultInnerDto result = service.parse("abc").get(0);

        assertEquals(GXCanalOutcomeEnums.NON_RETRYABLE_FAILURE, result.outcome());
        assertTrue(result.reason().contains("JSON"));
    }

    @Test
    void shouldReturnNonRetryableWhenJsonIsScalar() {
        GXCanalParseResultInnerDto result = service.parse("\"just-a-string\"").get(0);

        assertEquals(GXCanalOutcomeEnums.NON_RETRYABLE_FAILURE, result.outcome());
    }

    @Test
    void shouldReturnNonRetryableWhenDatabaseOrTableBlank() {
        GXCanalParseResultInnerDto result =
                service.parse("{\"database\":\"db\",\"type\":\"INSERT\"}").get(0);

        assertEquals(GXCanalOutcomeEnums.NON_RETRYABLE_FAILURE, result.outcome());
        assertTrue(result.reason().contains("database"));
    }

    @Test
    void shouldSkipDdlEvents() {
        GXCanalParseResultInnerDto byFlag = service
                .parse("{\"database\":\"db\",\"table\":\"user\",\"type\":\"ALTER\",\"isDdl\":true}").get(0);
        GXCanalParseResultInnerDto byType = service
                .parse("{\"database\":\"db\",\"table\":\"user\",\"type\":\"CREATE\"}").get(0);

        assertEquals(GXCanalOutcomeEnums.SKIPPED, byFlag.outcome());
        assertEquals(GXCanalOutcomeEnums.SKIPPED, byType.outcome());
    }

    @Test
    void shouldReturnNonRetryableWhenOperationUnknown() {
        GXProcessCanalDataService handler = Mockito.mock(GXProcessCanalDataService.class);
        Mockito.when(resolver.resolve("db", "user")).thenReturn(handler);

        GXCanalParseResultInnerDto result =
                service.parse("{\"database\":\"db\",\"table\":\"user\",\"type\":\"UPSERT\"}").get(0);

        assertEquals(GXCanalOutcomeEnums.NON_RETRYABLE_FAILURE, result.outcome());
        assertTrue(result.reason().contains("unknown"));
    }

    @Test
    void shouldDispatchInsertToResolvedHandler() {
        GXProcessCanalDataService handler = handlerReturning(Dict.create().set("status", "success").set("source", "insert"));
        Mockito.when(resolver.resolve("db", "user")).thenReturn(handler);

        GXCanalParseResultInnerDto result = service.parse(INSERT_EVENT).get(0);

        assertEquals(GXCanalOutcomeEnums.SUCCESS, result.outcome());
        assertEquals("insert", result.operation());
        assertNotNull(result.event());
        assertEquals("success", result.handlerResult().getStr("status"));
        Mockito.verify(handler, Mockito.times(1)).processInsert(Mockito.any(), Mockito.any());
    }

    @Test
    void shouldDispatchUpdateAndDelete() {
        GXProcessCanalDataService handler = handlerReturning(Dict.create().set("status", "success"));
        Mockito.when(resolver.resolve("db", "user")).thenReturn(handler);

        GXCanalParseResultInnerDto updated =
                service.parse("{\"database\":\"db\",\"table\":\"user\",\"type\":\"UPDATE\"}").get(0);
        GXCanalParseResultInnerDto deleted =
                service.parse("{\"database\":\"db\",\"table\":\"user\",\"type\":\"DELETE\"}").get(0);

        assertEquals("update", updated.operation());
        assertEquals("delete", deleted.operation());
        Mockito.verify(handler, Mockito.times(1)).processUpdate(Mockito.any(), Mockito.any());
        Mockito.verify(handler, Mockito.times(1)).processDelete(Mockito.any(), Mockito.any());
    }

    @Test
    void shouldReturnNonRetryableWhenNoHandlerAndNoDefault() {
        Mockito.when(resolver.resolve("db", "user")).thenReturn(null);

        GXCanalParseResultInnerDto result = service.parse(INSERT_EVENT).get(0);

        assertEquals(GXCanalOutcomeEnums.NON_RETRYABLE_FAILURE, result.outcome());
        assertTrue(result.reason().contains("handler"));
    }

    @Test
    void shouldReturnNonRetryableWhenHandlerReturnsNull() {
        GXProcessCanalDataService handler = handlerReturning(null);
        Mockito.when(resolver.resolve("db", "user")).thenReturn(handler);

        GXCanalParseResultInnerDto result = service.parse(INSERT_EVENT).get(0);

        assertEquals(GXCanalOutcomeEnums.NON_RETRYABLE_FAILURE, result.outcome());
        assertTrue(result.reason().contains("null"));
    }

    @Test
    void shouldPropagateHandlerException() {
        GXProcessCanalDataService handler = handlerReturning(null);
        Mockito.when(handler.processInsert(Mockito.any(), Mockito.any())).thenThrow(new IllegalStateException("boom"));
        Mockito.when(resolver.resolve("db", "user")).thenReturn(handler);

        assertThrows(IllegalStateException.class, () -> service.parse(INSERT_EVENT));
    }

    @Test
    void shouldProcessEveryEventOfBatchPayload() throws IOException {
        GXProcessCanalDataService handler = handlerReturning(Dict.create().set("status", "success"));
        Mockito.when(resolver.resolve("demo", "sys_user")).thenReturn(handler);

        List<GXCanalParseResultInnerDto> results = service.parse(readResource("/canal/batch.json"));

        assertEquals(3, results.size());
        assertEquals(GXCanalOutcomeEnums.SUCCESS, results.get(0).outcome());
        assertEquals(0, results.get(0).batchIndex());
        assertEquals(GXCanalOutcomeEnums.SKIPPED, results.get(1).outcome());
        assertEquals(1, results.get(1).batchIndex());
        assertEquals(GXCanalOutcomeEnums.NON_RETRYABLE_FAILURE, results.get(2).outcome());
        assertEquals(2, results.get(2).batchIndex());
    }

    @Test
    void shouldIsolateBatchElementFromHandlerFailure() {
        GXProcessCanalDataService handler = handlerReturning(Dict.create().set("status", "success"));
        Mockito.when(handler.processUpdate(Mockito.any(), Mockito.any()))
                .thenThrow(new IllegalStateException("boom"));
        Mockito.when(resolver.resolve("db", "user")).thenReturn(handler);
        String batch = "[{\"database\":\"db\",\"table\":\"user\",\"type\":\"UPDATE\"},"
                + "{\"database\":\"db\",\"table\":\"user\",\"type\":\"INSERT\"}]";

        List<GXCanalParseResultInnerDto> results = service.parse(batch);

        assertEquals(2, results.size());
        assertEquals(GXCanalOutcomeEnums.RETRYABLE_FAILURE, results.get(0).outcome());
        assertEquals(0, results.get(0).batchIndex());
        assertEquals(GXCanalOutcomeEnums.SUCCESS, results.get(1).outcome(),
                "a failing element must not stop the remaining batch elements");
        Mockito.verify(handler, Mockito.times(1)).processInsert(Mockito.any(), Mockito.any());
    }

    @Test
    void shouldParseSingleObjectResource() throws IOException {
        GXProcessCanalDataService handler = handlerReturning(Dict.create().set("status", "success"));
        Mockito.when(resolver.resolve("demo", "sys_user")).thenReturn(handler);

        List<GXCanalParseResultInnerDto> results = service.parse(readResource("/canal/single.json"));

        assertEquals(1, results.size());
        assertEquals(GXCanalOutcomeEnums.SUCCESS, results.get(0).outcome());
        assertEquals("update", results.get(0).operation());
    }

    @Test
    void shouldKeepLegacyParseMessageContract() {
        Dict handlerResult = Dict.create().set("status", "success").set("source", "insert");
        GXProcessCanalDataService handler = handlerReturning(handlerResult);
        Mockito.when(resolver.resolve("db", "user")).thenReturn(handler);

        Dict legacyResult = service.parseMessage(INSERT_EVENT);

        assertEquals(handlerResult, legacyResult);
        assertEquals("insert", legacyResult.getStr("source"));
    }

    @Test
    void shouldReportWarningForNullHandlerResultOnLegacyPath() {
        GXProcessCanalDataService handler = handlerReturning(null);
        Mockito.when(resolver.resolve("db", "user")).thenReturn(handler);

        Dict legacyResult = service.parseMessage(INSERT_EVENT);

        assertEquals("warning", legacyResult.getStr("status"));
    }

    @Test
    void shouldReportErrorForDdlOnLegacyPathAsSuccess() {
        Dict legacyResult = service.parseMessage("{\"database\":\"db\",\"table\":\"user\",\"type\":\"CREATE\"}");

        assertEquals("success", legacyResult.getStr("status"));
        assertNull(legacyResult.get("source"));
    }

    private GXProcessCanalDataService handlerReturning(Dict result) {
        GXProcessCanalDataService handler = Mockito.mock(GXProcessCanalDataService.class);
        Mockito.when(handler.processInsert(Mockito.any(), Mockito.any())).thenReturn(result);
        Mockito.when(handler.processUpdate(Mockito.any(), Mockito.any())).thenReturn(result);
        Mockito.when(handler.processDelete(Mockito.any(), Mockito.any())).thenReturn(result);
        return handler;
    }

    private String readResource(String path) throws IOException {
        try (InputStream stream = Objects.requireNonNull(getClass().getResourceAsStream(path))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
