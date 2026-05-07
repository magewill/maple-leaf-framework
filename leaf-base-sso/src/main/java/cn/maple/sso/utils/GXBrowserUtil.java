package cn.maple.sso.utils;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.SecureUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GXBrowserUtil {
    private static final Logger LOG = LoggerFactory.getLogger(GXBrowserUtil.class);

    private static final String USER_AGENT_HEADER = "user-agent";

    private static final int FINGERPRINT_START_INDEX = 3;

    private static final int FINGERPRINT_END_INDEX = 8;

    private GXBrowserUtil() {
    }

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

            return userAgentHash.substring(FINGERPRINT_START_INDEX, FINGERPRINT_END_INDEX);
        } catch (Exception e) {
            LOG.error("生成浏览器指纹时发生异常", e);
            return null;
        }
    }

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
