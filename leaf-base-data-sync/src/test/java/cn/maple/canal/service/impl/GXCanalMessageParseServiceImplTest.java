package cn.maple.canal.service.impl;

import cn.hutool.core.lang.Dict;
import cn.maple.canal.service.GXProcessCanalDataService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXCanalMessageParseServiceImplTest {
    private final GXCanalMessageParseServiceImpl service = new GXCanalMessageParseServiceImpl();

    @Test
    void shouldReturnErrorWhenMessageBlank() {
        Dict result = service.parseMessage("  ");
        assertEquals("error", result.getStr("status"));
    }

    @Test
    void shouldReturnErrorWhenMessageNotJson() {
        Dict result = service.parseMessage("abc");
        assertEquals("error", result.getStr("status"));
        assertTrue(result.getStr("message").contains("JSON"));
    }

    @Test
    void shouldReturnErrorWhenDatabaseOrTableBlank() {
        Dict result = service.parseMessage("{\"database\":\"db\",\"type\":\"INSERT\"}");
        assertEquals("error", result.getStr("status"));
        assertTrue(result.getStr("message").contains("database"));
    }

    @Test
    void shouldDispatchInsertToResolvedHandler() {
        GXProcessCanalDataService handler = Mockito.mock(GXProcessCanalDataService.class);
        Mockito.when(handler.processInsert(Mockito.any(), Mockito.any()))
                .thenReturn(Dict.create().set("status", "success").set("source", "insert"));

        String message = "{\"database\":\"db\",\"table\":\"user\",\"type\":\"INSERT\"}";
        try (MockedStatic<GXSpringContextUtils> mocked = Mockito.mockStatic(GXSpringContextUtils.class)) {
            mocked.when(() -> GXSpringContextUtils.getBean("dbUserService")).thenReturn(handler);

            Dict result = service.parseMessage(message);

            assertEquals("success", result.getStr("status"));
            assertEquals("insert", result.getStr("source"));
            Mockito.verify(handler, Mockito.times(1)).processInsert(Mockito.any(), Mockito.any());
        }
    }

    @Test
    void shouldDispatchUpdateToResolvedHandler() {
        GXProcessCanalDataService handler = Mockito.mock(GXProcessCanalDataService.class);
        Mockito.when(handler.processUpdate(Mockito.any(), Mockito.any()))
                .thenReturn(Dict.create().set("status", "success").set("source", "update"));

        String message = "{\"database\":\"db\",\"table\":\"user\",\"type\":\"UPDATE\"}";
        try (MockedStatic<GXSpringContextUtils> mocked = Mockito.mockStatic(GXSpringContextUtils.class)) {
            mocked.when(() -> GXSpringContextUtils.getBean("dbUserService")).thenReturn(handler);

            Dict result = service.parseMessage(message);

            assertEquals("success", result.getStr("status"));
            assertEquals("update", result.getStr("source"));
            Mockito.verify(handler, Mockito.times(1)).processUpdate(Mockito.any(), Mockito.any());
        }
    }

    @Test
    void shouldDispatchDeleteToResolvedHandler() {
        GXProcessCanalDataService handler = Mockito.mock(GXProcessCanalDataService.class);
        Mockito.when(handler.processDelete(Mockito.any(), Mockito.any()))
                .thenReturn(Dict.create().set("status", "success").set("source", "delete"));

        String message = "{\"database\":\"db\",\"table\":\"user\",\"type\":\"DELETE\"}";
        try (MockedStatic<GXSpringContextUtils> mocked = Mockito.mockStatic(GXSpringContextUtils.class)) {
            mocked.when(() -> GXSpringContextUtils.getBean("dbUserService")).thenReturn(handler);

            Dict result = service.parseMessage(message);

            assertEquals("success", result.getStr("status"));
            assertEquals("delete", result.getStr("source"));
            Mockito.verify(handler, Mockito.times(1)).processDelete(Mockito.any(), Mockito.any());
        }
    }

    @Test
    void shouldFallbackToDefaultHandlerWhenNamedBeanMissing() {
        GXProcessCanalDataService defaultHandler = Mockito.mock(GXProcessCanalDataService.class);
        Mockito.when(defaultHandler.processUpdate(Mockito.any(), Mockito.any()))
                .thenReturn(Dict.create().set("status", "success").set("source", "default"));

        String message = "{\"database\":\"db\",\"table\":\"user\",\"type\":\"UPDATE\"}";
        try (MockedStatic<GXSpringContextUtils> mocked = Mockito.mockStatic(GXSpringContextUtils.class)) {
            mocked.when(() -> GXSpringContextUtils.getBean("dbUserService")).thenReturn(null);
            mocked.when(() -> GXSpringContextUtils.getBean("defaultProcessCanalDataService")).thenReturn(defaultHandler);

            Dict result = service.parseMessage(message);

            assertEquals("success", result.getStr("status"));
            assertEquals("default", result.getStr("source"));
            Mockito.verify(defaultHandler, Mockito.times(1)).processUpdate(Mockito.any(), Mockito.any());
        }
    }

    @Test
    void shouldReturnErrorWhenTypeUnknown() {
        GXProcessCanalDataService handler = Mockito.mock(GXProcessCanalDataService.class);
        String message = "{\"database\":\"db\",\"table\":\"user\",\"type\":\"UPSERT\"}";

        try (MockedStatic<GXSpringContextUtils> mocked = Mockito.mockStatic(GXSpringContextUtils.class)) {
            mocked.when(() -> GXSpringContextUtils.getBean("dbUserService")).thenReturn(handler);

            Dict result = service.parseMessage(message);

            assertEquals("error", result.getStr("status"));
            assertTrue(result.getStr("message").contains("unknown"));
        }
    }

    @Test
    void shouldReturnErrorWhenNoHandlerExists() {
        String message = "{\"database\":\"db\",\"table\":\"user\",\"type\":\"INSERT\"}";

        try (MockedStatic<GXSpringContextUtils> mocked = Mockito.mockStatic(GXSpringContextUtils.class)) {
            mocked.when(() -> GXSpringContextUtils.getBean("dbUserService")).thenReturn(null);
            mocked.when(() -> GXSpringContextUtils.getBean("defaultProcessCanalDataService")).thenReturn(null);

            Dict result = service.parseMessage(message);

            assertEquals("error", result.getStr("status"));
            assertTrue(result.getStr("message").contains("handler"));
        }
    }

    @Test
    void shouldReturnErrorWhenBeanTypeMismatch() {
        String message = "{\"database\":\"db\",\"table\":\"user\",\"type\":\"INSERT\"}";

        try (MockedStatic<GXSpringContextUtils> mocked = Mockito.mockStatic(GXSpringContextUtils.class)) {
            mocked.when(() -> GXSpringContextUtils.getBean("dbUserService")).thenReturn(new Object());

            Dict result = service.parseMessage(message);

            assertEquals("error", result.getStr("status"));
            assertTrue(result.getStr("message").contains("type"));
        }
    }

    @Test
    void shouldReplaceNullHandlerResult() {
        GXProcessCanalDataService handler = Mockito.mock(GXProcessCanalDataService.class);
        Mockito.when(handler.processInsert(Mockito.any(), Mockito.any())).thenReturn(null);
        String message = "{\"database\":\"db\",\"table\":\"user\",\"type\":\"INSERT\"}";

        try (MockedStatic<GXSpringContextUtils> mocked = Mockito.mockStatic(GXSpringContextUtils.class)) {
            mocked.when(() -> GXSpringContextUtils.getBean("dbUserService")).thenReturn(handler);

            Dict result = service.parseMessage(message);

            assertEquals("warning", result.getStr("status"));
            assertTrue(result.getStr("message").contains("null"));
        }
    }

    @Test
    void shouldPropagateHandlerException() {
        GXProcessCanalDataService handler = Mockito.mock(GXProcessCanalDataService.class);
        Mockito.when(handler.processInsert(Mockito.any(), Mockito.any())).thenThrow(new IllegalStateException("boom"));
        String message = "{\"database\":\"db\",\"table\":\"user\",\"type\":\"INSERT\"}";

        try (MockedStatic<GXSpringContextUtils> mocked = Mockito.mockStatic(GXSpringContextUtils.class)) {
            mocked.when(() -> GXSpringContextUtils.getBean("dbUserService")).thenReturn(handler);

            assertThrows(IllegalStateException.class, () -> service.parseMessage(message));
        }
    }
}
