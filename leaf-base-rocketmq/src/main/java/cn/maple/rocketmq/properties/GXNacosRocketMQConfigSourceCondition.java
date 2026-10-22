package cn.maple.rocketmq.properties;

import cn.hutool.core.text.CharSequenceUtil;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/**
 * Matches when the Nacos RocketMQ configuration source must be activated.
 *
 * <ul>
 *   <li>{@code rocketmq.config-source=nacos} wins over auto detection;</li>
 *   <li>without an explicit value, Nacos is assumed only when the Nacos configuration
 *       annotation is present on the classpath.</li>
 * </ul>
 */
public final class GXNacosRocketMQConfigSourceCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String configSource = context.getEnvironment().getProperty(GXRocketMQConfigSources.SOURCE_KEY);
        if (CharSequenceUtil.isNotBlank(configSource)) {
            return GXRocketMQConfigSources.NACOS.equalsIgnoreCase(configSource.trim());
        }
        return ClassUtils.isPresent(GXRocketMQConfigSources.NACOS_ANNOTATION, context.getClassLoader());
    }
}
