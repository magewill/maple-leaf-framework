package cn.maple.core.datasource.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MyBatis write-operation listener settings.
 *
 * <p>Every default keeps the hardened behaviour; each switch can restore the legacy behaviour
 * without touching business code.</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "maple.framework.mybatis.listener")
public class GXMyBatisListenerProperties {
    /**
     * Whether the same listener is invoked at most once per write operation.
     */
    private boolean deduplicate = true;

    /**
     * Whether a listener exception is rethrown to the caller instead of being isolated.
     */
    private boolean propagateException = false;

    /**
     * Whether synchronous callbacks are deferred until the transaction commits.
     */
    private boolean syncAfterCommit = false;

    /**
     * Whether values that cannot be cloned are reported as degraded snapshots.
     */
    private boolean strictSnapshot = false;

    /**
     * Maximum number of cached listener declaration resolutions.
     */
    private int metadataCacheSize = 2048;

    /**
     * Idle expiration of the declaration resolution cache, in seconds.
     */
    private long metadataCacheExpireSeconds = 3600L;

    /**
     * Threshold in milliseconds above which a synchronous callback is reported as slow.
     */
    private long slowCallbackThresholdMs = 30000L;
}
