package cn.maple.sse.service;

import cn.maple.sse.service.impl.GXSseEmitterServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@SpringBootTest(classes = GXSseEmitterServiceSpringBootTest.TestApplication.class)
class GXSseEmitterServiceSpringBootTest {
    @Autowired
    private GXSseEmitterService sseEmitterService;

    @Test
    void springBootContextLoadsSseEmitterServiceBean() {
        assertNotNull(sseEmitterService);
        assertInstanceOf(GXSseEmitterServiceImpl.class, sseEmitterService);

        sseEmitterService.createSseConnect("spring-client", 120L);
        assertEquals(1, ((GXSseEmitterServiceImpl) sseEmitterService).getActiveConnectionCount());
        sseEmitterService.closeConnect("spring-client");
    }

    @Test
    void springBootManagedBean_ExposesMetricsAndReplacesConnections() {
        sseEmitterService.createSseConnect("spring-metrics", 120L);
        SseEmitter first = sseEmitterService.getSseEmitterByClientId("spring-metrics");
        SseEmitter second = sseEmitterService.createSseConnect("spring-metrics", 120L);

        assertSame(second, sseEmitterService.getSseEmitterByClientId("spring-metrics"));
        assertEquals(1, sseEmitterService.getMetrics().connections());
        assertNotNull(first);

        sseEmitterService.closeConnect("spring-metrics");
        assertEquals(0, sseEmitterService.getMetrics().connections());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(GXSseEmitterServiceImpl.class)
    static class TestApplication {
    }
}
