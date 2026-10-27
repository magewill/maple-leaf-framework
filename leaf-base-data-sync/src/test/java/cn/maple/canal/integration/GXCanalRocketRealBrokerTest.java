package cn.maple.canal.integration;

import cn.hutool.core.lang.Dict;
import cn.maple.canal.config.GXCanalPropertiesConfig;
import cn.maple.canal.config.GXCanalRocketMQConfig;
import cn.maple.canal.dto.GXCanalDataDto;
import cn.maple.canal.listener.GXCanalRocketMQListener;
import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.service.GXProcessCanalDataService;
import cn.maple.canal.service.impl.GXCanalMessageParseServiceImpl;
import cn.maple.canal.service.impl.rocketmq.GXCanalRocketMQFailureHandler;
import cn.maple.canal.service.support.GXCanalHandlerResolver;
import cn.maple.canal.service.support.GXCanalLogThrottle;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real RocketMQ verification, gated by {@code ROCKETMQ_NAME_SERVER}.
 *
 * <p>Without the variable the class is skipped. When present, the test sends a Canal payload through
 * {@code rocketmq-spring} and asserts that the shared parsing pipeline hands it to the table handler.</p>
 */
@EnabledIfEnvironmentVariable(named = "ROCKETMQ_NAME_SERVER", matches = ".+")
@SpringBootTest(classes = GXCanalRocketRealBrokerTest.BrokerConfig.class,
        properties = {
                "canal.mq-type=rocketmq",
                "canal.rocketmq.topic=canal-it-topic",
                "canal.rocketmq.consumer-group=canal-it-consumer-group",
                "rocketmq.name-server=${ROCKETMQ_NAME_SERVER}",
                "rocketmq.producer.group=canal-it-producer-group"
        })
@ImportAutoConfiguration(RocketMQAutoConfiguration.class)
class GXCanalRocketRealBrokerTest {
    private static final String VALID_PAYLOAD =
            "{\"database\":\"demo\",\"table\":\"sys_user\",\"type\":\"UPDATE\",\"data\":[{\"id\":\"1\"}]}";

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private CountingHandler countingHandler;

    @Autowired
    private GXCanalConsumeStatistics statistics;

    @Test
    void shouldConsumePayloadFromRocketMqTopic() throws Exception {
        rocketMQTemplate.syncSend("canal-it-topic", VALID_PAYLOAD);

        assertTrue(countingHandler.awaitInvocation(30, TimeUnit.SECONDS),
                "the table handler must receive the payload published to the RocketMQ topic");
        assertTrue(statistics.successCount() >= 1);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties
    @Import({GXCanalPropertiesConfig.class, GXCanalRocketMQConfig.class, GXCanalMessageParseServiceImpl.class,
            GXCanalHandlerResolver.class, GXCanalConsumeStatistics.class, GXCanalLogThrottle.class,
            GXCanalRocketMQFailureHandler.class, GXCanalRocketMQListener.class})
    static class BrokerConfig {
        @Bean("demoSysUserService")
        CountingHandler demoSysUserService() {
            return new CountingHandler();
        }
    }

    static class CountingHandler implements GXProcessCanalDataService {
        private final CountDownLatch invoked = new CountDownLatch(1);

        boolean awaitInvocation(long timeout, TimeUnit unit) throws InterruptedException {
            return invoked.await(timeout, unit);
        }

        @Override
        public Dict processUpdate(GXCanalDataDto canalData, Dict param) {
            invoked.countDown();
            return Dict.create().set("status", "success");
        }

        @Override
        public Dict processInsert(GXCanalDataDto canalData, Dict param) {
            invoked.countDown();
            return Dict.create().set("status", "success");
        }

        @Override
        public Dict processDelete(GXCanalDataDto canalData, Dict param) {
            invoked.countDown();
            return Dict.create().set("status", "success");
        }
    }
}
