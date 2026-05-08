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
 * Feign 调用扩展契约，提供认证 token、平台标识、traceId、HMAC 和敏感请求头的默认能力。
 *
 * <p>使用默认 token 实现时，必须配置 `maple.framework.web.feign.token` 和
 * `maple.framework.web.feign.secret`。</p>
 */
public interface GXFeignService {
    /**
     * 生成 Feign 认证 token。
     *
     * @return 生成的认证令牌字符串
     * @throws GXBusinessException 当未配置基础令牌源时抛出
     */
    default String generateHttpAuthToken() {
        String tokenSource = GXCommonUtils.getEnvironmentValue("maple.framework.web.feign.token", String.class);
        if (CharSequenceUtil.isBlank(tokenSource)) {
            throw new GXBusinessException("请配置maple.framework.web.feign.token");
        }
        return generateHttpAuthToken(tokenSource, GXTokenConstant.WEB_CLIENT_TOKEN_EXPIRE);
    }

    /**
     * 使用指定源字符串和过期时间生成 Feign 认证 token。
     *
     * @param source 源字符串，用于生成Token的基础数据
     * @param expiry Token的过期时间，单位为秒
     * @return HTTP认证Token字符串，不应返回null
     * @throws GXBusinessException 当Token生成失败时抛出
     */
    default String generateHttpAuthToken(String source, int expiry) {
        String authTokenSecret = getAuthTokenSecret();
        return GXAuthCodeUtils.authCodeEncode(source, authTokenSecret, expiry);
    }

    /**
     * 检查当前请求中的 Feign 认证 token 是否有效。
     *
     * @return true 有效 ; false 无效
     */
    default boolean checkTokenValidity() {
        String webClientToken = GXCurrentRequestContextUtils.getHeader(GXCommonConstant.X_AUTH_TOKEN);
        if (CharSequenceUtil.isBlank(webClientToken)) {
            return false; // Token为空，直接返回无效
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
     * 获取 Feign token 加解密密钥。
     *
     * @return 密钥字符串
     * @throws GXBusinessException 当未配置密钥时抛出
     */
    default String getAuthTokenSecret() {
        String tokenSecret = GXCommonUtils.getEnvironmentValue("maple.framework.web.feign.secret", String.class);
        if (CharSequenceUtil.isBlank(tokenSecret)) {
            throw new GXBusinessException("请配置maple.framework.web.feign.secret");
        }
        return tokenSecret;
    }

    /**
     * 获取需要在日志中脱敏的请求头字段。
     *
     * @return 需要脱敏的请求头字段名称集合，键为字段名，值为脱敏后的显示值
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
     * 获取当前请求的平台信息。
     *
     * @return 平台标识字符串，可能返回null或空字符串（当请求头未设置时）
     */
    default String getPlatform() {
        return GXCurrentRequestContextUtils.getHeader(GXTokenConstant.PLATFORM);
    }

    /**
     * 生成 HMAC 签名。
     *
     * @param data   需要加密的数据对象
     * @param secret 密钥字符串
     * @return 生成的HMAC加密结果
     * @throws GXBusinessException 当加密过程中发生错误时抛出
     */
    default String generateHmac(Object data, String secret) {
        return GXCommonUtils.generateHmac(data, secret);
    }

    /**
     * 验证 HMAC 签名是否匹配。
     *
     * @param secret     用于生成HMAC的密钥
     * @param clientHmac 客户端提供的HMAC值
     * @param payload    原始数据负载
     * @return boolean true表示验证通过，false表示验证失败
     */
    default boolean checkHmac(String secret, String clientHmac, Object payload) {
        return GXCommonUtils.checkHmac(secret, clientHmac, payload);
    }

    /**
     * 获取当前请求的 traceId，优先级为请求属性、请求头、MDC、自动生成。
     *
     * @return 当前请求的traceId，如果无法获取则返回全局traceId
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
