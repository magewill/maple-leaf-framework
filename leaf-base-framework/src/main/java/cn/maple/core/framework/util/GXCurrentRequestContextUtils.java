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
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GXCurrentRequestContextUtils {
    private static final Logger LOG = LoggerFactory.getLogger(GXCurrentRequestContextUtils.class);

    private static final int MAX_INPUT_LENGTH = 1024 * 1024;

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "p", "br", "b", "i", "u", "strong", "em", "span", "div",
            "ul", "ol", "li", "table", "tr", "td", "th", "thead", "tbody",
            "h1", "h2", "h3", "h4", "h5", "h6", "a", "img"
    );

    private static final Set<String> ALLOWED_ATTRIBUTES = Set.of(
            "href", "src", "alt", "title", "style", "class", "id", "align", "width", "height"
    );

    private static final Pattern[] XSS_PATTERNS = {
            Pattern.compile("<\\s*script\\b[^>]*>(.*?)<\\s*/\\s*script\\s*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on\\w+\\s*=\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on\\w+\\s*=\\s*'[^']*'", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on\\w+\\s*=\\s*`[^`]*`", Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("vbscript\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("data\\s*:[^\\s]*,", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\s*iframe[^>]*>(.*?)<\\s*/\\s*iframe\\s*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\s*frame[^>]*>(.*?)<\\s*/\\s*frame\\s*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\s*object[^>]*>(.*?)<\\s*/\\s*object\\s*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\s*embed[^>]*>(.*?)<\\s*/\\s*embed\\s*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("eval\\((.*?)\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Function\\((.*?)\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("expression\\((.*?)\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("&#x[0-9a-fA-F]{2,4};?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("&#[0-9]{2,4};?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\s*svg[^>]*>.*?<\\s*/\\s*svg\\s*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\s*img[^>]*\\bonerror\\s*=[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\s*meta[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("style\\s*=\\s*['\"][^'\"]*expression\\([^'\"]*\\)[^'\"]*['\"]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<!--.*?-->", Pattern.DOTALL)
    };

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ATTRIBUTE_PATTERN = Pattern.compile("\\b([a-zA-Z][a-zA-Z0-9]*)\\s*=\\s*['\"][^'\"]*['\"]", Pattern.CASE_INSENSITIVE);

    private static final int MAX_IP_LENGTH = 45;

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}" +
                    "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$"
    );

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

    private static final int MAX_REQUESTS_PER_MINUTE = 1000;
    private static final ConcurrentMap<String, ConcurrentLinkedDeque<Long>> requestTimestamps = new ConcurrentHashMap<>();
    private static final long CLEANUP_INTERVAL = 60000;
    private static final AtomicLong lastCleanupTime = new AtomicLong(System.currentTimeMillis());

    private GXCurrentRequestContextUtils() {
    }

    private static <T> T getDefaultValue(Class<T> clazz) {
        if (clazz == null) {
            return null;
        }
        return GXCommonUtils.getClassDefaultValue(clazz);
    }

    private static <T> T convertSafely(Class<T> clazz, Object value, T defaultValue) {
        if (clazz == null || value == null) {
            return defaultValue;
        }
        try {
            return Convert.convert(clazz, value);
        } catch (Exception e) {
            LOG.debug("Convert value failed: targetType={}, error={}", clazz.getName(), e.getMessage());
            return defaultValue;
        }
    }

    public static HttpServletRequest getHttpServletRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes requestAttributes) {
            return requestAttributes.getRequest();
        }
        return null;
    }

    public static HttpServletResponse getHttpServletResponse() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes requestAttributes) {
            return requestAttributes.getResponse();
        }
        return null;
    }

    public static String getDomain() {
        HttpServletRequest request = Objects.requireNonNull(getHttpServletRequest(), "Current thread is not an HTTP request thread");
        StringBuffer url = request.getRequestURL();
        return url.delete(url.length() - request.getRequestURI().length(), url.length()).toString();
    }

    public static String getOrigin() {
        HttpServletRequest request = Objects.requireNonNull(getHttpServletRequest(), "Current thread is not an HTTP request thread");
        return request.getHeader("Origin");
    }

    public static <T> T getHttpParam(String paramName, Class<T> clazz) {
        if (CharSequenceUtil.isBlank(paramName)) {
            LOG.warn("Parameter name must not be blank");
            return getDefaultValue(clazz);
        }

        final HttpServletRequest httpServletRequest = getHttpServletRequest();
        if (null == httpServletRequest) {
            LOG.debug("No HTTP request context, cannot read request parameter");
            return getDefaultValue(clazz);
        }

        Object jsonRequestBody = Optional.ofNullable(httpServletRequest.getAttribute("JSON_REQUEST_BODY")).orElse("{}");
        final JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.toBean(jsonRequestBody.toString(), JSONObject.class);
        } catch (Exception e) {
            LOG.debug("Parse JSON request body failed: {}", e.getMessage());
            return getParamFromRequestOrAttribute(httpServletRequest, paramName, clazz);
        }

        if (!jsonObject.isEmpty()) {
            try {
                T value = jsonObject.getByPath(paramName, clazz);
                if (null != value) {
                    return value;
                }
            } catch (Exception e) {
                LOG.debug("Read parameter from JSON request body failed: paramName={}, error={}", paramName, e.getMessage());
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

        T obj = convertSafely(clazz, requestParameter, null);

        if (null == obj) {
            return getDefaultValue(clazz);
        }

        return obj;
    }

    public static String getHeader(String headerName) {
        if (CharSequenceUtil.isBlank(headerName)) {
            LOG.warn("Header name must not be blank");
            return null;
        }

        HttpServletRequest request = getHttpServletRequest();
        if (Objects.isNull(request)) {
            LOG.debug("No HttpServletRequest available");
            return null;
        }
        return JakartaServletUtil.getHeader(request, headerName, CharsetUtil.UTF_8);
    }

    public static Dict getAllHeaders() {
        HttpServletRequest request = getHttpServletRequest();
        if (Objects.isNull(request)) {
            LOG.debug("No HttpServletRequest available");
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
        return convertSafely(targetClass, headerValue, defaultValue);
    }

    public static <R> R getLoginFieldFromToken(String tokenName, String tokenFieldName, Class<R> clazz, String secretKey) {
        if (CharSequenceUtil.isBlank(tokenFieldName) || clazz == null) {
            return null;
        }
        Object attribute = isHTTP() ? getHttpServletRequestAttribute(tokenFieldName, clazz) : null;
        if (Objects.nonNull(attribute)) {
            return convertSafely(clazz, attribute, null);
        }
        if (Objects.isNull(secretKey)) {
            secretKey = GXTokenConstant.USER_TOKEN_SECRET_KEY;
        }
        Dict dict = getLoginCredentials(tokenName, secretKey);
        Object retValue = dict.getObj(tokenFieldName);
        if (Objects.nonNull(retValue)) {
            return convertSafely(clazz, retValue, null);
        }
        return null;
    }

    public static Dict getLoginCredentials(String tokenName, String secretKey) {
        if (isRPC()) {
            return Dict.create();
        }
        HttpServletRequest request = getHttpServletRequest();
        if (request == null) {
            return Dict.create();
        }
        Object attribute = request.getAttribute(GXCommonConstant.SSO_TOKEN_ATTR);
        if (attribute instanceof Dict tokenDict) {
            if (!tokenDict.isEmpty()) {
                return tokenDict;
            }
        } else if (Objects.nonNull(attribute)) {
            LOG.debug("SSO token attribute is not Dict: type={}", attribute.getClass().getName());
        }
        String token = getHeader(tokenName);
        if (CharSequenceUtil.isBlank(token)) {
            LOG.error("Token header is missing: tokenName={}", tokenName);
            return Dict.create();
        }
        if (!GXCommonUtils.isBase64(token)) {
            LOG.error("Token header is not a valid base64 string");
            return Dict.create();
        }
        String s = GXAuthCodeUtils.authCodeDecode(token, secretKey);
        if (CharSequenceUtil.equalsIgnoreCase("{}", s) || !JSONUtil.isTypeJSONObject(s)) {
            return Dict.create();
        }
        try {
            return JSONUtil.toBean(s, Dict.class);
        } catch (Exception e) {
            LOG.error("Token JSON parse failed: {}", e.getMessage());
            return Dict.create();
        }
    }

    public static String getClientIP(HttpServletRequest httpServletRequest) {
        if (null == httpServletRequest) {
            return "";
        }

        String[] headerNames = {
                "X-Forwarded-For",
                "CF-Connecting-IP",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR",
                "X-Cluster-Client-IP",
                "Fastly-Client-IP",
                "True-Client-IP"
        };

        String ip;
        for (String headerName : headerNames) {
            ip = httpServletRequest.getHeader(headerName);
            if (!CharSequenceUtil.isBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                if (isValidIP(ip)) {
                    LOG.debug("Client IP found from header: headerName={}, ip={}", headerName, ip);
                    return ip;
                }
            }
        }

        ip = JakartaServletUtil.getClientIP(httpServletRequest);
        if (!CharSequenceUtil.isBlank(ip)) {
            LOG.debug("Client IP found from JakartaServletUtil: ip={}", ip);
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
            LOG.debug("Get IP address type failed: ip={}", ip, e);
            return "INVALID";
        }
    }

    public static HttpServletRequest setHttpServletRequestAttribute(String attributeName, String attributeValue) {
        final HttpServletRequest servletRequest = getHttpServletRequest();
        if (servletRequest == null) {
            return null;
        }
        servletRequest.setAttribute(attributeName, attributeValue);
        return servletRequest;
    }

    public static <T> T getHttpServletRequestAttribute(String attributeName, Class<T> clazz) {
        final HttpServletRequest servletRequest = getHttpServletRequest();
        if (servletRequest == null) {
            return null;
        }
        final Object attributeValue = servletRequest.getAttribute(attributeName);
        if (null == attributeValue) {
            return null;
        }
        return convertSafely(clazz, attributeValue, null);
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
            if (httpServletRequest == null) {
                return false;
            }
            String header = JakartaServletUtil.getHeader(httpServletRequest, GXTokenConstant.TOKEN_NAME, StandardCharsets.UTF_8);
            return Objects.nonNull(header);
        }
        return false;
    }

    public static boolean isInternalV4IP(byte[] ip) {
        if (ip == null || ip.length != 4) {
            throw new GXBusinessException("Invalid IPv4 address byte array", HttpStatus.HTTP_INTERNAL_ERROR);
        }

        if (ip[0] == (byte) 127) {
            return true;
        }

        if (ip[0] == (byte) 10) {
            return true;
        }

        if (ip[0] == (byte) 172) {
            return (ip[1] & 0xFF) >= 16 && (ip[1] & 0xFF) <= 31;
        }

        if (ip[0] == (byte) 192) {
            return ip[1] == (byte) 168;
        }

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
            LOG.error("Invalid IP address: {}", ipStr, e);
            throw new GXBusinessException("Invalid IP address: " + ipStr, HttpStatus.HTTP_INTERNAL_ERROR);
        }
    }

    public static boolean isInternalV6IP(InetAddress inetAddr) {
        if (Objects.isNull(inetAddr)) {
            return false;
        }

        return inetAddr.isAnyLocalAddress()
                || inetAddr.isLinkLocalAddress()
                || inetAddr.isLoopbackAddress()
                || inetAddr.isSiteLocalAddress()
                || inetAddr.isMulticastAddress();
    }

    public static String filterXSS(String input) {
        if (CharSequenceUtil.isBlank(input)) {
            LOG.debug("Input is blank, return directly: {}", input);
            return input;
        }

        if (input.length() > MAX_INPUT_LENGTH) {
            LOG.warn("Input length exceeds max limit: max={}, actual={}", MAX_INPUT_LENGTH, input.length());
            throw new IllegalArgumentException("Input length exceeds max limit: " + MAX_INPUT_LENGTH);
        }

        LOG.debug("Start filtering XSS: input={}", input);

        String result = input;
        for (Pattern pattern : XSS_PATTERNS) {
            result = pattern.matcher(result).replaceAll("");
        }

        result = cleanHtmlTagsAndAttributes(result);

        result = encodeHtmlEntities(result);

        LOG.debug("XSS filtering completed: result={}", result);
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
                LOG.debug("Remove tag not in whitelist: tagName={}", tagName);
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
                LOG.debug("Remove attribute not in whitelist: attrName={}, tagName={}", attrName, tagName);
            }
        }

        cleanedTag.append(">");
        return cleanedTag.toString();
    }

    public static String encodeHtmlEntities(String input) {
        if (input == null) {
            LOG.debug("Input is null, return null");
            return null;
        }

        if (input.length() > MAX_INPUT_LENGTH) {
            LOG.warn("Input length exceeds max limit: max={}, actual={}", MAX_INPUT_LENGTH, input.length());
            throw new IllegalArgumentException("Input length exceeds max limit: " + MAX_INPUT_LENGTH);
        }

        if (input.isEmpty()) {
            LOG.debug("Input is empty, return directly");
            return input;
        }

        LOG.debug("Start HTML entity encoding: input={}", input);

        StringBuilder encoded = new StringBuilder(input.length() * 2);
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
        LOG.debug("HTML entity encoding completed: result={}", result);
        return result;
    }

    public static boolean isRateLimited(String clientId) {
        if (CharSequenceUtil.isBlank(clientId)) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long lastCleanup = lastCleanupTime.get();
        if (currentTime - lastCleanup > CLEANUP_INTERVAL && lastCleanupTime.compareAndSet(lastCleanup, currentTime)) {
            cleanupExpiredRecords(currentTime);
        }

        ConcurrentLinkedDeque<Long> timestamps = requestTimestamps.computeIfAbsent(clientId, k -> new ConcurrentLinkedDeque<>());

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
            LOG.warn("Parameter length exceeds max limit: actual={}", param.length());
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
                LOG.warn("SQL injection pattern detected: param={}", param);
                return false;
            }
        }

        return true;
    }

    public static <T> T getSafeHttpParam(String paramName, Class<T> clazz) {
        T value = getHttpParam(paramName, clazz);
        if (value instanceof String strValue) {
            if (!isValidParameter(strValue)) {
                throw new GXBusinessException("Parameter contains invalid characters", HttpStatus.HTTP_BAD_REQUEST);
            }
            value = (T) filterXSS(strValue);
        }
        return value;
    }

    public static String getSafeHeader(String headerName) {
        String headerValue = getHeader(headerName);
        if (CharSequenceUtil.isNotBlank(headerValue)) {
            if (!isValidParameter(headerValue)) {
                throw new GXBusinessException("Header contains invalid characters", HttpStatus.HTTP_BAD_REQUEST);
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
                LOG.warn("Invalid IP address format detected: ip={}", ip);
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
                throw new GXBusinessException("Invalid domain format", HttpStatus.HTTP_BAD_REQUEST);
            }
            return filterXSS(domain);
        }
        return domain;
    }
}
