package cn.maple.sso.utils;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.sso.properties.GXSSOProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class GXIpHelperUtil {
    /**
     * Local address resolved once at class initialisation.
     *
     * <p>Immutable on purpose: a mutable public field could be rewritten by any code and would
     * silently change the address recorded into the login token.</p>
     */
    public static final String LOCAL_IP;

    /**
     * Local host name resolved once at class initialisation.
     */
    public static final String HOST_NAME;

    /**
     * Every local address resolved once at class initialisation, so {@link #isLocalIp(String)}
     * never performs a host name lookup on the request hot path.
     */
    private static final Set<String> LOCAL_ADDRESSES;

    static {
        String resolvedIp = "unknown";
        String resolvedHostName = "";
        Set<String> addresses = new HashSet<>();
        try {
            InetAddress inetAddr = InetAddress.getLocalHost();
            resolvedHostName = inetAddr.getHostName();
            resolvedIp = formatAddress(inetAddr.getAddress());
            addresses.add(resolvedIp);
            for (InetAddress inetAddress : InetAddress.getAllByName(resolvedHostName)) {
                addresses.add(inetAddress.getHostAddress());
            }
        } catch (UnknownHostException e) {
            log.error("Resolve local host failed: {}", e.getMessage());
        }
        LOCAL_IP = resolvedIp;
        HOST_NAME = resolvedHostName;
        LOCAL_ADDRESSES = Set.copyOf(addresses);
    }

    private GXIpHelperUtil() {
    }

    private static String formatAddress(byte[] addr) {
        StringBuilder ip = new StringBuilder();
        for (int i = 0; i < addr.length; i++) {
            if (i > 0) {
                ip.append(".");
            }
            ip.append(addr[i] & 0xFF);
        }
        return ip.toString();
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
            if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
                // Reuse the address resolved once at class initialisation: resolving the local
                // host on every request performs a host name lookup on the hot path.
                ip = LOCAL_IP;
            }
        }
        return ip;
    }

    /**
     * Whether the given address belongs to the local host.
     *
     * <p>Uses the address set resolved once at class initialisation instead of resolving the
     * local host name on every call (T051).</p>
     *
     * @param ip address to check, may be blank
     * @return {@code true} when the address is a local address
     */
    public static boolean isLocalIp(String ip) {
        return CharSequenceUtil.isNotEmpty(ip) && LOCAL_ADDRESSES.contains(ip);
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
