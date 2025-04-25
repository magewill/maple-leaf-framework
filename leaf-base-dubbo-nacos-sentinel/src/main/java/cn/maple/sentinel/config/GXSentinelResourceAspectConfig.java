package cn.maple.sentinel.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sentinel资源切面配置类
 * <p>
 * 该配置类用于启用Sentinel注解支持，允许在应用中使用@SentinelResource注解进行资源定义和限流、熔断等规则配置。
 * 通过注册SentinelResourceAspect Bean，实现对标注了@SentinelResource注解的方法进行拦截和处理。
 * </p>
 * 
 * <p>
 * 主要功能：
 * <ul>
 *   <li>启用@SentinelResource注解支持</li>
 *   <li>自动拦截和处理标注了@SentinelResource的方法</li>
 *   <li>根据配置的规则对资源进行流量控制、熔断降级等处理</li>
 * </ul>
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 在启动类中引入该配置
 * @SpringBootApplication
 * @Import(GXSentinelResourceAspectConfig.class)
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * 
 * // 在服务类中使用@SentinelResource注解
 * @Service
 * public class UserService {
 *     @SentinelResource(value = "getUserById", blockHandler = "handleGetUserByIdBlock")
 *     public User getUserById(Long id) {
 *         // 业务逻辑
 *         return userRepository.findById(id);
 *     }
 *     
 *     // 限流降级处理方法
 *     public User handleGetUserByIdBlock(Long id, BlockException ex) {
 *         // 降级逻辑
 *         log.warn("获取用户信息被限流，用户ID: {}", id, ex);
 *         return new User().setName("默认用户");
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author britton
 */
@Configuration
public class GXSentinelResourceAspectConfig {
    /**
     * 创建SentinelResourceAspect Bean
     * <p>
     * 注册SentinelResourceAspect切面，用于拦截和处理@SentinelResource注解。
     * 该切面将自动对标注了@SentinelResource注解的方法进行增强，实现限流、熔断等功能。
     * </p>
     * 
     * @return SentinelResourceAspect 实例
     */
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }
}
