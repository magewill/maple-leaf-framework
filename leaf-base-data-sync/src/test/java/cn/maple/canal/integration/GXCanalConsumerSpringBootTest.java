package cn.maple.canal.integration;

import cn.hutool.core.lang.Dict;
import cn.maple.canal.config.GXCanalConfig;
import cn.maple.canal.config.GXCanalPropertiesConfig;
import cn.maple.canal.config.GXCanalRocketMQConfig;
import cn.maple.canal.config.GXCanalRocketMQDependencyConfig;
import cn.maple.canal.dto.GXCanalDataDto;
import cn.maple.canal.listener.GXCanalRabbitMQListener;
import cn.maple.canal.listener.GXCanalRocketMQListener;
import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.service.GXProcessCanalDataService;
import cn.maple.canal.service.impl.GXCanalMessageParseServiceImpl;
import cn.maple.canal.service.impl.GXDefaultProcessCanalDataServiceImpl;
import cn.maple.canal.service.impl.rabbit.GXCanalRabbitMQFailureHandler;
import cn.maple.canal.service.impl.rocketmq.GXCanalRocketMQFailureHandler;
import cn.maple.canal.service.support.GXCanalHandlerResolver;
import cn.maple.canal.service.support.GXCanalLogThrottle;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end assembly test for both message middleware choices without a broker.
 *
 * <p>Real broker connectivity is intentionally out of scope here: it is exercised by the gated
 * scenarios documented in {@code quickstart.md}.</p>
 */
class GXCanalConsumerSpringBootTest {
    private static final String PAYLOAD = "{\"database\":\"demo\",\"table\":\"sys_user\",\"type\":\"UPDATE\","
            + "\"data\":[{\"id\":\"1\"}]}";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ScanConfig.class);

    @Test
    void shouldConsumeWithRabbitMqByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GXCanalRabbitMQListener.class);
            assertThat(context.getBeanNamesForType(GXCanalRocketMQListener.class)).isEmpty();
            assertThat(context.getBeanNamesForType(Queue.class)).isNotEmpty();

            AtomicInteger handled = handlerCounter(context.getBean(CountingHandler.class));
            context.getBean(GXCanalRabbitMQListener.class).listener(PAYLOAD, "trace-it", null);

            assertEquals(1, handled.get());
            assertEquals(1, context.getBean(GXCanalConsumeStatistics.class).successCount());
        });
    }

    @Test
    void shouldConsumeWithRocketMqWhenSelected() {
        contextRunner.withPropertyValues("canal.mq-type=rocketmq",
                "canal.rocketmq.topic=canal-it-topic",
                "canal.rocketmq.consumer-group=canal-it-group").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GXCanalRocketMQListener.class);
            assertThat(context.getBeanNamesForType(GXCanalRabbitMQListener.class)).isEmpty();
            assertEquals(0, context.getBeanNamesForType(Queue.class).length);
            assertEquals(0, context.getBeanNamesForType(Exchange.class).length);

            AtomicInteger handled = handlerCounter(context.getBean(CountingHandler.class));
            context.getBean(GXCanalRocketMQListener.class).onMessage(PAYLOAD);

            assertEquals(1, handled.get());
            assertEquals(1, context.getBean(GXCanalConsumeStatistics.class).successCount());
        });
    }

    @Test
    void shouldProduceSameResultForBothMiddlewares() {
        contextRunner.run(context -> {
            AtomicInteger handled = handlerCounter(context.getBean(CountingHandler.class));
            context.getBean(GXCanalRabbitMQListener.class).listener(PAYLOAD, null, null);
            assertEquals(1, handled.get());
        });
        contextRunner.withPropertyValues("canal.mq-type=rocketmq",
                "canal.rocketmq.topic=canal-it-topic",
                "canal.rocketmq.consumer-group=canal-it-group").run(context -> {
            AtomicInteger handled = handlerCounter(context.getBean(CountingHandler.class));
            context.getBean(GXCanalRocketMQListener.class).onMessage(PAYLOAD);
            assertEquals(1, handled.get());
        });
    }

    private AtomicInteger handlerCounter(CountingHandler handler) {
        return handler.counter;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties
    @Import({GXCanalPropertiesConfig.class, GXCanalConfig.class, GXCanalRocketMQConfig.class,
            GXCanalRocketMQDependencyConfig.class, GXCanalMessageParseServiceImpl.class,
            GXDefaultProcessCanalDataServiceImpl.class, GXCanalHandlerResolver.class,
            GXCanalConsumeStatistics.class, GXCanalLogThrottle.class, GXCanalRabbitMQFailureHandler.class,
            GXCanalRocketMQFailureHandler.class, GXCanalRabbitMQListener.class, GXCanalRocketMQListener.class})
    static class ScanConfig {
        @Bean("demoSysUserService")
        CountingHandler demoSysUserService() {
            return new CountingHandler();
        }
    }

    static class CountingHandler implements GXProcessCanalDataService {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Dict processUpdate(GXCanalDataDto canalData, Dict param) {
            counter.incrementAndGet();
            return Dict.create().set("status", "success");
        }

        @Override
        public Dict processInsert(GXCanalDataDto canalData, Dict param) {
            counter.incrementAndGet();
            return Dict.create().set("status", "success");
        }

        @Override
        public Dict processDelete(GXCanalDataDto canalData, Dict param) {
            counter.incrementAndGet();
            return Dict.create().set("status", "success");
        }
    }
}
