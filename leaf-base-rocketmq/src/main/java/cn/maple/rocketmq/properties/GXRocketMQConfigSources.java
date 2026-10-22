package cn.maple.rocketmq.properties;

/**
 * Shared constants for RocketMQ configuration source selection.
 */
public final class GXRocketMQConfigSources {
    /**
     * Configuration key selecting the RocketMQ configuration source.
     */
    public static final String SOURCE_KEY = "rocketmq.config-source";

    /**
     * Explicit local source value.
     */
    public static final String LOCAL = "local";

    /**
     * Explicit Nacos source value.
     */
    public static final String NACOS = "nacos";

    /**
     * Marker class used to auto detect a Nacos aware classpath.
     */
    public static final String NACOS_ANNOTATION = "com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties";

    private GXRocketMQConfigSources() {
        throw new AssertionError("Utility class, cannot be instantiated");
    }
}
