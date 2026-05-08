package cn.maple.rocketmq.properties;

import cn.maple.rocketmq.properties.local.GXLocalRocketMQConfigProperties;
import cn.maple.rocketmq.properties.nacos.GXNacosRocketMQConfigProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class GXRocketMQConfigPropertiesTest {
    @Nested
    @SpringBootTest(
            classes = TestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.NONE,
            properties = {
                    "spring.cloud.nacos.config.import-check.enabled=false",
                    "spring.profiles.active=default",
                    "rocketmq.config-source=local"
            }
    )
    class LocalConfigSourceTest {
        @Test
        void loadsLocalConfigSource(ApplicationContext applicationContext) {
            assertThat(applicationContext.getBeansOfType(GXLocalRocketMQConfigProperties.class)).hasSize(1);
            assertThat(applicationContext.getBeansOfType(GXNacosRocketMQConfigProperties.class)).isEmpty();
            assertThat(applicationContext.getEnvironment().getProperty("rocketmq.test-marker")).isEqualTo("local-source");
        }
    }

    @Nested
    @SpringBootTest(
            classes = TestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.NONE,
            properties = {
                    "spring.cloud.nacos.config.import-check.enabled=false",
                    "rocketmq.config-source=nacos"
            }
    )
    class NacosConfigSourceTest {
        @Test
        void loadsNacosConfigSource(ApplicationContext applicationContext) {
            assertThat(applicationContext.getBeansOfType(GXNacosRocketMQConfigProperties.class)).hasSize(1);
            assertThat(applicationContext.getBeansOfType(GXLocalRocketMQConfigProperties.class)).isEmpty();
        }
    }

    @SpringBootConfiguration
    @Import({GXLocalRocketMQConfigProperties.class, GXNacosRocketMQConfigProperties.class})
    static class TestApplication {
    }
}
