package cn.maple.rocketmq.properties.local;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import cn.maple.rocketmq.properties.GXLocalRocketMQConfigSourceCondition;
import cn.maple.rocketmq.properties.GXRocketMQConfigSources;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads RocketMQ configuration from {@code classpath:/<profile>/rocket-mq.yml}.
 *
 * <p>Activated by {@link GXLocalRocketMQConfigSourceCondition}. The class carries no bound
 * fields on purpose: {@code rocketmq-spring-boot-starter} already binds the
 * {@code rocketmq.*} prefix, this class only selects and observes the configuration source.</p>
 */
@Slf4j
@Component
@Conditional(GXLocalRocketMQConfigSourceCondition.class)
@PropertySource(value = {"classpath:/${spring.profiles.active:default}/rocket-mq.yml"},
        factory = GXYamlPropertySourceFactory.class, encoding = "utf-8", ignoreResourceNotFound = true)
public class GXLocalRocketMQConfigProperties {
    private static final String SCANNING_HINT =
            "Component scanning hint: enable @GXEnableLeafFramework or scan cn.maple.rocketmq to activate this module beans.";

    public GXLocalRocketMQConfigProperties(Environment environment) {
        String profile = environment.getProperty("spring.profiles.active", "default");
        String location = "/" + profile + "/rocket-mq.yml";
        boolean exists = new ClassPathResource(location).exists();
        String mode = CharSequenceUtil.isBlank(environment.getProperty(GXRocketMQConfigSources.SOURCE_KEY))
                ? "auto-detected" : "explicit";
        String resource = exists ? location : "NOT_FOUND (" + location + ")";
        log.info("RocketMQ config source: local ({}), profile={}, resource={}", mode, profile, resource);
        log.info(SCANNING_HINT);
    }
}
