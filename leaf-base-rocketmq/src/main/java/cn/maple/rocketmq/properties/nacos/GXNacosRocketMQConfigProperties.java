package cn.maple.rocketmq.properties.nacos;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.rocketmq.properties.GXNacosRocketMQConfigSourceCondition;
import cn.maple.rocketmq.properties.GXRocketMQConfigSources;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Validates and observes the Nacos RocketMQ configuration source.
 *
 * <p>Values are loaded by Spring Cloud Alibaba through
 * {@code spring.config.import=nacos:rocket-mq.yml}; the module no longer relies on the
 * {@code @NacosConfigurationProperties} annotation, which has no processor on the
 * classpath and therefore never took effect.</p>
 */
@Slf4j
@Component
@Conditional(GXNacosRocketMQConfigSourceCondition.class)
public class GXNacosRocketMQConfigProperties {
    public GXNacosRocketMQConfigProperties(Environment environment) {
        String nameServer = environment.getProperty("rocketmq.name-server");
        if (CharSequenceUtil.isBlank(nameServer)) {
            if (CharSequenceUtil.isBlank(environment.getProperty("rocketmq.config-source"))) {
                log.warn("RocketMQ config source auto-detected as nacos but rocketmq.name-server is blank. "
                        + "Set rocketmq.config-source explicitly to fail fast instead of warning.");
                return;
            }
            throw new GXBusinessException("RocketMQ config source is nacos but rocketmq.name-server is blank. "
                    + "Load rocket-mq.yml via spring.config.import=nacos:rocket-mq.yml or provide rocketmq.name-server.");
        }
        boolean explicitSource = CharSequenceUtil.isNotBlank(environment.getProperty(GXRocketMQConfigSources.SOURCE_KEY));
        log.info("RocketMQ config source: nacos ({}), name-server={}, dataId=rocket-mq.yml, loader=spring.config.import{}",
                explicitSource ? "explicit" : "auto-detected", nameServer,
                explicitSource ? "" : "; set rocketmq.config-source to make it explicit");
        if (CharSequenceUtil.isBlank(environment.getProperty("rocketmq.producer.group"))) {
            log.warn("RocketMQ config source is nacos but rocketmq.producer.group is blank. Producer may be skipped.");
        }
    }
}
