package cn.maple.rocketmq.properties;

import cn.hutool.core.text.CharSequenceUtil;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/**
 * Matches when the local RocketMQ configuration source must be activated.
 *
 * <ul>
 *   <li>{@code rocketmq.config-source=local} wins over auto detection;</li>
 *   <li>without an explicit value, the local source is used only when the Nacos
 *       configuration annotation is absent from the classpath.</li>
 * </ul>
 */
public final class GXLocalRocketMQConfigSourceCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String configSource = context.getEnvironment().getProperty(GXRocketMQConfigSources.SOURCE_KEY);
        if (CharSequenceUtil.isNotBlank(configSource)) {
            return GXRocketMQConfigSources.LOCAL.equalsIgnoreCase(configSource.trim());
        }
        return !ClassUtils.isPresent(GXRocketMQConfigSources.NACOS_ANNOTATION, context.getClassLoader());
    }
}
