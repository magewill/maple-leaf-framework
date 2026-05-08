package cn.maple.canal.listener;

import cn.maple.canal.service.GXCanalMessageParseService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GXCanalRabbitMQListenerTest {
    @Test
    void shouldInvokeParseService() {
        GXCanalMessageParseService parseService = Mockito.mock(GXCanalMessageParseService.class);
        GXCanalRabbitMQListener listener = new GXCanalRabbitMQListener();
        ReflectionTestUtils.setField(listener, "canalMessageParseService", parseService);

        listener.listener("{\"type\":\"INSERT\"}");

        verify(parseService, times(1)).parseMessage(anyString());
    }

    @Test
    void shouldRethrowWhenParseServiceFails() {
        GXCanalMessageParseService parseService = Mockito.mock(GXCanalMessageParseService.class);
        Mockito.when(parseService.parseMessage(anyString())).thenThrow(new IllegalStateException("boom"));
        GXCanalRabbitMQListener listener = new GXCanalRabbitMQListener();
        ReflectionTestUtils.setField(listener, "canalMessageParseService", parseService);

        assertThrows(IllegalStateException.class, () -> listener.listener("{\"type\":\"INSERT\"}"));
        verify(parseService, times(1)).parseMessage(anyString());
    }
}
