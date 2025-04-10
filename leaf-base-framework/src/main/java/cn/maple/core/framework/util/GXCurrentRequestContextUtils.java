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
import java.util.Enumeration;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

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
    
    /**
     * XSS过滤正则表达式集合
     * 包含多种常见的XSS攻击向量模式
     * 这些模式覆盖了大多数已知的XSS攻击向量
     */
    private static final Pattern[] XSS_PATTERNS = {
        // 基本的script标签
        Pattern.compile("<script>(.*?)</script>", Pattern.CASE_INSENSITIVE),
        // 避免空格绕过
        Pattern.compile("<\\s*script\\b[^>]*>(.*?)<\\s*/\\s*script\\s*>", Pattern.CASE_INSENSITIVE),
        // 避免事件属性注入
        Pattern.compile("on\\w+\\s*=\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE),
        Pattern.compile("on\\w+\\s*=\\s*'[^']*'", Pattern.CASE_INSENSITIVE),
        Pattern.compile("on\\w+\\s*=\\s*`[^`]*`", Pattern.CASE_INSENSITIVE), // 反引号形式
        // 避免javascript:协议
        Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE),
        // 避免vbscript:协议
        Pattern.compile("vbscript\\s*:", Pattern.CASE_INSENSITIVE),
        // 避免data:协议注入
        Pattern.compile("data\\s*:[^\\s]*,", Pattern.CASE_INSENSITIVE),
        // 避免iframe注入
        Pattern.compile("<\\s*iframe[^>]*>(.*?)<\\s*/\\s*iframe\\s*>", Pattern.CASE_INSENSITIVE),
        // 避免frame注入
        Pattern.compile("<\\s*frame[^>]*>(.*?)<\\s*/\\s*frame\\s*>", Pattern.CASE_INSENSITIVE),
        // 避免object注入
        Pattern.compile("<\\s*object[^>]*>(.*?)<\\s*/\\s*object\\s*>", Pattern.CASE_INSENSITIVE),
        // 避免embed注入
        Pattern.compile("<\\s*embed[^>]*>(.*?)<\\s*/\\s*embed\\s*>", Pattern.CASE_INSENSITIVE),
        // 避免eval等危险函数
        Pattern.compile("eval\\((.*?)\\)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Function\\((.*?)\\)", Pattern.CASE_INSENSITIVE),
        // 避免expression注入
        Pattern.compile("expression\\((.*?)\\)", Pattern.CASE_INSENSITIVE),
        // 避免url编码绕过
        Pattern.compile("&#x[0-9a-f]{2,4};?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("&#[0-9]{2,4};?", Pattern.CASE_INSENSITIVE),
        // 避免svg onload攻击
        Pattern.compile("<\\s*svg[^>]*>.*?<\\s*/\\s*svg\\s*>", Pattern.CASE_INSENSITIVE),
        // 避免img标签事件注入
        Pattern.compile("<\\s*img[^>]*\\bonerror\\s*=[^>]*>", Pattern.CASE_INSENSITIVE)
    };

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
     * 验证IP地址格式是否有效
     * 支持IPv4和IPv6格式验证，包括压缩格式的IPv6地址
     * 
     * @param ip IP地址字符串
     * @return 是否是有效的IP地址
     */
    private static boolean isValidIP(String ip) {
        if (CharSequenceUtil.isBlank(ip)) {
            return false;
        }
        
        try {
            // 使用Java内置的InetAddress验证，这是最可靠的方法
            InetAddress inetAddress = InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            // 如果InetAddress解析失败，尝试使用正则表达式验证
            LOG.debug("IP地址验证失败: {}", ip, e);
            
            // 完整的IPv4验证正则表达式
            String ipv4Regex = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$";
            
            // 更完整的IPv6验证正则表达式，支持压缩格式
            String ipv6Regex = "^(([0-9a-fA-F]{1,4}:){7}([0-9a-fA-F]{1,4}|:))|" + 
                              "(([0-9a-fA-F]{1,4}:){6}(:[0-9a-fA-F]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|" + 
                              "(([0-9a-fA-F]{1,4}:){5}(((:[0-9a-fA-F]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|" + 
                              "(([0-9a-fA-F]{1,4}:){4}(((:[0-9a-fA-F]{1,4}){1,3})|((:[0-9a-fA-F]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|" + 
                              "(([0-9a-fA-F]{1,4}:){3}(((:[0-9a-fA-F]{1,4}){1,4})|((:[0-9a-fA-F]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|" + 
                              "(([0-9a-fA-F]{1,4}:){2}(((:[0-9a-fA-F]{1,4}){1,5})|((:[0-9a-fA-F]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|" + 
                              "(([0-9a-fA-F]{1,4}:){1}(((:[0-9a-fA-F]{1,4}){1,6})|((:[0-9a-fA-F]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|" + 
                              "(:(((:[0-9a-fA-F]{1,4}){1,7})|((:[0-9a-fA-F]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))$";
            
            return ip.matches(ipv4Regex) || ip.matches(ipv6Regex);
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
    public static boolean isInternalIP(byte[] ip) {
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
        if (ip[0] == (byte) 169 && ip[1] == (byte) 254) {
            return true;
        }
        
        return false;
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
            return isInternalIP(inetAddress.getAddress());
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
     * 过滤可能包含XSS攻击的字符串
     * <p>
     * 该方法使用预定义的正则表达式模式集合来检测和过滤常见的XSS攻击向量
     * </p>
     *
     * @param input 需要过滤的输入字符串
     * @return 过滤后的安全字符串
     */
    public static String filterXSS(String input) {
        if (CharSequenceUtil.isBlank(input)) {
            return input;
        }
        
        String result = input;
        for (Pattern pattern : XSS_PATTERNS) {
            result = pattern.matcher(result).replaceAll("");
        }
        
        // 对HTML实体编码进行处理
        result = result.replaceAll("&", "&amp;");
        result = result.replaceAll("<", "&lt;");
        result = result.replaceAll(">", "&gt;");
        result = result.replaceAll("\"", "&quot;");
        result = result.replaceAll("'", "&#x27;");
        result = result.replaceAll("/", "&#x2F;");
        
        return result;
    }
}
