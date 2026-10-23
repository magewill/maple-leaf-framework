package cn.maple.webclient.service;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXAuthCodeUtils;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SPI used by {@code leaf-base-webclient} to supply auth, trace and platform
 * metadata for outbound WebClient calls.
 * <p>Implementations may be invoked concurrently and should keep token lookup
 * and validation thread-safe.</p>
 *
 * @author britton
 * @since 1.0.0
 */
public interface GXWebClientService {
    /**
     * Maximum reuse window for generated tokens.
     */
    long TOKEN_CACHE_WINDOW_MILLIS = 60_000L;

    /**
     * Generated token cache keyed by {@code source + secret}.
     */
    Map<String, CachedToken> TOKEN_CACHE = new ConcurrentHashMap<>();

    /**
     * Generates the auth token used for outbound WebClient requests.
     *
     * @return auth token
     */
    default String generateHttpAuthToken() {
        String tokenSource = GXCommonUtils.getEnvironmentValue("maple.framework.web.client.token", String.class);
        if (CharSequenceUtil.isBlank(tokenSource)) {
            throw new GXBusinessException("请配置maple.framework.web.client.token");
        }
        return generateHttpAuthToken(tokenSource, GXTokenConstant.WEB_CLIENT_TOKEN_EXPIRE);
    }

    /**
     * Encodes a WebClient auth token with the configured secret.
     * <p>The generated value is reused for at most {@code min(60s, expiry/2)} and
     * invalidated when the source or secret changes.</p>
     *
     * @param source token payload
     * @param expiry expiry in seconds
     * @return encoded auth token
     */
    default String generateHttpAuthToken(String source, int expiry) {
        if (CharSequenceUtil.isBlank(source)) {
            throw new GXBusinessException("Please configure maple.framework.web.client.token");
        }
        if (expiry <= 0) {
            throw new GXBusinessException("WebClient Token expiry must be greater than 0");
        }
        String authTokenSecret = getAuthTokenSecret();
        long windowMillis = Math.min(TOKEN_CACHE_WINDOW_MILLIS, (expiry * 1000L) / 2);
        String cacheKey = source + '\u0000' + authTokenSecret;
        long now = System.currentTimeMillis();
        CachedToken cachedToken = TOKEN_CACHE.get(cacheKey);
        if (cachedToken != null && cachedToken.windowMillis() == windowMillis
                && cachedToken.expiresAtMillis() > now) {
            return cachedToken.token();
        }

        String authToken = GXAuthCodeUtils.authCodeEncode(source, authTokenSecret, expiry);
        TOKEN_CACHE.put(cacheKey, new CachedToken(authToken, now + windowMillis, windowMillis));
        return authToken;
    }

    /**
     * Clears the generated token cache.
     */
    static void clearTokenCache() {
        TOKEN_CACHE.clear();
    }

    /**
     * Validates the inbound WebClient auth token from the current request.
     *
     * @return {@code true} when token is valid
     */
    default boolean checkTokenValidity() {
        String webClientToken = GXCurrentRequestContextUtils.getHeader(GXCommonConstant.X_AUTH_TOKEN);
        if (CharSequenceUtil.isBlank(webClientToken)) {
            return false;
        }

        try {
            String authTokenSecret = getAuthTokenSecret();
            String decodedToken = GXAuthCodeUtils.authCodeDecode(webClientToken, authTokenSecret);
            return CharSequenceUtil.isNotBlank(decodedToken)
                    && !CharSequenceUtil.equalsIgnoreCase(decodedToken, "{}");
        } catch (Exception e) {
            LoggerFactory.getLogger(GXWebClientService.class).warn(
                    "Validate WebClient auth token failed: exceptionType={}", e.getClass().getName());
            return false;
        }
    }

    /**
     * Returns the secret used to encode and decode WebClient auth tokens.
     *
     * @return auth token secret
     */
    default String getAuthTokenSecret() {
        String tokenSecret = GXCommonUtils.getEnvironmentValue("maple.framework.web.client.secret", String.class);
        if (CharSequenceUtil.isBlank(tokenSecret)) {
            throw new GXBusinessException("请配置maple.framework.web.client.secret");
        }
        return tokenSecret;
    }

    default void logRequestResponse(HttpMethod method, String url, HttpHeaders requestHeaders,
                                    @Nullable Object requestBody, HttpStatusCode statusCode,
                                    HttpHeaders responseHeaders, @Nullable Object responseBody, long durationMs) {
    }

    default void logRequestResponse(HttpMethod method, String url, HttpStatusCode statusCode,
                                    long durationMs, String requestId) {
    }

    default void logRequestException(HttpMethod method, String url, Throwable exception, String requestId) {
    }

    /**
     * Returns request headers that should be masked by logging implementations.
     *
     * @return header name to masked value mapping
     */
    default Map<String, String> getSensitiveHeaderFields() {
        return Map.of(
                "Authorization", "******",
                "Cookie", "******",
                "X-Auth-Token", "******",
                "X-HMAC-Signature", "******",
                "X-API-Key", "******"
        );
    }

    /**
     * Returns the platform header from the current request.
     *
     * @return platform value, or {@code null} when absent
     */
    default @Nullable String getPlatform() {
        return GXCurrentRequestContextUtils.getHeader(GXTokenConstant.PLATFORM);
    }

    /**
     * Generates an HMAC value.
     *
     * @param data   payload
     * @param secret secret
     * @return HMAC value
     */
    default String generateHmac(Object data, String secret) {
        return GXCommonUtils.generateHmac(data, secret);
    }

    /**
     * Checks whether the provided HMAC matches the payload.
     *
     * @param secret     secret
     * @param clientHmac client HMAC
     * @param payload    payload
     * @return {@code true} when matched
     */
    default boolean checkHmac(String secret, String clientHmac, Object payload) {
        return GXCommonUtils.checkHmac(secret, clientHmac, payload);
    }

    /**
     * Returns trace id from the current request attribute, then MDC fallback.
     *
     * @return trace id, or empty string when absent
     */
    default String getTraceId() {
        HttpServletRequest httpServletRequest = GXCurrentRequestContextUtils.getHttpServletRequest();
        Object traceId = httpServletRequest != null
                ? httpServletRequest.getAttribute(GXTraceIdContextUtils.TRACE_ID_KEY)
                : null;

        return Optional.ofNullable(traceId)
                .map(Object::toString)
                .orElse(GXTraceIdContextUtils.getTraceId());
    }

    /**
     * Cached auth token entry.
     *
     * @param token           encoded token
     * @param expiresAtMillis absolute expiry of the reuse window
     * @param windowMillis    reuse window that produced the entry
     */
    record CachedToken(String token, long expiresAtMillis, long windowMillis) {
    }
}
