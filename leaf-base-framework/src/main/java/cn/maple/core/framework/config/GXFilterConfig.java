package cn.maple.core.framework.config;

import cn.maple.core.framework.filter.GXXssFilter;
import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
