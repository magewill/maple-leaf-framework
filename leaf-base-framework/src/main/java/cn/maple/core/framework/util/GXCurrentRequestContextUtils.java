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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 * HTTP请求上下文工具类，用于封装Spring Boot框架中HttpServletRequest相关操作
 * 提供了获取请求参数、请求头、客户端IP、Token验证等功能
 * </p>
 *
 * <p>
 * 主要功能包括：
 * <ul>
 *   <li>获取当前请求的HttpServletRequest和HttpServletResponse对象</li>
 *   <li>获取请求参数（支持JSON请求体、URL查询参数、请求属性）</li>
 *   <li>获取和验证请求头信息</li>
 *   <li>处理Token相关操作（获取、验证、解析）</li>
 *   <li>获取客户端IP地址（支持代理服务器）</li>
 *   <li>判断请求类型（HTTP、RPC）</li>
 *   <li>内网IP地址验证（IPv4和IPv6）</li>
 * </ul>
 * </p>
 *
 * @author maple
 * @since 1.0.0
 */
public class GXCurrentRequestContextUtils {
    /**
     * 日志对象
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXCurrentRequestContextUtils.class);

    // 最大输入长度，防止超长输入导致性能问题
    private static final int MAX_INPUT_LENGTH = 1024 * 1024; // 1MB

    // 允许的 HTML 标签白名单（富文本编辑器常用标签）
    private static final Set<String> ALLOWED_TAGS = new HashSet<>(Arrays.asList(
            "p", "br", "b", "i", "u", "strong", "em", "span", "div",
            "ul", "ol", "li", "table", "tr", "td", "th", "thead", "tbody",
            "h1", "h2", "h3", "h4", "h5", "h6", "a", "img"
    ));

    // 允许的 HTML 属性白名单（富文本编辑器常用属性）
    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "href", "src", "alt", "title", "style", "class", "id", "align", "width", "height"
    ));

    // XSS 过滤正则表达式集合，覆盖常见的 XSS 攻击向量
    private static final Pattern[] XSS_PATTERNS = {
            // 基本的 script 标签（包括大小写变体）
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

    /**
     * 获取当前请求的HttpServletRequest对象
     * 如果当前线程不是Web请求线程（如后台任务线程），则返回null
     *
     * @return 当前请求的HttpServletRequest对象，如果不在Web请求线程中则返回null
     */
    public static HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (Objects.nonNull(requestAttributes)) {
            return requestAttributes.getRequest();
        }
        return null;
    }

    /**
     * 获取当前请求的HttpServletResponse对象
     * 如果当前线程不是Web请求线程（如后台任务线程），则返回null
     *
     * @return 当前请求的HttpServletResponse对象，如果不在Web请求线程中则返回null
     */
    public static HttpServletResponse getHttpServletResponse() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (Objects.nonNull(requestAttributes)) {
            return requestAttributes.getResponse();
        }
        return null;
    }

    /**
     * 获取Http请求中的域名部分（不包含路径）
     * 例如：http://example.com/path 返回 http://example.com
     *
     * @return 当前请求的域名URL字符串
     * @throws NullPointerException 如果当前不在HTTP请求上下文中
     */
    public static String getDomain() {
        HttpServletRequest request = Objects.requireNonNull(getHttpServletRequest(), "当前线程不是Web请求线程，无法获取请求域名");
        StringBuffer url = request.getRequestURL();
        return url.delete(url.length() - request.getRequestURI().length(), url.length()).toString();
    }

    /**
     * 获取Http头中的Origin字段
     * 通常用于CORS（跨域资源共享）场景
     *
     * @return Origin头的值，如果不存在则可能返回null
     * @throws NullPointerException 如果当前不在HTTP请求上下文中
     */
    public static String getOrigin() {
        HttpServletRequest request = Objects.requireNonNull(getHttpServletRequest(), "当前线程不是Web请求线程，无法获取Origin");
        return request.getHeader("Origin");
    }

    /**
     * 获取Http请求中的参数数据，支持多种数据来源：
     * 1. JSON请求体中的数据（通过"JSON_REQUEST_BODY"属性获取）
     * 2. URL查询参数（通过request.getParameter获取）
     * 3. 请求属性（通过request.getAttribute获取）
     *
     * <p>该方法会按照以下顺序查找参数：</p>
     * <ol>
     *   <li>首先尝试从JSON请求体中获取（适用于POST请求）</li>
     *   <li>然后尝试从URL查询参数中获取（适用于GET请求）</li>
     *   <li>最后尝试从请求属性中获取（适用于请求处理过程中设置的属性）</li>
     * </ol>
     *
     * @param paramName 参数名称，支持路径表达式（如user.name）
     * @param clazz     期望转换的结果类型
     * @param <T>       泛型类型参数
     * @return 转换后的参数值，如果参数不存在则返回对应类型的默认值
     */
    public static <T> T getHttpParam(String paramName, Class<T> clazz) {
        if (CharSequenceUtil.isBlank(paramName)) {
            LOG.warn("参数名不能为空");
            return GXCommonUtils.getClassDefaultValue(clazz);
        }

        // 获取HttpServletRequest对象
        final HttpServletRequest httpServletRequest = getHttpServletRequest();
        if (null == httpServletRequest) {
            LOG.debug("当前不在HTTP请求上下文中，无法获取请求参数");
            return GXCommonUtils.getClassDefaultValue(clazz);
        }

        // 尝试从JSON请求体中获取数据
        Object jsonRequestBody = Optional.ofNullable(httpServletRequest.getAttribute("JSON_REQUEST_BODY")).orElse("{}");
        final JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.toBean(jsonRequestBody.toString(), JSONObject.class);
        } catch (Exception e) {
            LOG.debug("解析JSON请求体失败: {}", e.getMessage());
            // JSON解析失败，继续尝试其他方式获取参数
            return getParamFromRequestOrAttribute(httpServletRequest, paramName, clazz);
        }

        // 如果JSON对象不为空，尝试从JSON中获取值
        if (!jsonObject.isEmpty()) {
            try {
                T value = jsonObject.getByPath(paramName, clazz);
                if (null != value) {
                    return value;
                }
            } catch (Exception e) {
                LOG.debug("从JSON请求体获取参数{}失败: {}", paramName, e.getMessage());
                // 继续尝试其他方式获取参数
            }
        }

        // 尝试从URL参数或请求属性中获取
        return getParamFromRequestOrAttribute(httpServletRequest, paramName, clazz);
    }

    /**
     * 从请求参数或请求属性中获取参数值
     *
     * @param request   HTTP请求对象
     * @param paramName 参数名称
     * @param clazz     期望转换的结果类型
     * @param <T>       泛型类型参数
     * @return 转换后的参数值，如果参数不存在则返回对应类型的默认值
     */
    private static <T> T getParamFromRequestOrAttribute(HttpServletRequest request, String paramName, Class<T> clazz) {
        // 尝试从URL参数中获取
        String requestParameter = request.getParameter(paramName);

        // 如果URL参数中不存在，尝试从请求属性中获取
        if (null == requestParameter) {
            final Object requestAttribute = request.getAttribute(paramName);
            if (null != requestAttribute) {
                requestParameter = requestAttribute.toString();
            }
        }

        // 转换参数值到目标类型
        T obj = Convert.convert(clazz, requestParameter);

        // 如果值为null，返回类型默认值
        if (null == obj) {
            return GXCommonUtils.getClassDefaultValue(clazz);
        }

        return obj;
    }

    /**
     * 获取Http头中的header值
     *
     * @param headerName 请求头名称
     * @return 请求头的值，如果不存在或当前不在HTTP上下文中则返回null
     */
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

    /**
     * 获取所有请求头信息
     *
     * @return 包含所有请求头的Dict对象，如果当前不在HTTP上下文中则返回空Dict
     */
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

    /**
     * 获取Http头中的header
     *
     * @param headerName  header头的名字
     * @param targetClass 返回的数据类型
     * @return 指定的目标类型
     */
    public static <T> T getHeader(String headerName, Class<T> targetClass) {
        return getHeader(headerName, targetClass, null);
    }

    /**
     * 获取Http头中的header
     *
     * @param headerName   header头的名字
     * @param targetClass  返回的数据类型
     * @param defaultValue 默认值
     * @return 指定的目标类型
     */
    public static <T> T getHeader(String headerName, Class<T> targetClass, T defaultValue) {
        String headerValue = getHeader(headerName);
        if (Objects.isNull(headerValue)) {
            return defaultValue;
        }
        return Convert.convert(targetClass, headerValue);
    }

    /**
     * 从token中获取登录用户的指定字段的值
     * <p>
     * {@code
     * getLoginFieldFromToken("token" , "username" , String.class , "123456");
     * getLoginFieldFromToken("token" , "userId" , Integer.class , "123456");
     * }
     *
     * @param tokenName      header中Token的名字 eg : Authorization、token、adminToken
     * @param tokenFieldName Token中包含的ID名字 eg : id、userId、adminId、username、nickname....
     * @param clazz          返回值类型
     * @param secretKey      加解密KEY
     * @return R
     */
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

    /**
     * 从token中获取登录信息
     *
     * @param tokenName token名字
     * @param secretKey token密钥
     * @return Dict
     */
    public static Dict getLoginCredentials(String tokenName, String secretKey) {
        // 如果是RPC调用 直接返回
        if (isRPC()) {
            return Dict.create();
        }
        // 减少token的二次解密操作 在GXSSOAuthorizationInterceptor拦截器中已经解密过一次并且已经将解密之后的token放入了当前请求对象中
        HttpServletRequest request = getHttpServletRequest();
        assert request != null;
        Object attribute = request.getAttribute(GXCommonConstant.SSO_TOKEN_ATTR);
        if (Objects.nonNull(attribute)) {
            Dict tokenDict = (Dict) attribute;
            if (!tokenDict.isEmpty()) {
                return tokenDict;
            }
        }
        // 从请求对象中获取token并且解密
        String token = getHeader(tokenName);
        if (CharSequenceUtil.isBlank(token)) {
            LOG.error("{}不存在", tokenName);
            return Dict.create();
        }
        // 判断token是否是一个正确的base64字符串
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

    /**
     * 获取客户端IP地址
     * <p>
     * 该方法会依次检查以下HTTP头信息，以处理各种代理服务器场景：
     * <ul>
     *   <li>X-Forwarded-For - 最常见的代理IP头，被大多数代理服务器支持</li>
     *   <li>CF-Connecting-IP - Cloudflare特有的头</li>
     *   <li>X-Real-IP - Nginx代理常用头</li>
     *   <li>Proxy-Client-IP - Apache HTTP Server代理使用</li>
     *   <li>WL-Proxy-Client-IP - WebLogic代理使用</li>
     *   <li>HTTP_CLIENT_IP - 一些代理服务器使用</li>
     *   <li>HTTP_X_FORWARDED_FOR - 一些代理服务器使用</li>
     *   <li>X-Cluster-Client-IP - 负载均衡场景使用</li>
     *   <li>Fastly-Client-IP - Fastly CDN使用</li>
     *   <li>True-Client-IP - Akamai和Cloudflare使用</li>
     * </ul>
     * 如果以上头信息都不存在，则使用{@link JakartaServletUtil#getClientIP}方法获取IP
     * </p>
     *
     * @param httpServletRequest HTTP请求对象
     * @return 客户端IP地址，如果无法获取则返回空字符串
     */
    public static String getClientIP(HttpServletRequest httpServletRequest) {
        if (null == httpServletRequest) {
            return "";
        }

        // 检查常见的代理服务器头信息，按优先级排序
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
                // 多个代理的情况，第一个IP为客户端真实IP
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                if (isValidIP(ip)) {
                    LOG.debug("从{}头获取到客户端IP: {}", headerName, ip);
                    return ip;
                }
            }
        }

        // 如果以上方法都无法获取，则使用JakartaServletUtil提供的方法
        ip = JakartaServletUtil.getClientIP(httpServletRequest);
        if (!CharSequenceUtil.isBlank(ip)) {
            LOG.debug("从JakartaServletUtil获取到客户端IP: {}", ip);
        }
        return CharSequenceUtil.isBlank(ip) ? "" : ip;
    }

    /**
     * 获取当前请求的客户端IP地址
     * <p>
     * 该方法会自动从当前请求上下文中获取HttpServletRequest对象，
     * 然后调用{@link #getClientIP(HttpServletRequest)}方法获取客户端IP
     * </p>
     *
     * @return 客户端IP地址，如果当前不在HTTP请求上下文中则返回空字符串
     */
    public static String getClientIP() {
        HttpServletRequest httpServletRequest = GXCurrentRequestContextUtils.getHttpServletRequest();
        if (Objects.nonNull(httpServletRequest)) {
            return getClientIP(httpServletRequest);
        }
        return "";
    }

    /**
     * 验证IP地址是否有效，支持IPv4和IPv6格式
     * <p>
     * 该方法使用多层验证策略：
     * <ol>
     *   <li>首先检查输入是否为空或超长（防止DoS攻击）</li>
     *   <li>检查是否包含非法字符（基本安全检查）</li>
     *   <li>然后使用Java内置的InetAddress进行验证（最可靠的方法）</li>
     *   <li>如果InetAddress验证失败，使用预编译正则表达式进行验证</li>
     * </ol>
     * </p>
     *
     * @param ip 需要验证的IP地址字符串
     * @return 如果IP地址有效返回true，否则返回false
     */
    public static boolean isValidIP(String ip) {
        // 检查输入是否为空或仅包含空白字符
        if (CharSequenceUtil.isBlank(ip)) {
            LOG.debug("IP 地址为空或仅包含空白字符: {}", ip);
            return false;
        }

        // 检查输入长度，防止超长输入导致性能问题
        if (ip.length() > MAX_IP_LENGTH) {
            LOG.warn("IP 地址长度超过最大限制 ({}): {}", MAX_IP_LENGTH, ip);
            return false;
        }

        // 检查是否包含非法字符（基本安全检查）
        if (ip.contains("'") || ip.contains("\"") || ip.contains(";") || ip.contains("&") || ip.contains("|")) {
            LOG.warn("IP 地址包含非法字符: {}", ip);
            return false;
        }

        // 尝试使用 InetAddress 验证 IP 地址（最可靠的方法）
        try {
            InetAddress inetAddress = InetAddress.getByName(ip);
            LOG.debug("IP 地址通过 InetAddress 验证: {}", ip);
            return true;
        } catch (UnknownHostException e) {
            // 如果 InetAddress 验证失败，记录调试信息并继续使用正则表达式验证
            LOG.debug("IP 地址通过 InetAddress 验证失败，尝试正则表达式验证: {}", ip, e);

            // 使用预编译的正则表达式验证 IPv4 或 IPv6 地址
            boolean isIPv4 = IPV4_PATTERN.matcher(ip).matches();
            boolean isIPv6 = IPV6_PATTERN.matcher(ip).matches();

            // 如果是有效的 IPv4 或 IPv6 地址，返回 true
            if (isIPv4 || isIPv6) {
                LOG.debug("IP 地址通过正则表达式验证: {} (IPv4: {}, IPv6: {})", ip, isIPv4, isIPv6);
                return true;
            }

            // 如果正则表达式验证也失败，记录失败信息并返回 false
            LOG.debug("IP 地址通过正则表达式验证失败: {}", ip);
            return false;
        }
    }

    /**
     * 获取IP地址类型
     * <p>
     * 返回IP地址的类型信息，包括：
     * <ul>
     *   <li>IPv4 - 标准IPv4地址</li>
     *   <li>IPv6 - 标准IPv6地址</li>
     *   <li>LOOPBACK - 回环地址（127.0.0.1或::1）</li>
     *   <li>PRIVATE - 私有地址（内网IP）</li>
     *   <li>INVALID - 无效IP地址</li>
     * </ul>
     * </p>
     *
     * @param ip 需要判断类型的IP地址字符串
     * @return IP地址类型的字符串描述
     */
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

    /**
     * 向HttpServletRequest中设置属性值
     *
     * @param attributeName  属性的名字
     * @param attributeValue 属性的值
     * @return HttpServletRequest
     */
    public static HttpServletRequest setHttpServletRequestAttribute(String attributeName, String attributeValue) {
        final HttpServletRequest servletRequest = getHttpServletRequest();
        assert servletRequest != null;
        servletRequest.setAttribute(attributeName, attributeValue);
        return servletRequest;
    }

    /**
     * 获取HttpServletRequest中设置的属性值
     *
     * @param attributeName 属性的名字
     * @return T
     */
    public static <T> T getHttpServletRequestAttribute(String attributeName, Class<T> clazz) {
        final HttpServletRequest servletRequest = Objects.requireNonNull(getHttpServletRequest());
        final Object attributeValue = servletRequest.getAttribute(attributeName);
        if (null == attributeValue) {
            return null;
        }
        return Convert.convert(clazz, attributeValue);
    }

    /**
     * 判断是否是RPC调用
     * 如果是RPC调用 则不会存在HttpServletRequest对象
     *
     * @return true 是 ; false 不是
     */
    public static boolean isRPC() {
        HttpServletRequest request = getHttpServletRequest();
        return Objects.isNull(request);
    }

    /**
     * 判断是否是HTTP调用
     * 如果是HTTP调用 则会存在HttpServletRequest对象
     *
     * @return true 是 ; false 不是
     */
    public static boolean isHTTP() {
        HttpServletRequest request = getHttpServletRequest();
        return Objects.nonNull(request);
    }

    /**
     * 判断token是否存在
     * 在有些场景下 不需要登录
     * 所以token是不存在
     *
     * @return true 存在 false 不存在
     */
    public static boolean tokenExists() {
        if (isHTTP()) {
            HttpServletRequest httpServletRequest = getHttpServletRequest();
            assert httpServletRequest != null;
            String header = JakartaServletUtil.getHeader(httpServletRequest, GXTokenConstant.TOKEN_NAME, StandardCharsets.UTF_8);
            return Objects.nonNull(header);
        }
        return false;
    }

    /**
     * 判断是否是IPv4内网IP地址
     * <p>
     * 内网IP地址范围包括：
     * <ul>
     *   <li>10.0.0.0 - 10.255.255.255 (10/8 前缀)</li>
     *   <li>172.16.0.0 - 172.31.255.255 (172.16/12 前缀)</li>
     *   <li>192.168.0.0 - 192.168.255.255 (192.168/16 前缀)</li>
     *   <li>127.0.0.0 - 127.255.255.255 (127/8 前缀，本地回环地址)</li>
     * </ul>
     * </p>
     *
     * @param ip IPv4地址的字节数组表示
     * @return 如果是内网IP则返回true，否则返回false
     * @throws GXBusinessException 如果提供的字节数组长度不是4（不是有效的IPv4地址）
     */
    public static boolean isInternalV4IP(byte[] ip) {
        if (ip.length != 4) {
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

    /**
     * 判断IPv4地址字符串是否是内网IP
     *
     * @param ipStr IPv4地址字符串
     * @return 如果是内网IP则返回true，否则返回false
     * @throws GXBusinessException 如果提供的IP地址格式不正确
     */
    public static boolean isInternalIP(String ipStr) {
        if (CharSequenceUtil.isBlank(ipStr)) {
            return false;
        }

        try {
            // 将IP地址字符串转换为InetAddress对象
            InetAddress inetAddress = InetAddress.getByName(ipStr);

            // 如果是IPv6地址，使用IPv6的判断方法
            if (inetAddress.getAddress().length > 4) {
                return isInternalV6IP(inetAddress);
            }

            // 否则使用IPv4的判断方法
            return isInternalV4IP(inetAddress.getAddress());
        } catch (UnknownHostException e) {
            LOG.error("无效的IP地址: {}", ipStr, e);
            throw new GXBusinessException("无效的IP地址: " + ipStr, HttpStatus.HTTP_INTERNAL_ERROR);
        }
    }

    /**
     * 判断是否是IPv6内网IP地址
     * <p>
     * 该方法检查IPv6地址是否属于以下类别：
     * <ul>
     *   <li>任意本地地址（AnyLocalAddress）</li>
     *   <li>链路本地地址（LinkLocalAddress）- fe80::/10</li>
     *   <li>回环地址（LoopbackAddress）- ::1/128</li>
     *   <li>站点本地地址（SiteLocalAddress）- fec0::/10（已弃用）或fc00::/7（唯一本地地址）</li>
     * </ul>
     * </p>
     *
     * @param inetAddr IPv6网络地址对象
     * @return 如果是内网IPv6地址则返回true，否则返回false
     */
    public static boolean isInternalV6IP(InetAddress inetAddr) {
        if (Objects.isNull(inetAddr)) {
            return false;
        }

        // 检查各种内网IPv6地址类型
        return inetAddr.isAnyLocalAddress()    // 任意本地地址
                || inetAddr.isLinkLocalAddress() // 链路本地地址: fe80::/10
                || inetAddr.isLoopbackAddress()  // 回环地址: ::1/128
                || inetAddr.isSiteLocalAddress() // 站点本地地址: fec0::/10(已弃用) 或 fc00::/7(ULA)
                || inetAddr.isMulticastAddress(); // 组播地址: ff00::/8
    }

    /**
     * 过滤可能包含 XSS 攻击的字符串，支持富文本编辑器输入。
     * <p>
     * 该方法通过以下步骤过滤 XSS 攻击向量：
     * 1. 检查输入是否为空或超长。
     * 2. 使用预定义的正则表达式模式移除常见的 XSS 攻击向量。
     * 3. 清理 HTML 标签和属性，仅保留白名单中的标签和属性。
     * 4. 对特殊字符进行 HTML 实体编码，防止二次注入。
     * </p>
     *
     * @param input 需要过滤的输入字符串
     * @return 过滤后的安全字符串
     */
    public static String filterXSS(String input) {
        // 检查输入是否为空或仅包含空白字符
        if (CharSequenceUtil.isBlank(input)) {
            LOG.debug("输入为空或仅包含空白字符，直接返回: {}", input);
            return input;
        }

        // 检查输入长度，防止超长输入导致性能问题
        if (input.length() > MAX_INPUT_LENGTH) {
            LOG.warn("输入长度超过最大限制 ({}): {}", MAX_INPUT_LENGTH, input);
            throw new IllegalArgumentException("输入长度超过最大限制: " + MAX_INPUT_LENGTH);
        }

        // 记录原始输入，便于调试
        LOG.debug("开始过滤 XSS，原始输入: {}", input);

        // 第一步：使用正则表达式移除常见的 XSS 攻击向量
        String result = input;
        for (Pattern pattern : XSS_PATTERNS) {
            result = pattern.matcher(result).replaceAll("");
        }

        // 第二步：清理 HTML 标签和属性，仅保留白名单中的内容
        result = cleanHtmlTagsAndAttributes(result);

        // 第三步：对特殊字符进行 HTML 实体编码，防止二次注入
        result = encodeHtmlEntities(result);

        // 记录过滤后的结果
        LOG.debug("XSS 过滤完成，结果: {}", result);
        return result;
    }

    /**
     * 清理 HTML 标签和属性，仅保留白名单中的标签和属性。
     * <p>
     * 该方法会：
     * 1. 提取所有 HTML 标签，检查是否在白名单中。
     * 2. 提取标签中的属性，检查是否在白名单中。
     * 3. 移除不在白名单中的标签和属性。
     * </p>
     *
     * @param input 输入字符串
     * @return 清理后的字符串
     */
    private static String cleanHtmlTagsAndAttributes(String input) {
        StringBuilder result = new StringBuilder();
        int lastIndex = 0;

        // 使用正则表达式匹配所有 HTML 标签
        Matcher matcher = HTML_TAG_PATTERN.matcher(input);
        while (matcher.find()) {
            // 提取标签名（小写形式，便于比较）
            String tagName = matcher.group(1).toLowerCase();
            String tagContent = matcher.group(0);

            // 将标签前的文本追加到结果中
            result.append(input, lastIndex, matcher.start());

            // 检查标签是否在白名单中
            if (ALLOWED_TAGS.contains(tagName)) {
                // 清理标签中的属性，仅保留白名单中的属性
                String cleanedTag = cleanAttributes(tagContent);
                result.append(cleanedTag);
            } else {
                // 如果标签不在白名单中，移除整个标签（但保留标签内的内容）
                LOG.debug("移除不在白名单中的标签: {}", tagName);
                // 查找对应的结束标签
                String endTag = "</" + tagName + ">";
                int endTagIndex = input.indexOf(endTag, matcher.end());
                if (endTagIndex != -1) {
                    // 提取标签内的内容
                    String innerContent = input.substring(matcher.end(), endTagIndex);
                    result.append(innerContent);
                    lastIndex = endTagIndex + endTag.length();
                } else {
                    // 如果没有结束标签，直接跳过
                    lastIndex = matcher.end();
                }
                continue;
            }

            lastIndex = matcher.end();
        }

        // 追加剩余的文本
        result.append(input.substring(lastIndex));
        return result.toString();
    }

    /**
     * 清理 HTML 标签中的属性，仅保留白名单中的属性。
     *
     * @param tag HTML 标签字符串（如 <a href="...">）
     * @return 清理后的标签字符串
     */
    private static String cleanAttributes(String tag) {
        // 提取标签名
        String tagName = tag.substring(1, tag.indexOf(' ') > 0 ? tag.indexOf(' ') : tag.length() - 1).toLowerCase();
        StringBuilder cleanedTag = new StringBuilder("<" + tagName);

        // 使用正则表达式匹配所有属性
        Matcher matcher = HTML_ATTRIBUTE_PATTERN.matcher(tag);
        while (matcher.find()) {
            // 提取属性名（小写形式，便于比较）
            String attrName = matcher.group(1).toLowerCase();
            String attrValue = matcher.group(0);

            // 检查属性是否在白名单中
            if (ALLOWED_ATTRIBUTES.contains(attrName)) {
                // 保留合法属性
                cleanedTag.append(" ").append(attrValue);
            } else {
                LOG.debug("移除不在白名单中的属性: {} (标签: {})", attrName, tagName);
            }
        }

        cleanedTag.append(">");
        return cleanedTag.toString();
    }

    /**
     * 对字符串中的特殊字符进行 HTML 实体编码。
     * <p>
     * 该方法将以下字符编码为对应的 HTML 实体：
     * - & -> &amp;
     * - < -> &lt;
     * - > -> &gt;
     * - " -> &quot;
     * - ' -> &apos;
     * - / -> &#x2F;
     * </p>
     *
     * @param input 需要编码的输入字符串
     * @return 编码后的字符串；如果输入为 null，则返回 null
     * @throws IllegalArgumentException 如果输入长度超过最大限制
     */
    public static String encodeHtmlEntities(String input) {
        // 检查输入是否为 null
        if (input == null) {
            LOG.debug("输入为 null，直接返回 null");
            return null;
        }

        // 检查输入长度，防止超长输入导致性能问题
        if (input.length() > MAX_INPUT_LENGTH) {
            LOG.warn("输入长度超过最大限制 ({}): {}", MAX_INPUT_LENGTH, input.length());
            throw new IllegalArgumentException("输入长度超过最大限制: " + MAX_INPUT_LENGTH);
        }

        // 如果输入为空字符串，直接返回
        if (input.isEmpty()) {
            LOG.debug("输入为空字符串，直接返回");
            return input;
        }

        // 记录原始输入，便于调试
        LOG.debug("开始 HTML 实体编码，原始输入: {}", input);

        // 使用 StringBuilder 进行字符串拼接，提高性能
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
                    // 其他字符保持不变
                    encoded.append(c);
                    break;
            }
        }

        String result = encoded.toString();
        LOG.debug("HTML 实体编码完成，结果: {}", result);
        return result;
    }

    /**
     * 检查请求频率是否超过限制
     * 使用滑动窗口算法实现请求频率限制
     *
     * @param clientId 客户端标识（可以是IP、用户ID等）
     * @return 如果超过限制返回true，否则返回false
     */
    public static boolean isRateLimited(String clientId) {
        if (CharSequenceUtil.isBlank(clientId)) {
            return false;
        }

        // 定期清理过期记录
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanupTime > CLEANUP_INTERVAL) {
            cleanupExpiredRecords(currentTime);
            lastCleanupTime = currentTime;
        }

        // 获取或创建该客户端的请求时间戳列表
        List<Long> timestamps = requestTimestamps.computeIfAbsent(clientId, k -> new ArrayList<>());

        // 移除1分钟前的记录
        timestamps.removeIf(timestamp -> currentTime - timestamp > 60000);

        // 添加当前请求时间戳
        timestamps.add(currentTime);

        // 检查是否超过限制
        return timestamps.size() > MAX_REQUESTS_PER_MINUTE;
    }

    /**
     * 清理过期的请求记录
     *
     * @param currentTime 当前时间戳
     */
    private static void cleanupExpiredRecords(long currentTime) {
        requestTimestamps.forEach((clientId, timestamps) -> {
            timestamps.removeIf(timestamp -> currentTime - timestamp > 60000);
            if (timestamps.isEmpty()) {
                requestTimestamps.remove(clientId);
            }
        });
    }

    /**
     * 验证请求参数是否合法
     * 检查参数是否包含潜在的危险字符或SQL注入
     *
     * @param param 需要验证的参数值
     * @return 如果参数合法返回true，否则返回false
     */
    public static boolean isValidParameter(String param) {
        if (CharSequenceUtil.isBlank(param)) {
            return true;
        }

        // 检查参数长度
        if (param.length() > MAX_INPUT_LENGTH) {
            LOG.warn("参数长度超过最大限制: {}", param.length());
            return false;
        }

        // 检查是否包含SQL注入特征
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

    /**
     * 获取安全的请求参数值
     * 对参数进行XSS过滤和SQL注入检查
     *
     * @param paramName 参数名
     * @param clazz     期望的参数类型
     * @param <T>       泛型类型
     * @return 安全的参数值
     */
    public static <T> T getSafeHttpParam(String paramName, Class<T> clazz) {
        T value = getHttpParam(paramName, clazz);
        if (value instanceof String) {
            String strValue = (String) value;
            if (!isValidParameter(strValue)) {
                throw new GXBusinessException("参数包含非法字符", HttpStatus.HTTP_BAD_REQUEST);
            }
            value = (T) filterXSS(strValue);
        }
        return value;
    }

    /**
     * 获取安全的请求头值
     * 对请求头值进行XSS过滤和SQL注入检查
     *
     * @param headerName 请求头名称
     * @return 安全的请求头值
     */
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

    /**
     * 获取安全的客户端IP地址
     * 对IP地址进行格式验证和XSS过滤
     *
     * @return 安全的客户端IP地址
     */
    public static String getSafeClientIP() {
        String ip = getClientIP();
        if (CharSequenceUtil.isNotBlank(ip)) {
            if (!isValidIP(ip)) {
                throw new GXBusinessException("无效的IP地址格式", HttpStatus.HTTP_BAD_REQUEST);
            }
            return filterXSS(ip);
        }
        return ip;
    }

    /**
     * 获取安全的请求域名
     * 对域名进行格式验证和XSS过滤
     *
     * @return 安全的请求域名
     */
    public static String getSafeDomain() {
        String domain = getDomain();
        if (CharSequenceUtil.isNotBlank(domain)) {
            // 验证域名格式
            if (!domain.matches("^https?://[\\w.-]+(:\\d+)?$")) {
                throw new GXBusinessException("无效的域名格式", HttpStatus.HTTP_BAD_REQUEST);
            }
            return filterXSS(domain);
        }
        return domain;
    }
}
