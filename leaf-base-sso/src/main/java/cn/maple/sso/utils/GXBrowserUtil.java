package cn.maple.sso.utils;

import cn.hutool.crypto.SecureUtil;

import jakarta.servlet.http.HttpServletRequest;

/**
 * <p>
 * 验证浏览器基本信息
 * </p>
 * 
 * 用于浏览器指纹识别，通过对User-Agent进行处理生成浏览器标识，
 * 可用于防止会话劫持和跨站请求伪造攻击
 *
 * @author britton birtton@126.com
 * @since 2021-09-16
 */
public class GXBrowserUtil {
    /**
     * 私有构造函数，防止实例化工具类
     */
    private GXBrowserUtil() {
    }

    /**
     * <p>
     * 混淆浏览器版本信息，取 MD5 中间部分字符
     * 获取浏览器客户端信息签名值
     * </p>
     * 
     * 安全说明：
     * 1. 通过MD5哈希算法处理user-agent，增加伪造难度
     * 2. 只取哈希值的部分字符，既保留了标识性又不暴露完整哈希值
     * 3. 用于SSO系统中验证请求的合法性
     *
     * @param request HTTP请求对象
     * @return 获取浏览器客户端信息签名值，长度为5个字符
     */
    public static String getUserAgent(HttpServletRequest request) {
        if (request == null || request.getHeader("user-agent") == null) {
            return null;
        }
        String userAgent = SecureUtil.md5(request.getHeader("user-agent"));
        if (null == userAgent) {
            return null;
        }
        return userAgent.substring(3, 8);
    }

    /**
     * <p>
     * 请求浏览器是否合法 (只校验客户端信息不校验domain)
     * </p>
     *
     * @param request   请求对象
     * @param userAgent 浏览器客户端信息
     * @return boolean
     */
    public static boolean isLegalUserAgent(HttpServletRequest request, String userAgent) {
        String ua = getUserAgent(request);
        if (null == ua) {
            return false;
        }
        return ua.equals(userAgent);
    }
}
