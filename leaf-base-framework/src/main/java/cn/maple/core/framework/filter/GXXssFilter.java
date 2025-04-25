package cn.maple.core.framework.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * XSS跨站脚本攻击过滤器
 * <p>
 * 该过滤器用于防止XSS（Cross-Site Scripting）攻击，通过包装HttpServletRequest对象，
 * 对请求参数、请求头和请求体中的恶意脚本进行过滤和转义，确保应用的安全性。
 * </p>
 * 
 * <p>
 * 使用示例：
 * 1. 在web.xml中配置过滤器
 * <pre>
 * &lt;filter&gt;
 *     &lt;filter-name&gt;xssFilter&lt;/filter-name&gt;
 *     &lt;filter-class&gt;cn.maple.core.framework.filter.GXXssFilter&lt;/filter-class&gt;
 * &lt;/filter&gt;
 * &lt;filter-mapping&gt;
 *     &lt;filter-name&gt;xssFilter&lt;/filter-name&gt;
 *     &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 * &lt;/filter-mapping&gt;
 * </pre>
 * 
 * 2. 在Spring Boot应用中配置
 * <pre>
 * @Bean
 * public FilterRegistrationBean&lt;GXXssFilter&gt; xssFilterRegistration() {
 *     FilterRegistrationBean&lt;GXXssFilter&gt; registration = new FilterRegistrationBean&lt;&gt;();
 *     registration.setFilter(new GXXssFilter());
 *     registration.addUrlPatterns("/*");
 *     registration.setName("xssFilter");
 *     registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
 *     return registration;
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 安全说明：
 * 1. 该过滤器通过GXXssHttpServletRequestWrapper类对请求内容进行过滤
 * 2. 主要防护HTML标签注入和JavaScript代码注入等XSS攻击
 * 3. 适用于传统表单提交和JSON API请求
 * </p>
 * 
 * @author maple
 * @see GXXssHttpServletRequestWrapper 实际执行XSS过滤的包装类
 */
public class GXXssFilter implements Filter {

    /**
     * 初始化过滤器
     * <p>
     * 该方法在过滤器实例化后由Servlet容器调用，用于初始化过滤器。
     * 当前实现为空，因为不需要特殊的初始化操作。
     * </p>
     *
     * @param config 过滤器配置对象，包含过滤器的初始化参数
     * @throws ServletException 如果发生影响过滤器初始化的错误
     */
    @Override
    public void init(FilterConfig config) throws ServletException {
        // 当前不需要特殊的初始化操作
    }

    /**
     * 执行过滤操作
     * <p>
     * 该方法是过滤器的核心方法，对每个请求和响应执行XSS过滤操作。
     * 它将原始的HttpServletRequest包装为GXXssHttpServletRequestWrapper，
     * 然后继续过滤器链的处理。
     * </p>
     *
     * @param request  原始的ServletRequest对象
     * @param response ServletResponse对象
     * @param chain    过滤器链，用于调用链中的下一个过滤器或目标资源
     * @throws IOException      如果发生I/O错误
     * @throws ServletException 如果发生影响过滤操作的错误
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        // 将请求包装为XSS过滤的请求包装器
        GXXssHttpServletRequestWrapper xssRequest = new GXXssHttpServletRequestWrapper((HttpServletRequest) request);
        // 继续过滤器链的处理
        chain.doFilter(xssRequest, response);
    }

    /**
     * 销毁过滤器
     * <p>
     * 该方法在过滤器生命周期结束时由Servlet容器调用，用于释放资源。
     * 当前实现为空，因为不需要特殊的资源释放操作。
     * </p>
     */
    @Override
    public void destroy() {
        // 当前不需要特殊的资源释放操作
    }
}