package cn.maple.feign.service;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXAuthCodeUtils;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.Optional;

/**
 * Extension contract for Maple Feign calls.
 *
 * <p>The framework interceptor uses this service to create internal auth
 * tokens, propagate platform and trace context, and expose shared HMAC helpers.
 * Applications may implement this interface as a Spring bean to customize any
 * of those behaviors.</p>
 *
 * <p>The default token implementation requires the following properties:
 * {@code maple.framework.web.feign.token} and
 * {@code maple.framework.web.feign.secret}.</p>
 */
public interface GXFeignService {
    /**
     * Generates the default Feign auth token from
     * {@code maple.framework.web.feign.token}.
     *
     * @return encoded auth token
     * @throws GXBusinessException when the token source or secret is missing
     */
    default String generateHttpAuthToken() {
        String tokenSource = GXCommonUtils.getEnvironmentValue("maple.framework.web.feign.token", String.class);
        if (CharSequenceUtil.isBlank(tokenSource)) {
            throw new GXBusinessException("请配置maple.framework.web.feign.token");
        }
        return generateHttpAuthToken(tokenSource, GXTokenConstant.WEB_CLIENT_TOKEN_EXPIRE);
    }

    /**
     * Generates a Feign auth token from a source value and expiry.
     *
     * @param source value to encode into the token
     * @param expiry token expiry in seconds
     * @return encoded auth token
     * @throws GXBusinessException when the secret is missing or token creation fails
     */
    default String generateHttpAuthToken(String source, int expiry) {
        String authTokenSecret = getAuthTokenSecret();
        return GXAuthCodeUtils.authCodeEncode(source, authTokenSecret, expiry);
    }

    /**
     * Checks whether the current request contains a valid Feign auth token.
     *
     * @return {@code true} when the current request token can be decoded
     */
    default boolean checkTokenValidity() {
        String webClientToken = GXCurrentRequestContextUtils.getHeader(GXCommonConstant.X_AUTH_TOKEN);
        if (CharSequenceUtil.isBlank(webClientToken)) {
            return false;
        }

        try {
            String authTokenSecret = getAuthTokenSecret();
            String decodedToken = GXAuthCodeUtils.authCodeDecode(webClientToken, authTokenSecret);
            return !CharSequenceUtil.equalsIgnoreCase(decodedToken, "{}");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the secret used to encode and decode Feign auth tokens.
     *
     * @return configured token secret
     * @throws GXBusinessException when {@code maple.framework.web.feign.secret} is missing
     */
    default String getAuthTokenSecret() {
        String tokenSecret = GXCommonUtils.getEnvironmentValue("maple.framework.web.feign.secret", String.class);
        if (CharSequenceUtil.isBlank(tokenSecret)) {
            throw new GXBusinessException("Please configure 'maple.framework.web.feign.secret' key");
        }
        return tokenSecret;
    }

    /**
     * Returns request headers that should be masked by callers when logging.
     *
     * @return header names mapped to their masked display value
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
     * Returns the current request platform header.
     *
     * @return platform value, or {@code null} when there is no HTTP request context
     */
    default String getPlatform() {
        return GXCurrentRequestContextUtils.getHeader(GXTokenConstant.PLATFORM);
    }

    /**
     * Generates an HMAC signature for the supplied payload.
     *
     * @param data   payload to sign
     * @param secret signing secret
     * @return generated HMAC value
     * @throws GXBusinessException when signing fails
     */
    default String generateHmac(Object data, String secret) {
        return GXCommonUtils.generateHmac(data, secret);
    }

    /**
     * Checks whether a client HMAC matches the supplied payload and secret.
     *
     * @param secret     signing secret
     * @param clientHmac HMAC value supplied by the caller
     * @param payload    original payload
     * @return {@code true} when the HMAC values match
     */
    default boolean checkHmac(String secret, String clientHmac, Object payload) {
        return GXCommonUtils.checkHmac(secret, clientHmac, payload);
    }

    /**
     * Resolves the current trace id in request attribute, request header, MDC,
     * then generated value order.
     *
     * @return non-blank trace id for the current Feign call
     */
    default String getTraceId() {
        HttpServletRequest httpServletRequest = GXCurrentRequestContextUtils.getHttpServletRequest();
        Object traceIdAttribute = httpServletRequest != null
                ? httpServletRequest.getAttribute(GXTraceIdContextUtils.TRACE_ID_KEY)
                : null;

        String currentTraceId = Optional.ofNullable(traceIdAttribute)
                .map(Object::toString)
                .filter(CharSequenceUtil::isNotBlank)
                .orElseGet(() -> httpServletRequest == null ? null : httpServletRequest.getHeader(GXTraceIdContextUtils.TRACE_ID_KEY));
        if (CharSequenceUtil.isBlank(currentTraceId)) {
            currentTraceId = GXTraceIdContextUtils.getTraceId();
        }
        if (CharSequenceUtil.isBlank(currentTraceId)) {
            return GXTraceIdContextUtils.generateTraceId();
        }
        return currentTraceId;
    }
}
