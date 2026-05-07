package cn.maple.sso.utils;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONUtil;
import cn.maple.sso.properties.GXSSOProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;

@Slf4j
public class GXHttpUtil {
    public static final String XML_HTTP_REQUEST = "XMLHttpRequest";

    public static final String X_REQUESTED_WITH = "X-Requested-With";

    private GXHttpUtil() {
    }

    public static boolean isAjax(HttpServletRequest request) {
        return XML_HTTP_REQUEST.equals(request.getHeader(X_REQUESTED_WITH));
    }

    public static void ajaxStatus(HttpServletResponse response, int status, String tip) {
        try {
            response.setContentType("application/json;charset=" + GXSSOProperties.getSsoEncoding());
            response.setStatus(status);
            PrintWriter out = response.getWriter();
            Dict data = Dict.create().set("code", status).set("msg", tip).set("data", null);
            JSONConfig jsonConfig = new JSONConfig();
            jsonConfig.setIgnoreNullValue(false);
            out.print(JSONUtil.toJsonStr(data, jsonConfig));
            out.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    public static String getQueryString(HttpServletRequest request, String encode) throws IOException {
        String url = request.getRequestURL().toString();
        StringBuilder sb = new StringBuilder(url);
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            sb.append(url.contains("?") ? "&" : "?").append(query);
        }
        return URLEncoder.encode(sb.toString(), encode);
    }

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

    public static String encodeRetURL(String url, String retParam, String retUrl) {
        return encodeRetURL(url, retParam, retUrl, null);
    }

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

    public static String decodeURL(String url) {
        if (url == null) {
            return null;
        }
        String retUrl = "";

        try {
            retUrl = URLDecoder.decode(url, GXSSOProperties.getSsoEncoding());
        } catch (UnsupportedEncodingException e) {
            log.error("decodeRetURL error.{} ,{}", url, e.getMessage());
        }

        return retUrl;
    }

    public static boolean isGet(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod());
    }

    public static boolean isPost(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod());
    }

    public static void sendRedirect(HttpServletResponse response, String location) {
        try {
            response.sendRedirect(location);
        } catch (IOException e) {
            log.error("sendRedirect location:{} ,{}", location, e.getMessage());
        }
    }

    public static String requestPlayload(HttpServletRequest request) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = request.getInputStream();
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            char[] charBuffer = new char[128];
            int bytesRead = -1;
            while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                stringBuilder.append(charBuffer, 0, bytesRead);
            }
        }
        return stringBuilder.toString();
    }

    public static String getRequestUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder(request.getScheme());
        url.append("://");
        url.append(getTrustedServerName(request));
        int serverPort = request.getServerPort();
        if (serverPort > 0 && !isDefaultPort(request.getScheme(), serverPort)) {
            url.append(":").append(serverPort);
        }
        url.append(request.getRequestURI());
        if (request.getQueryString() != null) {
            url.append("?").append(request.getQueryString());
        }
        return url.toString();
    }

    private static String getTrustedServerName(HttpServletRequest request) {
        String configuredDomain = GXSSOProperties.getInstance().getCookieDomain();
        if (CharSequenceUtil.isNotBlank(configuredDomain)) {
            return configuredDomain.startsWith(".") ? configuredDomain.substring(1) : configuredDomain;
        }
        return request.getServerName();
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
    }
}
