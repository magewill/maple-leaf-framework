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
 * Feign服务接口
 * <p>
 * 该接口提供了Feign客户端调用所需的各种工具方法，包括认证令牌生成与验证、请求头处理、
 * HMAC签名生成与验证等功能。接口的默认实现适用于大多数场景，同时允许通过继承进行定制。
 * </p>
 *
 * <p>
 * 主要功能：
 * 1. HTTP认证令牌的生成与验证
 * 2. 请求头信息的处理与传递
 * 3. HMAC签名的生成与验证
 * 4. 分布式追踪ID的获取与传递
 * 5. 平台标识的获取与传递
 * </p>
 *
 * <p>
 * 使用示例：
 * 1. 生成认证令牌：
 * ```java
 *
 * @author maple
 * @Autowired private GXFeignService feignService;
 * <p>
 * public void example() {
 * String token = feignService.generateHttpAuthToken();
 * // 使用token进行API调用
 * }
 * ```
 * <p>
 * 2. 验证令牌有效性：
 * ```java
 * @Autowired private GXFeignService feignService;
 * <p>
 * public void validateToken() {
 * boolean isValid = feignService.checkTokenValidity();
 * if (!isValid) {
 * throw new GXBusinessException("无效的认证令牌");
 * }
 * }
 * ```
 * </p>
 *
 * <p>
 * 配置要求：
 * 使用默认实现时，需要在配置文件中设置以下属性：
 * - maple.framework.web.client.token: 用于生成令牌的基础字符串
 * - maple.framework.web.client.secret: 用于令牌加密的密钥
 * </p>
 */
public interface GXFeignService {
    /**
     * 生成HTTP客户端认证令牌
     * <p>
     * 该方法返回用于HTTP请求认证的Token值，通常会被设置在请求头中。
     * 默认实现从环境配置中获取基础令牌源，并使用默认过期时间生成Token。
     * 实现了令牌缓存机制，避免频繁生成新令牌，提高性能。
     * </p>
     * <p>
     * 实现类可以覆盖此方法，提供更高效的Token生成和缓存策略，例如：
     * 1. 使用本地缓存存储Token，避免频繁生成
     * 2. 实现Token预生成机制，在当前Token即将过期前提前生成新Token
     * 3. 使用分布式缓存在多实例环境中共享Token
     * </p>
     *
     * @return 生成的认证令牌字符串
     * @throws GXBusinessException 当未配置基础令牌源时抛出
     */
    default String generateHttpAuthToken() {
        String tokenSource = GXCommonUtils.getEnvironmentValue("maple.framework.web.client.token", String.class);
        // 校验基础令牌配置有效性，为空时抛出业务异常
        if (CharSequenceUtil.isBlank(tokenSource)) {
            throw new GXBusinessException("请配置maple.framework.web.client.token");
        }
        return generateHttpAuthToken(tokenSource, GXTokenConstant.WEB_CLIENT_TOKEN_EXPIRE);
    }

    /**
     * 生成Feign调用的HTTP请求认证Token
     * <p>
     * 该方法根据提供的源字符串和过期时间生成认证Token。
     * 默认实现使用GXAuthCodeUtils进行Token的编码生成。
     * </p>
     * <p>
     * 实现注意事项：
     * <ul>
     *   <li>Token生成应考虑性能影响，避免每次调用都重新生成</li>
     *   <li>对于有过期时间的Token，应实现自动刷新机制</li>
     *   <li>应处理Token获取失败的情况，提供合理的降级策略</li>
     *   <li>可以考虑使用缓存机制存储Token，提高性能</li>
     * </ul>
     * </p>
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
     * 检查Feign的Token是否有效
     * <p>
     * 该方法从当前请求上下文中获取Feign认证Token，并验证其有效性。
     * 默认实现使用GXAuthCodeUtils进行Token的解码验证。
     * </p>
     * <p>
     * 实现类可以覆盖此方法，提供更复杂的Token验证逻辑，例如：
     * 1. 验证Token的签发时间和过期时间
     * 2. 检查Token是否在黑名单中（已被撤销）
     * 3. 验证Token的签名是否有效
     * 4. 检查Token中的用户信息是否与当前请求匹配
     * </p>
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
            // Token解码过程中发生异常，视为无效
            return false;
        }
    }

    /**
     * 获取Token的密钥
     * <p>
     * 该方法从环境配置中获取用于Token加密和解密的密钥。
     * 默认实现从环境变量maple.framework.web.client.secret中获取密钥。
     * </p>
     * <p>
     * 实现类可以覆盖此方法，提供更安全的密钥管理策略，例如：
     * 1. 从安全的密钥管理系统获取密钥
     * 2. 使用环境变量或配置中心存储加密后的密钥，运行时解密
     * 3. 实现密钥轮换机制，定期更新密钥
     * </p>
     *
     * @return 密钥字符串
     * @throws GXBusinessException 当未配置密钥时抛出
     */
    default String getAuthTokenSecret() {
        String tokenSecret = GXCommonUtils.getEnvironmentValue("maple.framework.web.client.secret", String.class);
        if (CharSequenceUtil.isBlank(tokenSecret)) {
            throw new GXBusinessException("请配置maple.framework.web.client.secret");
        }
        return tokenSecret;
    }

    /**
     * 获取需要脱敏的请求头字段集合
     * <p>
     * 该方法返回需要在日志记录中进行脱敏处理的请求头字段名称集合。
     * 默认实现包含常见的敏感字段，如Authorization、Cookie等。
     * 实现类可以覆盖此方法，根据实际需求添加或移除字段。
     * </p>
     *
     * @return 需要脱敏的请求头字段名称集合，键为字段名，值为脱敏后的显示值
     */
    default Map<String, String> getSensitiveHeaderFields() {
        // 返回默认的敏感字段映射，键为字段名，值为脱敏后的显示值
        return Map.of(
                "Authorization", "******",
                "Cookie", "******",
                "X-Auth-Token", "******",
                "X-HMAC-Signature", "******",
                "X-API-Key", "******"
        );
    }

    /**
     * 获取当前请求的平台信息
     * <p>
     * 从HTTP请求头中提取平台标识字段，用于标识请求来源的平台类型。
     * 该信息通常用于在微服务间传递，以便服务端根据不同平台来源进行差异化处理。
     * </p>
     *
     * @return 平台标识字符串，可能返回null或空字符串（当请求头未设置时）
     * 典型值如：WEB/APP/ADMIN等
     */
    default String getPlatform() {
        return GXCurrentRequestContextUtils.getHeader(GXTokenConstant.PLATFORM);
    }

    /**
     * 生成HMAC加密字符串
     * <p>
     * 使用指定的密钥对数据进行HMAC加密，生成签名字符串。
     * 该方法通常用于API调用的签名验证，确保请求的完整性和真实性。
     * </p>
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
     * 验证HMAC签名是否匹配
     * <p>
     * 使用指定的密钥和原始数据验证客户端提供的HMAC签名是否有效。
     * 该方法通常用于验证API请求的签名，防止请求被篡改。
     * </p>
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
     * 获取当前请求的追踪ID(traceId)
     * <p>
     * 该方法返回用于分布式追踪的唯一标识符，便于跨服务调用时关联日志和监控信息。
     * 优先从当前HTTP请求属性中获取，若不存在则从全局上下文中获取。
     * </p>
     *
     * @return 当前请求的traceId，如果无法获取则返回全局traceId
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
}
