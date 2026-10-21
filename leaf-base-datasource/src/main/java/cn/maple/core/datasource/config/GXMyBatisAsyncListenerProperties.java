package cn.maple.core.datasource.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Async executor settings for MyBatis write-operation listeners.
 *
 * <p>The property keys are unchanged from the former {@code @Value} based configuration; only the
 * binding mechanism moved to type-safe properties.</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "maple.framework.mybatis.async.listener")
public class GXMyBatisAsyncListenerProperties {
    private double corePoolSizeFactor = 1.0D;

    private double maxPoolSizeFactor = 1.0D;

    private int queueCapacity = 1000;

    private int keepAliveSeconds = 120;

    private long longTaskThresholdMs = 30000L;

    private int awaitTerminationSeconds = 60;

    private Virtual virtual = new Virtual();

    @Data
    public static class Virtual {
        /**
         * {@code null} means the value falls back to {@code spring.threads.virtual.enabled}.
         */
        private Boolean enabled;

        /**
         * {@code 0} reuses the maximum pooled thread count, {@code -1} removes the limit.
         */
        private int concurrencyLimit = 0;
    }
}
