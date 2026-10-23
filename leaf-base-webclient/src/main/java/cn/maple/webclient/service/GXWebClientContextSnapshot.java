package cn.maple.webclient.service;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;

/**
 * Immutable snapshot of the caller thread context (trace id, platform, optional
 * auth token) that can be carried into reactive or asynchronous threads.
 * <p>{@link #capture()} never generates an auth token: token generation depends on
 * the environment, not on thread local state, and is still performed by the
 * request filter. A token may be supplied explicitly when a specific outbound
 * identity must be delegated.</p>
 *
 * @author britton chen <britton@126.com>
 * @since 1.0.0
 */
public class GXWebClientContextSnapshot {
    /**
     * Client request attribute key holding the snapshot.
     */
    public static final String ATTRIBUTE_KEY = "GXWebClientContextSnapshot";

    private final @Nullable String traceId;

    private final @Nullable String platform;

    private final @Nullable String authToken;

    private final long capturedAtMillis;

    /**
     * Creates a snapshot value object.
     *
     * @param traceId         trace id, may be {@code null}
     * @param platform        platform value, may be {@code null}
     * @param authToken       auth token, may be {@code null}
     * @param capturedAtMillis capture timestamp
     */
    public GXWebClientContextSnapshot(@Nullable String traceId, @Nullable String platform,
                                      @Nullable String authToken, long capturedAtMillis) {
        this.traceId = traceId;
        this.platform = platform;
        this.authToken = authToken;
        this.capturedAtMillis = capturedAtMillis;
    }

    /**
     * Captures the current thread context. Never returns {@code null}; when no
     * request context or MDC value exists the returned snapshot is empty.
     *
     * @return snapshot of the calling thread context
     */
    public static GXWebClientContextSnapshot capture() {
        return new GXWebClientContextSnapshot(
                currentTraceId(),
                emptyToNull(GXCurrentRequestContextUtils.getHeader(GXTokenConstant.PLATFORM)),
                null,
                System.currentTimeMillis());
    }

    private static @Nullable String currentTraceId() {
        var request = GXCurrentRequestContextUtils.getHttpServletRequest();
        Object attribute = request == null ? null : request.getAttribute(GXTraceIdContextUtils.TRACE_ID_KEY);
        if (attribute != null) {
            return emptyToNull(attribute.toString());
        }
        return emptyToNull(GXTraceIdContextUtils.getTraceId());
    }

    /**
     * Captures the current thread context and attaches an explicitly provided token.
     *
     * @param authToken token to propagate, may be {@code null}
     * @return snapshot of the calling thread context
     */
    public static GXWebClientContextSnapshot capture(@Nullable String authToken) {
        GXWebClientContextSnapshot captured = capture();
        return new GXWebClientContextSnapshot(captured.traceId, captured.platform,
                emptyToNull(authToken), captured.capturedAtMillis);
    }

    /**
     * Applies non blank snapshot values to the outbound request headers.
     *
     * @param builder mutable request builder
     */
    public void apply(ClientRequest.Builder builder) {
        if (CharSequenceUtil.isNotBlank(traceId)) {
            builder.header(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
        }
        if (CharSequenceUtil.isNotBlank(platform)) {
            builder.header(GXTokenConstant.PLATFORM, platform);
        }
        if (CharSequenceUtil.isNotBlank(authToken)) {
            builder.header(GXCommonConstant.X_AUTH_TOKEN, authToken);
        }
    }

    /**
     * Returns whether the snapshot carries no usable value.
     *
     * @return {@code true} when every field is empty
     */
    public boolean isEmpty() {
        return CharSequenceUtil.isBlank(traceId)
                && CharSequenceUtil.isBlank(platform)
                && CharSequenceUtil.isBlank(authToken);
    }

    public @Nullable String getTraceId() {
        return traceId;
    }

    public @Nullable String getPlatform() {
        return platform;
    }

    public @Nullable String getAuthToken() {
        return authToken;
    }

    public long getCapturedAtMillis() {
        return capturedAtMillis;
    }

    private static @Nullable String emptyToNull(@Nullable String value) {
        return CharSequenceUtil.isBlank(value) ? null : value;
    }
}
