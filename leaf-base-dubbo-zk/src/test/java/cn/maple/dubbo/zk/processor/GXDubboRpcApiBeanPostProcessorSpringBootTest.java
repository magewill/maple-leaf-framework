package cn.maple.dubbo.zk.processor;

import cn.maple.dubbo.zk.support.GXDubboTestApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the processor is picked up by a real Spring context and never alters unrelated beans.
 */
@SpringBootTest(
        classes = {GXDubboTestApp.class, GXDubboRpcApiBeanPostProcessorSpringBootTest.ProcessorConfig.class},
        properties = {
                "spring.cloud.nacos.config.import-check.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false"
        }
)
class GXDubboRpcApiBeanPostProcessorSpringBootTest {
    @Autowired
    private ApplicationContext context;

    @Test
    void processorIsRegisteredInSpringContext() {
        assertTrue(context.getBeanNamesForType(GXDubboRpcApiBeanPostProcessor.class).length > 0);
    }

    @Test
    void unrelatedBeanIsReturnedUnchanged() {
        GXDubboRpcApiBeanPostProcessor processor = context.getBean(GXDubboRpcApiBeanPostProcessor.class);
        Object plainBean = new Object();

        assertSame(plainBean, processor.postProcessBeforeInitialization(plainBean, "plain"));
        assertSame(plainBean, processor.postProcessAfterInitialization(plainBean, "plain"));
        assertEquals(1, context.getBeanNamesForType(GXDubboRpcApiBeanPostProcessor.class).length);
    }

    @TestConfiguration
    static class ProcessorConfig {
        @Bean
        GXDubboRpcApiBeanPostProcessor gxDubboRpcApiBeanPostProcessor() {
            return new GXDubboRpcApiBeanPostProcessor();
        }
    }
}
