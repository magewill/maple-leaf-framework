package cn.maple.rocketmq.properties;

import cn.hutool.core.text.CharSequenceUtil;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails fast when {@code rocketmq.config-source} carries an unsupported value.
 *
 * <p>Supported values are {@code local} and {@code nacos}; a blank value means
 * "auto detect from the classpath".</p>
 */
@Component
public class GXRocketMQConfigSourceValidator {
    public GXRocketMQConfigSourceValidator(Environment environment) {
        String configSource = environment.getProperty(GXRocketMQConfigSources.SOURCE_KEY);
        if (CharSequenceUtil.isBlank(configSource)) {
            return;
        }
        String normalized = configSource.trim().toLowerCase();
        if (!GXRocketMQConfigSources.LOCAL.equals(normalized) && !GXRocketMQConfigSources.NACOS.equals(normalized)) {
            throw new IllegalStateException("Unsupported " + GXRocketMQConfigSources.SOURCE_KEY + "=" + configSource
                    + ". Supported values are local and nacos.");
        }
    }
}
