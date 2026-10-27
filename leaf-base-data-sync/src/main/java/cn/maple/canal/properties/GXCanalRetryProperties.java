package cn.maple.canal.properties;

import cn.maple.canal.constant.GXCanalConstant;
import lombok.Data;

/**
 * Bounded retry settings for failed Canal messages.
 *
 * <p>The single field is {@code volatile} so consumer threads observe a rebind.</p>
 */
@Data
public class GXCanalRetryProperties {
    /**
     * Maximum re-delivery attempts. Zero means failed messages go to the dead letter destination immediately.
     */
    private volatile int maxAttempts = GXCanalConstant.DEFAULT_RETRY_MAX_ATTEMPTS;
}
