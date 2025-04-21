package cn.maple.core.framework.config.web;

import cn.maple.core.framework.filter.GXBaseRequestLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web基础配置类
 * <p>
 * 该配置类提供了Web应用的基础配置，包括请求日志记录过滤器等组件。
 * 通过Spring的@Configuration注解，在应用启动时自动注册相关Bean。
 * 该配置类是线程安全的，所有Bean都是单例模式，在应用上下文中共享。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 在Spring Boot应用中，只需引入该配置类所在的包即可自动启用配置
 * // 例如在启动类上添加包扫描
 * @SpringBootApplication(scanBasePackages = {"cn.maple.core.framework"})
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author britton chen <britton@126.com>
 */
@Configuration
public class GXBaseWebConfig {
    /**
     * 创建请求日志记录过滤器Bean
     * <p>
     * 该方法创建并配置GXBaseRequestLoggingFilter实例，用于记录HTTP请求的详细信息。
     * 过滤器会记录请求的URL、请求头、请求参数、请求体等信息，便于调试和问题排查。
     * </p>
     * <p>
     * 线程安全说明：
     * - 该过滤器是线程安全的，每个请求都有独立的处理上下文
     * - 过滤器在应用启动时创建单例，在多线程环境中共享使用
     * </p>
     *
     * @return 配置好的GXBaseRequestLoggingFilter实例
     */
    @Bean
    public GXBaseRequestLoggingFilter requestLoggingFilter() {
        return new GXBaseRequestLoggingFilter();
    }
}