package cn.maple.sso.utils;

import cn.hutool.core.text.CharSequenceUtil;
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
            log.error(e.getMessage());
        } finally {
            LOCAL_IP = ip.toString();
        }
    }

    private GXIpHelperUtil() {
    }

    public static String getIpAddr(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
            if (ip.equals("127.0.0.1")) {
                try {
                    ip = InetAddress.getLocalHost().getHostAddress();
                } catch (UnknownHostException e) {
                    log.error("IpHelper error.{}", e.getMessage());
                }
            }
        }
        if (ip != null && ip.length() > 15 && ip.indexOf(",") > 1) {
            ip = ip.substring(0, ip.indexOf(","));
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
                log.error("判断本地IP时发生异常: {}", e.getMessage());
            }
        }
        return false;
    }
}
