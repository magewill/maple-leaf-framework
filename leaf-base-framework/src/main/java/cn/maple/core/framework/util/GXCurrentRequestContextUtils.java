package cn.maple.core.framework.util;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GXCurrentRequestContextUtils {
    private static final Logger LOG = LoggerFactory.getLogger(GXCurrentRequestContextUtils.class);

    private static final int MAX_INPUT_LENGTH = 1024 * 1024;

    private static final Set<String> ALLOWED_TAGS = new HashSet<>(Arrays.asList(
            "p", "br", "b", "i", "u", "strong", "em", "span", "div",
            "ul", "ol", "li", "table", "tr", "td", "th", "thead", "tbody",
            "h1", "h2", "h3", "h4", "h5", "h6", "a", "img"
    ));

    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "href", "src", "alt", "title", "style", "class", "id", "align", "width", "height"
    ));

    private static final Pattern[] XSS_PATTERNS = {
            Pattern.compile("<\\s*script\\b[^>]*>(.*?)<\\s*/\\s*script\\s*>", Pattern.CASE_INSENSITIVE),
            // 事件属性注入（如 onclick、onerror 等）
            Pattern.compile("on\\w+\\s*=\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on\\w+\\s*=\\s*'[^']*'", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on\\w+\\s*=\\s*`[^`]*`", Pattern.CASE_INSENSITIVE), // 反引号形式
            // 危险协议（如 javascript:、vbscript:、data:）
            Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("vbscript\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("data\\s*:[^\\s]*,", Pattern.CASE_INSENSITIVE),
            // 危险标签（如 iframe、frame、object、embed）
            Pattern.compile("<\\s*iframe[^>]*>(.*?)<\\s*/\\s*iframe\\s*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\s*frame[^>]*>(.*?)<\\s*/\\s*frame\\s*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\s*object[^>]*>(.*?)<\\s*/\\s*object\\s*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\s*embed[^>]*>(.*?)<\\s*/\\s*embed\\s*>", Pattern.CASE_INSENSITIVE),
            // 危险函数（如 eval、Function、expression）
            Pattern.compile("eval\\((.*?)\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Function\\((.*?)\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("expression\\((.*?)\\)", Pattern.CASE_INSENSITIVE),
            // HTML 实体编码绕过（如 &#x3C; 表示 <）
            Pattern.compile("&#x[0-9a-fA-F]{2,4};?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("&#[0-9]{2,4};?", Pattern.CASE_INSENSITIVE),
            // SVG 相关攻击（如 SVG onload）
            Pattern.compile("<\\s*svg[^>]*>.*?<\\s*/\\s*svg\\s*>", Pattern.CASE_INSENSITIVE),
            // img 标签的 onerror 事件注入
            Pattern.compile("<\\s*img[^>]*\\bonerror\\s*=[^>]*>", Pattern.CASE_INSENSITIVE),
            // meta 标签（可能用于重定向攻击）
            Pattern.compile("<\\s*meta[^>]*>", Pattern.CASE_INSENSITIVE),
            // CSS 注入（如 style 属性中的 expression）
            Pattern.compile("style\\s*=\\s*['\"][^'\"]*expression\\([^'\"]*\\)[^'\"]*['\"]", Pattern.CASE_INSENSITIVE),
            // 注释绕过（如 <!-- --> 包含脚本）
            Pattern.compile("<!--.*?-->", Pattern.DOTALL)
    };

    // HTML 标签清理正则表达式，用于提取和验证标签
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    // HTML 属性清理正则表达式，用于提取和验证属性
    private static final Pattern HTML_ATTRIBUTE_PATTERN = Pattern.compile("\\b([a-zA-Z][a-zA-Z0-9]*)\\s*=\\s*['\"][^'\"]*['\"]", Pattern.CASE_INSENSITIVE);

    // 最大输入长度，防止超长输入导致性能问题
    private static final int MAX_IP_LENGTH = 45; // IPv6 地址最大长度（包括冒号和压缩格式）

    // IPv4 地址的正则表达式（预编译以提高性能）
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}" +
                    "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$"
    );

    // IPv6 地址的正则表达式（预编译以提高性能）
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^(([0-9a-fA-F]{1,4}:){7}([0-9a-fA-F]{1,4}|:))|" +
                    "(([0-9a-fA-F]{1,4}:){6}(:[0-9a-fA-F]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)" +
                    "(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|" +
                    "(([0-9a-fA-F]{1,4}:){5}(((:[0-9a-fA-F]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)" +
                    "(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|" +
                    "(([0-9a-fA-F]{1,4}:){4}(((:[0-9a-fA-F]{1,4}){1,3})|((:[0-9a-fA-F]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)" +
                    "(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|" +
                    "(([0-9a-fA-F]{1,4}:){3}(((:[0-9a-fA-F]{1,4}){1,4})|((:[0-9a-fA-F]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)" +
                    "(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|" +
                    "(([0-9a-fA-F]{1,4}:){2}(((:[0-9a-fA-F]{1,4}){1,5})|((:[0-9a-fA-F]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)" +
                    "(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|" +
                    "(([0-9a-fA-F]{1,4}:){1}(((:[0-9a-fA-F]{1,4}){1,6})|((:[0-9a-fA-F]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)" +
                    "(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|" +
                    "(:(((:[0-9a-fA-F]{1,4}){1,7})|((:[0-9a-fA-F]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)" +
                    "(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))$"
    );

    /**
     * 请求频率限制配置
     */
    private static final int MAX_REQUESTS_PER_MINUTE = 1000;
    private static final Map<String, List<Long>> requestTimestamps = new ConcurrentHashMap<>();
    private static final long CLEANUP_INTERVAL = 60000; // 1分钟清理一次过期记录
    private static long lastCleanupTime = System.currentTimeMillis();

    private GXCurrentRequestContextUtils() {
    }

    public static HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (Objects.nonNull(requestAttributes)) {
            return requestAttributes.getRequest();
        }
        return null;
    }

    public static HttpServletResponse getHttpServletResponse() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (Objects.nonNull(requestAttributes)) {
            return requestAttributes.getResponse();
        }
        return null;
    }

    public static String getDomain() {
        HttpServletRequest request = Objects.requireNonNull(getHttpServletRequest(), "当前线程不是Web请求线程，无法获取请求域名");
        StringBuffer url = request.getRequestURL();
        return url.delete(url.length() - request.getRequestURI().length(), url.length()).toString();
    }

    public static String getOrigin() {
        HttpServletRequest request = Objects.requireNonNull(getHttpServletRequest(), "当前线程不是Web请求线程，无法获取Origin");
        return request.getHeader("Origin");
    }

    public static <T> T getHttpParam(String paramName, Class<T> clazz) {
        if (CharSequenceUtil.isBlank(paramName)) {
            LOG.warn("参数名不能为空");
            return GXCommonUtils.getClassDefaultValue(clazz);
        }

        final HttpServletRequest httpServletRequest = getHttpServletRequest();
        if (null == httpServletRequest) {
            LOG.debug("当前不在HTTP请求上下文中，无法获取请求参数");
            return GXCommonUtils.getClassDefaultValue(clazz);
        }

        Object jsonRequestBody = Optional.ofNullable(httpServletRequest.getAttribute("JSON_REQUEST_BODY")).orElse("{}");
        final JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.toBean(jsonRequestBody.toString(), JSONObject.class);
        } catch (Exception e) {
            LOG.debug("解析JSON请求体失败: {}", e.getMessage());
            return getParamFromRequestOrAttribute(httpServletRequest, paramName, clazz);
        }

        if (!jsonObject.isEmpty()) {
            try {
                T value = jsonObject.getByPath(paramName, clazz);
                if (null != value) {
                    return value;
                }
            } catch (Exception e) {
                LOG.debug("从JSON请求体获取参数{}失败: {}", paramName, e.getMessage());
            }
        }

        return getParamFromRequestOrAttribute(httpServletRequest, paramName, clazz);
    }

    private static <T> T getParamFromRequestOrAttribute(HttpServletRequest request, String paramName, Class<T> clazz) {
        String requestParameter = request.getParameter(paramName);

        if (null == requestParameter) {
            final Object requestAttribute = request.getAttribute(paramName);
            if (null != requestAttribute) {
                requestParameter = requestAttribute.toString();
            }
        }

        T obj = Convert.convert(clazz, requestParameter);

        if (null == obj) {
            return GXCommonUtils.getClassDefaultValue(clazz);
        }

        return obj;
    }

    public static String getHeader(String headerName) {
        if (CharSequenceUtil.isBlank(headerName)) {
            LOG.warn("请求头名称不能为空");
            return null;
        }

        HttpServletRequest request = getHttpServletRequest();
        if (Objects.isNull(request)) {
            LOG.debug("本次请求是RPC调用,没有HttpServletRequest对象");
            return null;
        }
        return JakartaServletUtil.getHeader(request, headerName, CharsetUtil.UTF_8);
    }

    public static Dict getAllHeaders() {
        HttpServletRequest request = getHttpServletRequest();
        if (Objects.isNull(request)) {
            LOG.debug("本次请求是RPC调用,没有HttpServletRequest对象");
            return Dict.create();
        }

        Dict headers = Dict.create();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.set(headerName, request.getHeader(headerName));
        }

        return headers;
    }

    public static <T> T getHeader(String headerName, Class<T> targetClass) {
        return getHeader(headerName, targetClass, null);
    }

    public static <T> T getHeader(String headerName, Class<T> targetClass, T defaultValue) {
        String headerValue = getHeader(headerName);
        if (Objects.isNull(headerValue)) {
            return defaultValue;
        }
        return Convert.convert(targetClass, headerValue);
    }

    public static <R> R getLoginFieldFromToken(String tokenName, String tokenFieldName, Class<R> clazz, String secretKey) {
        Object attribute = getHttpServletRequestAttribute(tokenFieldName, clazz);
        if (Objects.nonNull(attribute)) {
            return Convert.convert(clazz, attribute);
        }
        if (Objects.isNull(secretKey)) {
            secretKey = GXTokenConstant.USER_TOKEN_SECRET_KEY;
        }
        Dict dict = getLoginCredentials(tokenName, secretKey);
        Object retValue = dict.getObj(tokenFieldName);
        if (Objects.nonNull(retValue)) {
            return Convert.convert(clazz, retValue);
        }
        return null;
    }

    public static Dict getLoginCredentials(String tokenName, String secretKey) {
        if (isRPC()) {
            return Dict.create();
        }
        HttpServletRequest request = getHttpServletRequest();
        assert request != null;
        Object attribute = request.getAttribute(GXCommonConstant.SSO_TOKEN_ATTR);
        if (Objects.nonNull(attribute)) {
            Dict tokenDict = (Dict) attribute;
            if (!tokenDict.isEmpty()) {
                return tokenDict;
            }
        }
        String token = getHeader(tokenName);
        if (CharSequenceUtil.isBlank(token)) {
            LOG.error("{}不存在", tokenName);
            return Dict.create();
        }
        if (!GXCommonUtils.isBase64(token)) {
            LOG.error("token不是一个有效的base64字符串!");
            return Dict.create();
        }
        String s = GXAuthCodeUtils.authCodeDecode(token, secretKey);
        if (CharSequenceUtil.equalsIgnoreCase("{}", s)) {
            return Dict.create();
        }
        return JSONUtil.toBean(s, Dict.class);
    }

    public static String getClientIP(HttpServletRequest httpServletRequest) {
        if (null == httpServletRequest) {
            return "";
        }

        String[] headerNames = {
                "X-Forwarded-For",       // 最常见的代理IP头
                "CF-Connecting-IP",      // Cloudflare特有的头
                "X-Real-IP",             // Nginx代理常用头
                "Proxy-Client-IP",       // Apache HTTP Server代理使用
                "WL-Proxy-Client-IP",    // WebLogic代理使用
                "HTTP_CLIENT_IP",        // 一些代理服务器使用
                "HTTP_X_FORWARDED_FOR",  // 一些代理服务器使用
                "X-Cluster-Client-IP",   // 负载均衡场景使用
                "Fastly-Client-IP",      // Fastly CDN使用
                "True-Client-IP"         // Akamai和Cloudflare使用
        };

        String ip;
        for (String headerName : headerNames) {
            ip = httpServletRequest.getHeader(headerName);
            if (!CharSequenceUtil.isBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                if (isValidIP(ip)) {
                    LOG.debug("从{}头获取到客户端IP: {}", headerName, ip);
                    return ip;
                }
            }
        }

        ip = JakartaServletUtil.getClientIP(httpServletRequest);
        if (!CharSequenceUtil.isBlank(ip)) {
            LOG.debug("从JakartaServletUtil获取到客户端IP: {}", ip);
        }
        return CharSequenceUtil.isBlank(ip) ? "" : ip;
    }

    public static String getClientIP() {
        HttpServletRequest httpServletRequest = GXCurrentRequestContextUtils.getHttpServletRequest();
        if (Objects.nonNull(httpServletRequest)) {
            return getClientIP(httpServletRequest);
        }
        return "";
    }

    public static boolean isIPv4(String ip) {
        if (CharSequenceUtil.isBlank(ip) || ip.length() > MAX_IP_LENGTH) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches();
    }

    public static boolean isIPv6(String ip) {
        if (CharSequenceUtil.isBlank(ip) || ip.length() > MAX_IP_LENGTH) {
            return false;
        }
        return IPV6_PATTERN.matcher(ip).matches();
    }

    public static String getIPAddressType(String ip) {
        if (!isValidIP(ip)) {
            return "INVALID";
        }

        try {
            InetAddress inetAddress = InetAddress.getByName(ip);

            if (inetAddress.isLoopbackAddress()) {
                return "LOOPBACK";
            }

            if (isInternalIP(ip)) {
                return "PRIVATE";
            }

            if (inetAddress instanceof java.net.Inet6Address) {
                return "IPv6";
            }

            if (inetAddress instanceof java.net.Inet4Address) {
                return "IPv4";
            }

            return "UNKNOWN";
        } catch (UnknownHostException e) {
            LOG.debug("获取IP地址类型时发生异常: {}", ip, e);
            return "INVALID";
        }
    }

    public static HttpServletRequest setHttpServletRequestAttribute(String attributeName, String attributeValue) {
        final HttpServletRequest servletRequest = getHttpServletRequest();
        assert servletRequest != null;
        servletRequest.setAttribute(attributeName, attributeValue);
        return servletRequest;
    }

    public static <T> T getHttpServletRequestAttribute(String attributeName, Class<T> clazz) {
        final HttpServletRequest servletRequest = Objects.requireNonNull(getHttpServletRequest());
        final Object attributeValue = servletRequest.getAttribute(attributeName);
        if (null == attributeValue) {
            return null;
        }
        return Convert.convert(clazz, attributeValue);
    }

    public static boolean isRPC() {
        HttpServletRequest request = getHttpServletRequest();
        return Objects.isNull(request);
    }

    public static boolean isHTTP() {
        HttpServletRequest request = getHttpServletRequest();
        return Objects.nonNull(request);
    }

    public static boolean tokenExists() {
        if (isHTTP()) {
            HttpServletRequest httpServletRequest = getHttpServletRequest();
            assert httpServletRequest != null;
            String header = JakartaServletUtil.getHeader(httpServletRequest, GXTokenConstant.TOKEN_NAME, StandardCharsets.UTF_8);
            return Objects.nonNull(header);
        }
        return false;
    }

    public static boolean isInternalV4IP(byte[] ip) {
        if (ip == null || ip.length != 4) {
            throw new GXBusinessException("非法的IPv4地址字节数组", HttpStatus.HTTP_INTERNAL_ERROR);
        }

        // 检查是否是本地回环地址 (127.0.0.0/8)
        if (ip[0] == (byte) 127) {
            return true;
        }

        // 检查私有地址范围
        // 10.0.0.0 - 10.255.255.255 (10/8 前缀)
        if (ip[0] == (byte) 10) {
            return true;
        }

        // 172.16.0.0 - 172.31.255.255 (172.16/12 前缀)
        if (ip[0] == (byte) 172) {
            return (ip[1] & 0xFF) >= 16 && (ip[1] & 0xFF) <= 31;
        }

        // 192.168.0.0 - 192.168.255.255 (192.168/16 前缀)
        if (ip[0] == (byte) 192) {
            return ip[1] == (byte) 168;
        }

        // 检查链路本地地址 (169.254.0.0/16)
        return ip[0] == (byte) 169 && ip[1] == (byte) 254;
    }

    public static boolean isInternalIP(String ipStr) {
        if (CharSequenceUtil.isBlank(ipStr)) {
            return false;
        }

        try {
            InetAddress inetAddress = InetAddress.getByName(ipStr);

            if (inetAddress.getAddress().length > 4) {
                return isInternalV6IP(inetAddress);
            }

            return isInternalV4IP(inetAddress.getAddress());
        } catch (UnknownHostException e) {
            LOG.error("无效的IP地址: {}", ipStr, e);
            throw new GXBusinessException("无效的IP地址: " + ipStr, HttpStatus.HTTP_INTERNAL_ERROR);
        }
    }

    public static boolean isInternalV6IP(InetAddress inetAddr) {
        if (Objects.isNull(inetAddr)) {
            return false;
        }

        return inetAddr.isAnyLocalAddress()    // 任意本地地址
                || inetAddr.isLinkLocalAddress() // 链路本地地址: fe80::/10
                || inetAddr.isLoopbackAddress()  // 回环地址: ::1/128
                || inetAddr.isSiteLocalAddress() // 站点本地地址: fec0::/10(已弃用) 或 fc00::/7(ULA)
                || inetAddr.isMulticastAddress(); // 组播地址: ff00::/8
    }

    public static String filterXSS(String input) {
        if (CharSequenceUtil.isBlank(input)) {
            LOG.debug("输入为空或仅包含空白字符，直接返回: {}", input);
            return input;
        }

        if (input.length() > MAX_INPUT_LENGTH) {
            LOG.warn("输入长度超过最大限制 ({}): {}", MAX_INPUT_LENGTH, input.length());
            throw new IllegalArgumentException("输入长度超过最大限制: " + MAX_INPUT_LENGTH);
        }

        LOG.debug("开始过滤 XSS，原始输入: {}", input);

        String result = input;
        for (Pattern pattern : XSS_PATTERNS) {
            result = pattern.matcher(result).replaceAll("");
        }

        result = cleanHtmlTagsAndAttributes(result);

        result = encodeHtmlEntities(result);

        LOG.debug("XSS 过滤完成，结果: {}", result);
        return result;
    }

    private static String cleanHtmlTagsAndAttributes(String input) {
        if (!input.contains("<") || !input.contains(">")) {
            return input;
        }

        StringBuilder result = new StringBuilder(input.length());
        int lastIndex = 0;

        Matcher matcher = HTML_TAG_PATTERN.matcher(input);
        while (matcher.find()) {
            String tagName = matcher.group(1).toLowerCase();
            String tagContent = matcher.group(0);

            result.append(input, lastIndex, matcher.start());

            if (ALLOWED_TAGS.contains(tagName)) {
                String cleanedTag = cleanAttributes(tagContent);
                result.append(cleanedTag);
            } else {
                LOG.debug("移除不在白名单中的标签: {}", tagName);
                String endTag = "</" + tagName + ">";
                int endTagIndex = input.indexOf(endTag, matcher.end());
                if (endTagIndex != -1) {
                    String innerContent = input.substring(matcher.end(), endTagIndex);
                    result.append(innerContent);
                    lastIndex = endTagIndex + endTag.length();
                } else {
                    lastIndex = matcher.end();
                }
                continue;
            }

            lastIndex = matcher.end();
        }

        result.append(input.substring(lastIndex));
        return result.toString();
    }

    private static String cleanAttributes(String tag) {
        String tagName = tag.substring(1, tag.indexOf(' ') > 0 ? tag.indexOf(' ') : tag.length() - 1).toLowerCase();
        StringBuilder cleanedTag = new StringBuilder("<" + tagName);

        Matcher matcher = HTML_ATTRIBUTE_PATTERN.matcher(tag);
        while (matcher.find()) {
            String attrName = matcher.group(1).toLowerCase();
            String attrValue = matcher.group(0);

            if (ALLOWED_ATTRIBUTES.contains(attrName)) {
                cleanedTag.append(" ").append(attrValue);
            } else {
                LOG.debug("移除不在白名单中的属性: {} (标签: {})", attrName, tagName);
            }
        }

        cleanedTag.append(">");
        return cleanedTag.toString();
    }

    public static String encodeHtmlEntities(String input) {
        if (input == null) {
            LOG.debug("输入为 null，直接返回 null");
            return null;
        }

        if (input.length() > MAX_INPUT_LENGTH) {
            LOG.warn("输入长度超过最大限制 ({}): {}", MAX_INPUT_LENGTH, input.length());
            throw new IllegalArgumentException("输入长度超过最大限制: " + MAX_INPUT_LENGTH);
        }

        if (input.isEmpty()) {
            LOG.debug("输入为空字符串，直接返回");
            return input;
        }

        LOG.debug("开始 HTML 实体编码，原始输入: {}", input);

        StringBuilder encoded = new StringBuilder(input.length() * 2); // 预估编码后长度
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&':
                    encoded.append("&amp;");
                    break;
                case '<':
                    encoded.append("&lt;");
                    break;
                case '>':
                    encoded.append("&gt;");
                    break;
                case '"':
                    encoded.append("&quot;");
                    break;
                case '\'':
                    encoded.append("&apos;");
                    break;
                case '/':
                    encoded.append("&#x2F;");
                    break;
                default:
                    encoded.append(c);
                    break;
            }
        }

        String result = encoded.toString();
        LOG.debug("HTML 实体编码完成，结果: {}", result);
        return result;
    }

    public static boolean isRateLimited(String clientId) {
        if (CharSequenceUtil.isBlank(clientId)) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanupTime > CLEANUP_INTERVAL) {
            cleanupExpiredRecords(currentTime);
            lastCleanupTime = currentTime;
        }

        List<Long> timestamps = requestTimestamps.computeIfAbsent(clientId, k -> new ArrayList<>());

        long oneMinuteAgo = currentTime - 60000;
        timestamps.removeIf(timestamp -> timestamp < oneMinuteAgo);

        timestamps.add(currentTime);

        return timestamps.size() > MAX_REQUESTS_PER_MINUTE;
    }

    private static void cleanupExpiredRecords(long currentTime) {
        long oneMinuteAgo = currentTime - 60000;
        requestTimestamps.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(timestamp -> timestamp < oneMinuteAgo);
            return entry.getValue().isEmpty();
        });
    }

    public static boolean isValidParameter(String param) {
        if (CharSequenceUtil.isBlank(param)) {
            return true;
        }

        if (param.length() > MAX_INPUT_LENGTH) {
            LOG.warn("参数长度超过最大限制: {}", param.length());
            return false;
        }

        String[] sqlInjectionPatterns = {
                "(?i)select\\s+.*\\s+from",
                "(?i)insert\\s+into",
                "(?i)update\\s+.*\\s+set",
                "(?i)delete\\s+from",
                "(?i)drop\\s+table",
                "(?i)truncate\\s+table",
                "(?i)union\\s+select",
                "(?i)exec\\s*\\(.*\\)",
                "(?i)execute\\s*\\(.*\\)"
        };

        for (String pattern : sqlInjectionPatterns) {
            if (Pattern.compile(pattern).matcher(param).find()) {
                LOG.warn("检测到SQL注入特征: {}", param);
                return false;
            }
        }

        return true;
    }

    public static <T> T getSafeHttpParam(String paramName, Class<T> clazz) {
        T value = getHttpParam(paramName, clazz);
        if (value instanceof String strValue) {
            if (!isValidParameter(strValue)) {
                throw new GXBusinessException("参数包含非法字符", HttpStatus.HTTP_BAD_REQUEST);
            }
            value = (T) filterXSS(strValue);
        }
        return value;
    }

    public static String getSafeHeader(String headerName) {
        String headerValue = getHeader(headerName);
        if (CharSequenceUtil.isNotBlank(headerValue)) {
            if (!isValidParameter(headerValue)) {
                throw new GXBusinessException("请求头包含非法字符", HttpStatus.HTTP_BAD_REQUEST);
            }
            return filterXSS(headerValue);
        }
        return headerValue;
    }

    public static boolean isValidIP(String ip) {
        if (CharSequenceUtil.isBlank(ip) || ip.length() > MAX_IP_LENGTH) {
            return false;
        }

        if (ip.contains("<") || ip.contains(">") || ip.contains("'") || ip.contains("\"") || ip.contains(";")) {
            return false;
        }

        return isIPv4(ip) || isIPv6(ip);
    }

    public static String getSafeClientIP() {
        String ip = getClientIP();
        if (CharSequenceUtil.isNotBlank(ip)) {
            if (!isValidIP(ip)) {
                LOG.warn("检测到无效的IP地址格式: {}", ip);
                return "";
            }
            return ip;
        }
        return ip;
    }

    public static String getSafeDomain() {
        String domain = getDomain();
        if (CharSequenceUtil.isNotBlank(domain)) {
            if (!domain.matches("^https?://[\\w.-]+(:\\d+)?$")) {
                throw new GXBusinessException("无效的域名格式", HttpStatus.HTTP_BAD_REQUEST);
            }
            return filterXSS(domain);
        }
        return domain;
    }
}
