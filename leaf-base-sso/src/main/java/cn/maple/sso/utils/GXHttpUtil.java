package cn.maple.sso.utils;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONUtil;
import cn.maple.sso.properties.GXSSOProperties;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;

/**
 * <p>
 * HTTP工具类
 * </p>
 * 
 * 提供HTTP请求处理的通用方法，主要用于：
 * 1. AJAX请求处理和响应
 * 2. URL编解码和参数处理
 * 3. 请求重定向
 * 4. 请求类型判断
 * 5. 请求内容获取
 * 
 * 在SSO系统中用于处理登录、注销等HTTP交互
 *
 * @author britton birtton@126.com
 * @since 2021-09-16
 */
@Slf4j
public class GXHttpUtil {
    public static final String XML_HTTP_REQUEST = "XMLHttpRequest";

    public static final String X_REQUESTED_WITH = "X-Requested-With";

    /**
     * 私有构造函数，防止实例化工具类
     */
    private GXHttpUtil() {
    }

    /**
     * <p>
     * 判断请求是否为AJAX请求
     * </p>
     * 
     * 通过检查请求头中的X-Requested-With字段判断是否为AJAX请求
     * 在SSO系统中用于区分普通请求和AJAX请求，以便返回不同格式的响应
     *
     * @param request 当前HTTP请求对象
     * @return 如果是AJAX请求返回true，否则返回false
     */
    public static boolean isAjax(HttpServletRequest request) {
        return XML_HTTP_REQUEST.equals(request.getHeader(X_REQUESTED_WITH));
    }

    /**
     * <p>
     * 为AJAX请求设置响应状态和内容
     * </p>
     * 
     * 用于向客户端返回JSON格式的响应数据，主要用于错误处理和状态通知
     * 设置了适当的内容类型和字符集，确保客户端能正确解析
     *
     * @param response HTTP响应对象
     * @param status   HTTP状态码
     * @param tip      提示信息，将包含在响应JSON的msg字段中
     */
    public static void ajaxStatus(HttpServletResponse response, int status, String tip) {
        try {
            response.setContentType("application/json;charset=" + GXSSOProperties.getSsoEncoding());
            response.setStatus(status);
            PrintWriter out = response.getWriter();
            Dict data = Dict.create().set("code", HttpStatus.HTTP_UNAUTHORIZED).set("msg", tip).set("data", null);
            JSONConfig jsonConfig = new JSONConfig();
            jsonConfig.setIgnoreNullValue(false);
            out.print(JSONUtil.toJsonStr(data, jsonConfig));
            out.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    /**
     * <p>
     * 获取当前URL（包含查询参数）并进行编码
     * </p>
     * 
     * 构建完整的请求URL，包括请求路径和查询参数，并进行URL编码
     * 在SSO系统中用于生成重定向URL，特别是在登录成功后返回原始请求页面
     *
     * @param request 请求对象
     * @param encode  URLEncoder编码格式（如UTF-8）
     * @return 编码后的完整URL字符串
     * @throws IOException 如果编码过程中发生错误
     */
    public static String getQueryString(HttpServletRequest request, String encode) throws IOException {
        String url = request.getRequestURL().toString();
        StringBuffer sb = new StringBuffer(url);
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            sb.append(url.contains("?") ? "&" : "?").append(query);
        }
        return URLEncoder.encode(sb.toString(), encode);
    }

    /**
     * <p>
     * 判断当前请求URL是否包含在指定的URL列表中
     * </p>
     * 
     * 用于URL白名单检查，判断当前请求是否为允许的URL
     * 在SSO系统中可用于判断是否需要进行登录拦截
     *
     * @param request 请求对象
     * @param url     以分号(';')分割的URL字符串列表
     * @return 如果当前请求URL包含在列表中返回true，否则返回false
     */
    public static boolean inContainURL(HttpServletRequest request, String url) {
        boolean result = false;
        if (url != null && !url.trim().isEmpty()) {
            String[] urlArr = url.split(";");
            StringBuilder reqUrl = new StringBuilder(request.getRequestURL());
            for (String s : urlArr) {
                if (reqUrl.indexOf(s) > 1) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * <p>
     * 构建带有返回地址参数的URL
     * </p>
     * 
     * 在URL中添加返回地址参数，常用于登录、注销等需要返回原始页面的场景
     * 返回地址会进行URL编码，确保参数传递的安全性
     *
     * @param url      目标跳转地址
     * @param retParam 返回地址参数名
     * @param retUrl   返回地址值
     * @return 构建并编码后的完整URL
     */
    public static String encodeRetURL(String url, String retParam, String retUrl) {
        return encodeRetURL(url, retParam, retUrl, null);
    }

    /**
     * <p>
     * 构建带有返回地址和额外参数的URL
     * </p>
     * 
     * 在URL中添加返回地址参数和其他自定义参数
     * 增强版的encodeRetURL方法，支持添加多个额外参数
     *
     * @param url      目标跳转地址
     * @param retParam 返回地址参数名
     * @param retUrl   返回地址值
     * @param data     额外携带的参数映射，键为参数名，值为参数值
     * @return 构建并编码后的完整URL
     */
    public static String encodeRetURL(String url, String retParam, String retUrl, Map<String, String> data) {
        if (url == null) {
            return null;
        }

        StringBuilder retStr = new StringBuilder(url);
        retStr.append(url.contains("?") ? "&" : "?");
        retStr.append(retParam);
        retStr.append("=");
        try {
            retStr.append(URLEncoder.encode(retUrl, GXSSOProperties.getSsoEncoding()));
        } catch (UnsupportedEncodingException e) {
            log.error("encodeRetURL error.{} , {}", url, e.getMessage());
        }

        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                retStr.append("&").append(entry.getKey()).append("=").append(entry.getValue());
            }
        }

        return retStr.toString();
    }

    /**
     * <p>
     * 对URL进行解码
     * </p>
     * 
     * 将编码后的URL还原为原始形式，使用系统配置的字符集
     * 处理了解码过程中可能出现的异常
     *
     * @param url 需要解码的URL字符串
     * @return 解码后的URL字符串，如果解码失败则返回空字符串
     */
    public static String decodeURL(String url) {
        if (url == null) {
            return null;
        }
        String retUrl = "";

        try {
            retUrl = URLDecoder.decode(url, GXSSOProperties.getSsoEncoding());
        } catch (UnsupportedEncodingException e) {
            log.error("encodeRetURL error.{} ,{}", url, e.getMessage());
        }

        return retUrl;
    }

    /**
     * <p>
     * 判断是否为GET请求
     * </p>
     * 
     * 通过检查请求方法判断是否为HTTP GET请求
     * 用于在不同请求方法下执行不同的处理逻辑
     *
     * @param request 请求对象
     * @return 如果是GET请求返回true，否则返回false
     */
    public static boolean isGet(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod());
    }

    /**
     * <p>
     * 判断是否为POST请求
     * </p>
     * 
     * 通过检查请求方法判断是否为HTTP POST请求
     * 用于在不同请求方法下执行不同的处理逻辑
     *
     * @param request 请求对象
     * @return 如果是POST请求返回true，否则返回false
     */
    public static boolean isPost(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod());
    }

    /**
     * <p>
     * 执行HTTP重定向
     * </p>
     * 
     * 将客户端重定向到指定的URL地址
     * 封装了重定向过程中的异常处理
     *
     * @param response HTTP响应对象
     * @param location 重定向目标URL
     */
    public static void sendRedirect(HttpServletResponse response, String location) {
        try {
            response.sendRedirect(location);
        } catch (IOException e) {
            log.error("sendRedirect location:{} ,{}", location, e.getMessage());
        }
    }

    /**
     * <p>
     * 获取请求体(Request Payload)内容
     * </p>
     * 
     * 读取HTTP请求体中的原始内容，通常用于处理JSON、XML等格式的POST数据
     * 使用了try-with-resources确保资源正确关闭，防止内存泄漏
     *
     * @param request HTTP请求对象
     * @return 请求体内容字符串
     * @throws IOException 如果读取过程中发生I/O错误
     */
    public static String requestPlayload(HttpServletRequest request) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = request.getInputStream();
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            char[] charBuffer = new char[128];
            int bytesRead = -1;
            while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                stringBuilder.append(charBuffer, 0, bytesRead);
            }
        } catch (IOException ex) {
            throw ex;
        }
        return stringBuilder.toString();
    }

    /**
     * <p>
     * 获取当前完整请求URL
     * </p>
     * 
     * 构建包含协议、主机名、端口、路径和查询参数的完整URL
     * 与getQueryString方法不同，此方法返回未编码的原始URL
     *
     * @param request HTTP请求对象
     * @return 完整的请求URL字符串
     */
    public static String getRequestUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder(request.getScheme());
        // 请求协议 http,https
        url.append("://");
        // 请求服务器
        url.append(request.getHeader("host"));
        // 工程名
        url.append(request.getRequestURI());
        if (request.getQueryString() != null) {
            // 请求参数
            url.append("?").append(request.getQueryString());
        }
        return url.toString();
    }
}
