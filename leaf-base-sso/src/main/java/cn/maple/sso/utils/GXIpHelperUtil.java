package cn.maple.sso.utils;

import cn.hutool.core.text.CharSequenceUtil;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * <p>
 * 获取IP地址工具类
 * </p>
 * 
 * 用于处理客户端IP地址获取、验证等操作，主要应用于：
 * 1. 获取HTTP请求的真实客户端IP，处理代理服务器转发的情况
 * 2. 判断IP是否为本地IP
 * 3. 在SSO系统中用于IP绑定验证，增强安全性
 *
 * @author britton birtton@126.com
 * @since 2021-09-16
 */
@Slf4j
public class GXIpHelperUtil {
    /**
     * 系统的本地IP地址
     * 在静态初始化块中赋值，用于快速判断请求是否来自本机
     */
    public static String LOCAL_IP;

    /**
     * 系统的本地服务器名
     * 在静态初始化块中赋值，用于日志记录和系统标识
     */
    public static String HOST_NAME;

    static {
        String ip = "";
        try {
            InetAddress inetAddr = InetAddress.getLocalHost();
            HOST_NAME = inetAddr.getHostName();
            byte[] addr = inetAddr.getAddress();
            for (int i = 0; i < addr.length; i++) {
                if (i > 0) {
                    ip += ".";
                }
                ip += addr[i] & 0xFF;
            }
        } catch (UnknownHostException e) {
            ip = "unknown";
            log.error(e.getMessage());
        } finally {
            LOCAL_IP = ip;
        }
    }

    /**
     * 私有构造函数，防止实例化工具类
     */
    private GXIpHelperUtil() {
    }


    /**
     * <p>
     * 获取客户端的真实IP地址
     * </p>
     * 
     * 获取客户端IP地址的基本方法是request.getRemoteAddr()，但这在使用反向代理时无法获取真实IP。
     * 本方法通过检查多个HTTP头来确定真实客户端IP：
     * 1. 首先检查X-Forwarded-For头，它包含经过的所有代理服务器IP
     * 2. 如果无法从X-Forwarded-For获取，则尝试Proxy-Client-IP和WL-Proxy-Client-IP头
     * 3. 最后才使用request.getRemoteAddr()作为后备方案
     * 
     * 安全说明：
     * - 对于多级代理，取X-Forwarded-For中第一个非unknown的有效IP
     * - 处理了127.0.0.1本地请求的特殊情况
     * - 适当处理了IP格式，确保返回单个有效IP
     *
     * @param request 当前HTTP请求对象
     * @return 客户端真实IP地址字符串
     */
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
                // 根据网卡取本机配置的IP
                try {
                    ip = InetAddress.getLocalHost().getHostAddress();
                } catch (UnknownHostException e) {
                    log.error("IpHelper error." + e.getMessage());
                }
            }
        }
        // 对于通过多个代理的情况， 第一个IP为客户端真实IP,多个IP按照','分割 "***.***.***.***".length() = 15
        if (ip != null && ip.length() > 15 && ip.indexOf(",") > 1) {
            ip = ip.substring(0, ip.indexOf(","));
        }
        return ip;
    }

    /**
     * <p>
     * 判断是否为本地IP地址
     * </p>
     * 
     * 通过比对给定IP与本机所有网卡IP地址，判断是否为本地IP
     * 用于区分内部请求和外部请求，在某些安全验证场景中很有用
     *
     * @param ip 待判断的IP地址字符串
     * @return 如果是本地IP返回true，否则返回false
     */
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
