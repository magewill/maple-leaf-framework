package cn.maple.canal.service.support;

import cn.maple.canal.dto.inner.GXCanalParseResultInnerDto;

/**
 * Transport level action for a failed Canal event.
 *
 * <p>Outcome classification is shared and already decided before this port is called; implementations
 * only decide how the message is acknowledged, retried or dead lettered for their broker.</p>
 */
public interface GXCanalFailureHandler {
    /**
     * Applies the transport action for a failure outcome.
     *
     * @param result            failure result; must not be a success outcome
     * @param rawPayload        original payload text
     * @param currentRetryCount retry count already carried by the message
     * @param traceId           trace id of the current delivery, propagated to re-published messages
     *                          when the broker supports headers
     */
    void handle(GXCanalParseResultInnerDto result, String rawPayload, int currentRetryCount, String traceId);
}
