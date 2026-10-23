package cn.maple.sse.service;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.sse.service.impl.GXSseEmitterServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the {@code @PreDestroy} path as the container actually invokes it: closing the
 * Spring context must close every connection and reject new ones. The context is started
 * and closed by the test itself, so the Spring test framework is not managing it.
 */
class GXSseEmitterContextLifecycleSpringBootTest {

    @Test
    void containerShutdownClosesConnectionsAndRejectsNewOnes() {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.NONE)
                .run();
        try {
            GXSseEmitterService service = context.getBean(GXSseEmitterService.class);
            service.createSseConnect("ctx-lifecycle", 600L);
            assertEquals(1, service.getMetrics().connections());

            context.close();

            assertEquals(0, service.getMetrics().connections(),
                    "context shutdown must close every registered connection");
            assertThrows(GXBusinessException.class,
                    () -> service.createSseConnect("ctx-after-close", 600L),
                    "the destroyed service must reject new connections");
        } finally {
            context.close();
        }
    }

    @EnableAutoConfiguration
    @Import(GXSseEmitterServiceImpl.class)
    static class TestApplication {
    }
}
