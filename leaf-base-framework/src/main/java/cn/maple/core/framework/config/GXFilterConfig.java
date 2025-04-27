package cn.maple.core.framework.config;

import cn.maple.core.framework.filter.GXXssFilter;
import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.web.filter.ServletRequestPathFilter;

/**
 * Web过滤器配置类
 * <p>
 * 该配置类负责注册和配置应用中使用的各种Servlet过滤器，主要包括XSS防护过滤器。
 * 通过FilterRegistrationBean进行配置，可以精确控制过滤器的执行顺序和URL匹配模式。
 * <p>
 * 安全说明：
 * 1. XSS过滤器应用于所有请求路径，提供全局防护
 * 2. 过滤器优先级设置为最高(Integer.MAX_VALUE)，确保在其他过滤器之前执行
 * 3. 仅处理REQUEST类型的请求，避免对内部转发和包含的请求重复处理
 * <p>
 * 使用示例：
 * <pre>
 * // 该配置会自动应用，无需额外配置
 * // 如需自定义XSS过滤规则，可以扩展GXXssFilter类
 * </pre>
 *
 * @author britton chen <britton@126.com>
 */
@Configuration
@Slf4j
public class GXFilterConfig {
    /**
     * 配置XSS防护过滤器
     * <p>
     * 该方法创建并配置XSS防护过滤器的注册Bean，应用于所有URL路径。
     * XSS过滤器会拦截所有HTTP请求，并对请求参数和请求体进行清洗，防止XSS攻击。
     * <p>
     * 线程安全说明：
     * - FilterRegistrationBean是线程安全的，在应用启动时完成配置
     * - GXXssFilter的实例在每个请求中都是线程隔离的，不存在并发问题
     * - 过滤器的执行顺序确保了安全检查在其他处理之前完成
     *
     * @return 配置好的FilterRegistrationBean实例
     */
    @Bean
    @ConditionalOnExpression("${maple.framework.web.filter.xss-filter.enabled:true}")
    public FilterRegistrationBean<GXXssFilter> xssFilterRegistration() {
        FilterRegistrationBean<GXXssFilter> registration = new FilterRegistrationBean<>();
        // 只处理原始请求，不处理转发和包含的请求
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        // 设置XSS过滤器
        registration.setFilter(new GXXssFilter());
        // 应用于所有URL路径
        registration.addUrlPatterns("/*");
        // 设置过滤器名称
        registration.setName("xssFilter");
        // 设置过滤器优先级为最高
        registration.setOrder(Integer.MAX_VALUE);

        log.info("XSS防护过滤器已配置，应用于所有请求路径");
        return registration;
    }

    /**
     * 配置RequestContextFilter
     * <p>
     * 该方法创建并配置RequestContextFilter的注册Bean，用于在子线程中获取上下文对象。
     * RequestContextFilter会将当前请求的上下文对象存储在线程本地变量中，
     * 使得子线程可以通过RequestContextHolder获取到当前请求的上下文对象。
     * <p>
     * 完整使用示例：
     * <pre>
     * // 在子线程中获取上下文对象
     * RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
     * HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
     * // 继续处理子线程的逻辑
     * </pre>
     * <p>
     * 线程安全说明：
     * - RequestContextFilter是线程安全的，在应用启动时完成配置
     * - RequestContextHolder的使用是线程安全的，每个线程都有自己的上下文对象
     * - 过滤器的执行顺序确保了上下文对象的设置在其他处理之前完成
     *
     * @return 配置好的FilterRegistrationBean实例
     */
    @Bean
    @ConditionalOnExpression("${maple.framework.web.filter.request-context-filter.enabled:false}")
    RequestContextFilter requestContextFilter() {
        log.info("RequestContextFilter已配置，子线程也可以获取上下文对象");
        RequestContextFilter filter = new RequestContextFilter();
        // 子线程也可以获取上下文对象
        filter.setThreadContextInheritable(true);
        return filter;
    }

    /**
     * 配置ServletRequestPathFilter
     * <p>
     * 该方法创建并配置ServletRequestPathFilter的注册Bean，用于在子线程中获取请求路径。
     * ServletRequestPathFilter会将当前请求的路径存储在线程本地变量中，
     * 使得子线程可以通过ServletRequestAttributes获取到当前请求的路径。
     * <p>
     * 完整使用示例：
     * <pre>
     * // 在子线程中获取请求路径
     * RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
     * String path = ((ServletRequestAttributes) attributes).getRequestPath();
     * // 继续处理子线程的逻辑
     * </pre>
     * <p>
     * 线程安全说明：
     * - ServletRequestPathFilter是线程安全的，在应用启动时完成配置
     * - ServletRequestAttributes的使用是线程安全的，每个线程都有自己的请求属性
     * - 过滤器的执行顺序确保了请求路径的设置在其他处理之前完成
     *
     * @return 配置好的FilterRegistrationBean实例
     */
    @Bean
    @ConditionalOnExpression("${maple.framework.web.filter.servlet-request-path-filter.enabled:false}")
    ServletRequestPathFilter servletRequestPathFilter() {
        log.info("ServletRequestPathFilter已配置，子线程也可以获取请求路径");
        ServletRequestPathFilter filter = new ServletRequestPathFilter();
        return filter;
    }

    /**
     * 配置CommonsRequestLoggingFilter
     * <p>
     * 该方法创建并配置CommonsRequestLoggingFilter的注册Bean，用于记录请求日志。
     * CommonsRequestLoggingFilter会将请求的详细信息（如请求方法、URL、参数等）记录到日志中，
     * 便于调试和排查问题。
     * <p>
     * 完整使用示例：
     * <pre>
     * // 在应用中使用CommonsRequestLoggingFilter
     * // 当请求到达时，请求日志会被记录
     * // 日志格式为：[yyyy-MM-dd HH:mm:ss.SSS] [HTTP METHOD] [REQUEST URL] [PARAMETERS] [HEADERS]
     * </pre>
     * <p>
     * 线程安全说明：
     * - CommonsRequestLoggingFilter是线程安全的，在应用启动时完成配置
     * - 过滤器的执行顺序确保了日志记录在其他处理之前完成
     *
     * @return 配置好的FilterRegistrationBean实例
     */
    @Bean
    @ConditionalOnExpression("${maple.framework.web.filter.commons-request-logging-filter.enabled:false}")
    CommonsRequestLoggingFilter commonsRequestLoggingFilter() {
        log.info("CommonsRequestLoggingFilter已配置，请求日志会被记录");
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludeHeaders(true);
        return filter;
    }
}
