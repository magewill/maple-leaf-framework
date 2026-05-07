package cn.maple.sso.utils;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.sso.properties.GXSSOProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
public class GXIpHelperUtil {
    public static String LOCAL_IP;

    public static String HOST_NAME;

    static {
        StringBuilder ip = new StringBuilder();
        try {
            InetAddress inetAddr = InetAddress.getLocalHost();
            HOST_NAME = inetAddr.getHostName();
            byte[] addr = inetAddr.getAddress();
            for (int i = 0; i < addr.length; i++) {
                if (i > 0) {
                    ip.append(".");
                }
                ip.append(addr[i] & 0xFF);
            }
        } catch (UnknownHostException e) {
            ip = new StringBuilder("unknown");
            log.error("Resolve local host failed: {}", e.getMessage());
        } finally {
            LOCAL_IP = ip.toString();
        }
    }

    private GXIpHelperUtil() {
    }

    public static String getIpAddr(HttpServletRequest request) {
        if (request == null) {
            return "";
        }

        String ip = null;
        if (GXSSOProperties.getInstance().isTrustForwardedIpHeaders()) {
            ip = getForwardedIp(request);
        }

        if (isUnknown(ip)) {
            ip = request.getRemoteAddr();
            if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
                try {
                    ip = InetAddress.getLocalHost().getHostAddress();
                } catch (UnknownHostException e) {
                    log.error("Resolve local request IP failed: {}", e.getMessage());
                }
            }
        }
        return ip;
    }

    public static boolean isLocalIp(String ip) {
        if (CharSequenceUtil.isNotEmpty(ip)) {
            try {
                InetAddress inetAddress = InetAddress.getLocalHost();
                InetAddress[] ias = InetAddress.getAllByName(inetAddress.getHostName());
                for (InetAddress ia : ias) {
                    if (ip.equals(ia.getHostAddress())) {
                        return true;
                    }
                }
            } catch (UnknownHostException e) {
                log.error("Check local IP failed: {}", e.getMessage());
            }
        }
        return false;
    }

    private static String getForwardedIp(HttpServletRequest request) {
        String ip = firstKnownHeader(request, "x-forwarded-for");
        if (isUnknown(ip)) {
            ip = firstKnownHeader(request, "Proxy-Client-IP");
        }
        if (isUnknown(ip)) {
            ip = firstKnownHeader(request, "WL-Proxy-Client-IP");
        }
        return ip;
    }

    private static String firstKnownHeader(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (isUnknown(value)) {
            return null;
        }
        int commaIndex = value.indexOf(",");
        if (commaIndex > -1) {
            return value.substring(0, commaIndex).trim();
        }
        return value.trim();
    }

    private static boolean isUnknown(String ip) {
        return CharSequenceUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip);
    }
}
