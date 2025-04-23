package cn.maple.core.framework.annotation;

import java.lang.annotation.*;

/**
 * Feign请求头透传注解
 * <p>
 * 该注解用于在微服务间调用时，将指定的HTTP请求头信息从调用方透传到被调用方。
 * 常用于传递用户认证信息、租户ID、追踪ID等上下文信息，确保微服务调用链中的上下文一致性。
 * </p>
 * 
 * <p>
 * 使用场景：
 * - 传递认证令牌（如JWT Token）
 * - 传递租户标识（多租户系统）
 * - 传递请求追踪ID（分布式追踪）
 * - 传递语言偏好、时区等用户上下文
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 在Feign客户端接口方法上使用
 * @FeignClient(name = "user-service", url = "${service.user.url}")
 * public interface UserFeignClient {
 *     
 *     // 透传Authorization和X-Tenant-Id两个请求头
 *     @GetMapping("/api/users/{id}")
 *     @GXFeignHeader(names = {"Authorization", "X-Tenant-Id"})
 *     UserVO getUserById(@PathVariable("id") Long id);
 *     
 *     // 透传所有追踪相关的请求头
 *     @PostMapping("/api/users")
 *     @GXFeignHeader(names = {"X-Request-ID", "X-Trace-ID"})
 *     Result createUser(@RequestBody UserDTO userDTO);
 * }
 * 
 * // 2. 在Feign拦截器中实现请求头透传
 * @Component
 * public class FeignHeaderInterceptor implements RequestInterceptor {
 *     
 *     @Autowired
 *     private RequestContextHolder requestContextHolder;
 *     
 *     @Override
 *     public void apply(RequestTemplate template) {
 *         // 获取当前请求上下文
 *         ServletRequestAttributes attributes = requestContextHolder.getRequestAttributes();
 *         if (attributes == null) {
 *             return;
 *         }
 *         
 *         // 获取当前请求
 *         HttpServletRequest request = attributes.getRequest();
 *         
 *         // 获取当前方法上的GXFeignHeader注解
 *         Method method = getMethodFromTemplate(template);
 *         if (method == null) {
 *             return;
 *         }
 *         
 *         GXFeignHeader feignHeader = method.getAnnotation(GXFeignHeader.class);
 *         if (feignHeader == null) {
 *             return;
 *         }
 *         
 *         // 获取需要透传的请求头名称
 *         String[] headerNames = feignHeader.names();
 *         
 *         // 将指定的请求头添加到Feign请求中
 *         for (String headerName : headerNames) {
 *             String headerValue = request.getHeader(headerName);
 *             if (headerValue != null) {
 *                 template.header(headerName, headerValue);
 *             }
 *         }
 *     }
 *     
 *     // 从请求模板中获取方法信息
 *     private Method getMethodFromTemplate(RequestTemplate template) {
 *         // 实现逻辑...
 *         return method;
 *     }
 * }
 * </pre>
 * </p>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GXFeignHeader {
    /**
     * 需要透传到被调用方的header参数名字
     * <p>
     * 指定需要从当前请求中获取并透传到Feign调用的目标服务的HTTP请求头名称列表。
     * 如果指定的请求头在当前请求中不存在，则不会添加到Feign请求中。
     * 如果不指定任何请求头名称（默认值），则不会透传任何请求头。
     * </p>
     * 
     * @return 需要透传的请求头名称数组
     */
    String[] names() default {};
}
