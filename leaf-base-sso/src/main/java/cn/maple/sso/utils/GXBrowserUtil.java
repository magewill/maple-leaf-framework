package cn.maple.sso.utils;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.SecureUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GXBrowserUtil {
    private static final Logger LOG = LoggerFactory.getLogger(GXBrowserUtil.class);

    private static final String USER_AGENT_HEADER = "user-agent";

    private static final int LEGACY_FINGERPRINT_START_INDEX = 3;

    private static final int LEGACY_FINGERPRINT_END_INDEX = 8;

    private GXBrowserUtil() {
    }

    public static String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            LOG.warn("Cannot get browser fingerprint: request is null.");
            return null;
        }

        String userAgentHeader = request.getHeader(USER_AGENT_HEADER);
        if (CharSequenceUtil.isBlank(userAgentHeader)) {
            LOG.warn("Cannot get browser fingerprint: User-Agent is blank.");
            return null;
        }

        try {
            String userAgentHash = SecureUtil.sha256(userAgentHeader);
            if (CharSequenceUtil.isBlank(userAgentHash)) {
                LOG.warn("Cannot get browser fingerprint: hash result is blank.");
                return null;
            }

            return userAgentHash;
        } catch (Exception e) {
            LOG.error("Generate browser fingerprint failed.", e);
            return null;
        }
    }

    public static boolean isLegalUserAgent(HttpServletRequest request, String userAgent) {
        if (request == null) {
            LOG.warn("Browser fingerprint validation failed: request is null.");
            return false;
        }

        if (CharSequenceUtil.isBlank(userAgent)) {
            LOG.warn("Browser fingerprint validation failed: stored fingerprint is blank.");
            return false;
        }

        String currentUserAgent = getUserAgent(request);
        if (currentUserAgent == null) {
            LOG.warn("Browser fingerprint validation failed: current fingerprint is unavailable.");
            return false;
        }

        boolean isLegal = currentUserAgent.equals(userAgent) || legacyUserAgent(request).equals(userAgent);
        if (!isLegal) {
            LOG.warn("Possible session hijacking detected: browser fingerprint mismatch.");
        }

        return isLegal;
    }

    private static String legacyUserAgent(HttpServletRequest request) {
        String userAgentHeader = request.getHeader(USER_AGENT_HEADER);
        if (CharSequenceUtil.isBlank(userAgentHeader)) {
            return "";
        }
        String userAgentHash = SecureUtil.md5(userAgentHeader);
        if (CharSequenceUtil.isBlank(userAgentHash) || userAgentHash.length() < LEGACY_FINGERPRINT_END_INDEX) {
            return "";
        }
        return userAgentHash.substring(LEGACY_FINGERPRINT_START_INDEX, LEGACY_FINGERPRINT_END_INDEX);
    }
}
