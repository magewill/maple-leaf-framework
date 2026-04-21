package cn.maple.core.framework.annotation;

import org.springframework.context.annotation.ComponentScan;

import java.lang.annotation.*;

/**
 * 启用Maple Leaf框架的注解
 * <p>
 * 该注解用于在Spring Boot应用程序中启用Maple Leaf框架的功能。
 * 通过在主配置类上添加此注解，系统将自动导入框架的核心配置，并初始化框架所需的组件。
 * </p>
 *
 * <p>
 * 主要功能：
 * - 自动导入框架核心配置类GXFrameworkConfig
 * - 设置最高优先级，确保框架配置在其他配置之前加载
 * - 简化框架的集成过程，无需手动配置多个组件
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 在Spring Boot应用的主类上添加注解
 * @SpringBootApplication
 * @GXEnableLeafFramework
 * public class MyApplication {
 *
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * 注意事项：
 * - 该注解应当添加在Spring Boot的主配置类上
 * - 只需添加一次，不要在多个配置类上重复添加
 * - 框架的具体功能可通过application.yml或application.properties进行配置
 * </p>
 *
 * @author britton
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Documented
@ComponentScan("cn.maple")
//@Import({GXFrameworkConfig.class})
//@Order(Ordered.HIGHEST_PRECEDENCE)
public @interface GXEnableLeafFramework {
}
