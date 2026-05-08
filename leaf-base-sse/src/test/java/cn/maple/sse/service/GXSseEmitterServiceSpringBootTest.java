package cn.maple.sse.service;

import cn.maple.sse.service.impl.GXSseEmitterServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(GXSseEmitterServiceImpl.class)
    static class TestApplication {
    }
}
