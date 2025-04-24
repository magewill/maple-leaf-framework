package cn.maple.sso.utils;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.SecureUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * 浏览器信息验证工具类
 * </p>
 * 
 * <p>
 * 用于浏览器指纹识别，通过对User-Agent进行处理生成浏览器标识，
 * 可用于防止会话劫持和跨站请求伪造攻击。主要功能包括：
 * 1. 生成浏览器指纹：基于User-Agent生成唯一标识
 * 2. 验证浏览器合法性：比对当前请求与已存储的浏览器标识
 * </p>
 * 
 * <p>
 * 安全特性：
 * - 使用MD5哈希算法处理User-Agent，增加伪造难度
 * - 只取哈希值的部分字符，既保留标识性又不暴露完整哈希值
 * - 在SSO系统中与IP验证结合使用，提供双重安全保障
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 用户登录时，获取并存储浏览器标识
 * String browserFingerprint = GXBrowserUtil.getUserAgent(request);
 * userSession.setBrowserFingerprint(browserFingerprint);
 * 
 * // 2. 后续请求验证浏览器标识是否一致
 * boolean isLegal = GXBrowserUtil.isLegalUserAgent(request, userSession.getBrowserFingerprint());
 * if (!isLegal) {
 *     // 可能存在会话劫持，执行安全措施
 *     securityService.handlePotentialHijacking(request);
 * }
 * </pre>
 * </p>
 * 
 * @author britton birtton@126.com
 * @since 2021-09-16
 */
public class GXBrowserUtil {
    /**
     * 日志对象
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXBrowserUtil.class);
    
    /**
     * User-Agent请求头名称常量
     */
    private static final String USER_AGENT_HEADER = "user-agent";
    
    /**
     * 浏览器指纹截取起始位置
     */
    private static final int FINGERPRINT_START_INDEX = 3;
    
    /**
     * 浏览器指纹截取结束位置
     */
    private static final int FINGERPRINT_END_INDEX = 8;

    /**
     * 私有构造函数，防止实例化工具类
     */
    private GXBrowserUtil() {
        // 工具类不应被实例化
    }

    /**
     * <p>
     * 获取浏览器指纹标识
     * </p>
     * 
     * <p>
     * 通过对User-Agent进行MD5哈希处理并截取部分字符，生成浏览器的唯一标识。
     * 该标识可用于验证请求的一致性，防止会话劫持攻击。
     * </p>
     * 
     * <p>
     * 安全说明：
     * 1. 通过MD5哈希算法处理user-agent，增加伪造难度
     * 2. 只取哈希值的部分字符(第3-8位)，既保留了标识性又不暴露完整哈希值
     * 3. 用于SSO系统中验证请求的合法性，与IP验证结合使用效果更佳
     * </p>
     * 
     * <p>
     * 性能优化：
     * - 使用常量定义截取位置，便于统一调整
     * - 增加空值检查和日志记录，提高代码健壮性
     * </p>
     *
     * @param request HTTP请求对象，不能为null
     * @return 获取浏览器客户端信息签名值，长度为5个字符；如果请求为null或不包含user-agent则返回null
     */
    public static String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            LOG.warn("无法获取浏览器指纹：请求对象为null");
            return null;
        }
        
        String userAgentHeader = request.getHeader(USER_AGENT_HEADER);
        if (CharSequenceUtil.isBlank(userAgentHeader)) {
            LOG.warn("无法获取浏览器指纹：User-Agent为空");
            return null;
        }
        
        try {
            String userAgentHash = SecureUtil.md5(userAgentHeader);
            if (CharSequenceUtil.isBlank(userAgentHash)) {
                LOG.warn("生成浏览器指纹失败：MD5哈希结果为空");
                return null;
            }
            
            // 截取哈希值的一部分作为浏览器指纹
            return userAgentHash.substring(FINGERPRINT_START_INDEX, FINGERPRINT_END_INDEX);
        } catch (Exception e) {
            LOG.error("生成浏览器指纹时发生异常", e);
            return null;
        }
    }

    /**
     * <p>
     * 验证请求浏览器是否合法
     * </p>
     * 
     * <p>
     * 通过比对当前请求的浏览器指纹与已存储的指纹，判断请求是否来自同一浏览器。
     * 这是防止会话劫持的重要手段，特别是在敏感操作和身份验证场景中。
     * </p>
     * 
     * <p>
     * 验证流程：
     * 1. 获取当前请求的浏览器指纹
     * 2. 与存储的指纹进行比对
     * 3. 返回比对结果
     * </p>
     * 
     * <p>
     * 安全建议：
     * - 与IP验证结合使用，提供双重安全保障
     * - 在敏感操作（如支付、密码修改）时强制验证
     * - 考虑浏览器升级场景，可实现降级策略
     * </p>
     *
     * @param request   请求对象，不能为null
     * @param userAgent 已存储的浏览器指纹，通常在用户登录时获取并存储
     * @return 如果浏览器指纹一致返回true，否则返回false
     */
    public static boolean isLegalUserAgent(HttpServletRequest request, String userAgent) {
        if (request == null) {
            LOG.warn("浏览器合法性验证失败：请求对象为null");
            return false;
        }
        
        if (CharSequenceUtil.isBlank(userAgent)) {
            LOG.warn("浏览器合法性验证失败：存储的浏览器指纹为空");
            return false;
        }
        
        String currentUserAgent = getUserAgent(request);
        if (currentUserAgent == null) {
            LOG.warn("浏览器合法性验证失败：无法获取当前请求的浏览器指纹");
            return false;
        }
        
        boolean isLegal = currentUserAgent.equals(userAgent);
        if (!isLegal) {
            LOG.warn("检测到可能的会话劫持：浏览器指纹不匹配");
        }
        
        return isLegal;
    }
}
