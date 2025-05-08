package cn.maple.core.framework.filter;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.text.CharSequenceUtil;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.Getter;
import lombok.SneakyThrows;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XSS过滤处理器
 * <p>
 * 该类通过包装HttpServletRequest对象，对请求参数、请求头和请求体进行XSS过滤处理，
 * 防止跨站脚本攻击。主要处理以下内容：
 * 1. 对JSON请求体内容进行XSS过滤
 * 2. 对请求参数名称和值进行XSS过滤
 * 3. 对请求头名称和值进行XSS过滤
 * </p>
 * <p>
 * 线程安全说明：此类的实例通常由Servlet容器为每个请求创建，不会在线程间共享，因此是线程安全的。
 * 静态的htmlFilter对象是线程安全的，可以安全地在多个请求间共享。
 * </p>
 */
public class GXXssHttpServletRequestWrapper extends HttpServletRequestWrapper {
    /**
     * HTML过滤器，用于过滤XSS攻击内容
     * 静态实例在多线程环境下共享，GXHTMLFilter类本身是线程安全的
     */
    private static final GXHTMLFilter htmlFilter = new GXHTMLFilter();

    /**
     * 原始的HttpServletRequest对象
     * 在某些特殊场景下，可能需要访问未经过滤的原始请求
     * -- GETTER --
     * 获取最原始的request对象
     * 在某些特殊场景下，可能需要访问未经过滤的原始请求
     */
    @Getter
    private final HttpServletRequest orgRequest;

    /**
     * 缓存请求体内容的字节数组
     * 由于InputStream只能读取一次，需要将内容缓存下来以便多次使用
     * 注意：对于大型请求体，这可能会占用较多内存
     */
    private byte[] cacheRequestBody;

    /**
     * 构造函数，创建一个XSS过滤的请求包装器
     *
     * @param request 原始的HTTP请求对象
     */
    @SneakyThrows
    public GXXssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        orgRequest = request;
        String contentType = super.getHeader(HttpHeaders.CONTENT_TYPE);
        // 只缓存JSON类型的请求体，减少内存占用
        if (CharSequenceUtil.containsIgnoreCase(contentType, MediaType.APPLICATION_JSON_VALUE)) {
            try {
                cacheRequestBody = IoUtil.readBytes(request.getInputStream());
                // 防止NPE，确保cacheRequestBody不为null
                if (cacheRequestBody == null) {
                    cacheRequestBody = new byte[0];
                }
            } catch (IOException e) {
                // 确保即使出现异常，cacheRequestBody也不会是null
                cacheRequestBody = new byte[0];
                throw e; // 重新抛出异常，让调用者知道发生了错误
            }
        } else {
            // 非JSON请求不缓存请求体，设置为空数组避免NPE
            cacheRequestBody = new byte[0];
        }
    }

    /**
     * 获取最原始的request对象
     * 如果请求已经被包装为GXXssHttpServletRequestWrapper，则返回其中的原始请求
     * 否则直接返回传入的请求对象
     *
     * @param request 可能被包装过的请求对象
     * @return 原始的HttpServletRequest对象
     */
    public static HttpServletRequest getOrgRequest(HttpServletRequest request) {
        if (request instanceof GXXssHttpServletRequestWrapper) {
            return ((GXXssHttpServletRequestWrapper) request).getOrgRequest();
        }
        return request;
    }

    /**
     * 获取请求体的Reader对象
     * 使用缓存的请求体内容创建BufferedReader，避免多次读取原始输入流
     *
     * @return 包含请求体内容的BufferedReader对象
     * @throws IOException 如果创建Reader时发生IO异常
     */
    @Override
    public BufferedReader getReader() throws IOException {
        // 使用UTF-8编码创建InputStreamReader，确保字符编码一致性
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cacheRequestBody), StandardCharsets.UTF_8));
    }

    /**
     * 获取请求体的输入流
     * 对于JSON类型的请求，会先进行XSS过滤处理，然后返回过滤后内容的输入流
     * 对于非JSON类型的请求，直接返回原始输入流
     *
     * @return ServletInputStream对象，包含可能经过XSS过滤的请求体内容
     * @throws IOException 如果处理输入流时发生IO异常
     */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        // 检查Content-Type，只对JSON类型的请求进行XSS过滤
        String contentType = super.getHeader(HttpHeaders.CONTENT_TYPE);
        if (contentType == null || !CharSequenceUtil.containsIgnoreCase(contentType, MediaType.APPLICATION_JSON_VALUE)) {
            return super.getInputStream();
        }

        // 如果缓存的请求体为空，直接返回原始输入流
        if (cacheRequestBody.length == 0) {
            return super.getInputStream();
        }

        // 将字节数组转换为字符串，使用UTF-8编码
        String json = IoUtil.read(new ByteArrayInputStream(cacheRequestBody), StandardCharsets.UTF_8);
        if (CharSequenceUtil.isBlank(json)) {
            return super.getInputStream();
        }

        // 对JSON内容进行XSS过滤
        json = xssEncode(json);

        // TODO 这里有待优化 START
        /*Object requestBodyData = Objects.requireNonNull(GXCurrentRequestContextUtils.getHttpServletRequest()).getAttribute("JSON_REQUEST_BODY");
        if (ObjectUtil.isEmpty(requestBodyData)) {
            Objects.requireNonNull(GXCurrentRequestContextUtils.getHttpServletRequest()).setAttribute("JSON_REQUEST_BODY", json);
        }*/
        // TODO 这里有待优化 END

        // 创建包含过滤后内容的ByteArrayInputStream
        final ByteArrayInputStream bis = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        // 返回自定义的ServletInputStream实现
        return new ServletInputStream() {
            /**
             * 检查流是否已读取完毕
             * @return 如果流中没有更多数据可读，返回true
             */
            @Override
            public boolean isFinished() {
                return bis.available() == 0;
            }

            /**
             * 检查流是否可读
             * @return 总是返回true，表示流随时可读
             */
            @Override
            public boolean isReady() {
                return true;
            }

            /**
             * 设置读取监听器
             * 由于使用的是内存中的ByteArrayInputStream，不需要异步读取，此方法为空实现
             * @param readListener 读取监听器
             */
            @Override
            public void setReadListener(ReadListener readListener) {
                // ByteArrayInputStream是同步的，不需要异步读取监听器
                if (readListener != null) {
                    // 可以在这里添加日志记录，表明监听器被忽略
                }
            }

            /**
             * 从流中读取下一个字节
             * @return 读取的字节，如果到达流末尾则返回-1
             * @throws IOException 如果读取时发生IO异常
             */
            @Override
            public int read() throws IOException {
                return bis.read();
            }
        };
    }

    /**
     * 获取请求参数值，并对参数名和参数值进行XSS过滤
     *
     * @param name 参数名称
     * @return 经过XSS过滤的参数值，如果参数不存在则返回null
     */
    @Override
    public String getParameter(String name) {
        // 参数名可能包含XSS攻击代码，需要先过滤
        String filteredName = xssEncode(name);
        String value = super.getParameter(filteredName);
        // 参数值也需要进行XSS过滤
        if (CharSequenceUtil.isNotBlank(value)) {
            value = xssEncode(value);
        }
        return value;
    }

    /**
     * 获取请求参数的多个值，并对每个值进行XSS过滤
     *
     * @param name 参数名称
     * @return 经过XSS过滤的参数值数组，如果参数不存在则返回null
     */
    @Override
    public String[] getParameterValues(String name) {
        // 获取原始参数值数组
        String[] parameters = super.getParameterValues(name);
        if (parameters == null || parameters.length == 0) {
            return null;
        }

        // 对数组中的每个值进行XSS过滤
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i] != null) {
                parameters[i] = xssEncode(parameters[i]);
            }
        }
        return parameters;
    }

    /**
     * 获取所有请求参数的Map，并对所有参数值进行XSS过滤
     *
     * @return 包含经过XSS过滤的参数值的Map
     */
    @Override
    public Map<String, String[]> getParameterMap() {
        // 创建一个新的LinkedHashMap，保持参数的顺序
        Map<String, String[]> map = new LinkedHashMap<>();
        // 获取原始参数Map
        Map<String, String[]> parameters = super.getParameterMap();

        // 遍历原始参数Map，对每个参数值进行XSS过滤
        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            final String[] values = entry.getValue();
            final String key = entry.getKey();

            // 对数组中的每个值进行XSS过滤
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null) {
                    values[i] = xssEncode(values[i]);
                }
            }
            map.put(key, values);
        }
        return map;
    }

    /**
     * 获取请求头的值，并对请求头名称和值进行XSS过滤
     *
     * @param name 请求头名称
     * @return 经过XSS过滤的请求头值，如果请求头不存在则返回null
     */
    @Override
    public String getHeader(String name) {
        // 请求头名称可能包含XSS攻击代码，需要先过滤
        String filteredName = xssEncode(name);
        String value = super.getHeader(filteredName);
        // 请求头值也需要进行XSS过滤
        if (CharSequenceUtil.isNotBlank(value)) {
            value = xssEncode(value);
        }
        return value;
    }

    /**
     * 对输入字符串进行XSS过滤
     * 使用GXHTMLFilter进行HTML标签和特殊字符的过滤，防止XSS攻击
     *
     * @param input 需要过滤的输入字符串
     * @return 经过XSS过滤的安全字符串
     */
    private String xssEncode(String input) {
        if (input == null) {
            return null;
        }
        return htmlFilter.filter(input);
    }
}
