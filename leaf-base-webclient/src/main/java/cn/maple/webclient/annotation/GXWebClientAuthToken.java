package cn.maple.webclient.annotation;

import java.lang.annotation.*;

/**
 * WebClient认证Token注解
 * <p>
 * 该注解用于标记需要进行WebClient认证Token验证的方法或类。
 * 当一个方法或类被标记了此注解，系统会在执行该方法前自动验证WebClient的认证Token是否有效。
 * 这对于保护需要认证的API接口或服务间调用非常有用，可以防止未授权的访问。
 * </p>
 *
 * <p>
 * 工作原理：
 * 1. 通过AOP切面（GXWebClientAuthTokenAspect）拦截被注解标记的方法
 * 2. 调用GXWebClientService的checkTokenValidity方法验证当前请求中的Token
 * 3. 如果Token无效，抛出GXWebClientAuthTokenException异常阻止方法执行
 * 4. 如果Token有效，则正常执行被拦截的方法
 * </p>
 *
 * <p>
 * 使用场景：
 * 1. 微服务间的安全调用，确保调用方持有有效的认证Token
 * 2. 内部API的访问控制，限制只有持有有效Token的客户端才能访问
 * 3. 第三方系统集成时的认证机制，验证第三方系统的调用权限
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 在类级别应用注解，类中所有方法都会进行Token验证
 * @GXWebClientAuthToken
 * @RestController
 * @RequestMapping("/api/v1/products")
 * public class ProductController {
 *     // 所有方法都会进行Token验证
 *     @GetMapping("/{id}")
 *     public Product getProduct(@PathVariable Long id) {
 *         // 方法实现...
 *     }
 * }
 * <p>
 * // 2. 在方法级别应用注解，只对特定方法进行Token验证
 * @RestController
 * @RequestMapping("/api/v1/users")
 * public class UserController {
 *     // 此方法会进行Token验证
 *     @GXWebClientAuthToken
 *     @GetMapping("/sensitive-data")
 *     public SensitiveData getSensitiveData() {
 *         // 方法实现...
 *     }
 * <p>
 *     // 此方法不会进行Token验证
 *     @GetMapping("/public-data")
 *     public PublicData getPublicData() {
 *         // 方法实现...
 *     }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * 注意事项：
 * 1. 使用此注解前，必须确保系统中已实现GXWebClientService接口
 * 2. 需要在请求头中包含WebClient认证Token（默认为X-Auth-Token字段）
 * 3. Token的生成和验证逻辑由GXWebClientService实现类定义
 * 4. 此注解可与Spring Security等安全框架结合使用，提供多层次的安全保障
 * </p>
 *
 * @author britton gapleaf@63.com
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GXWebClientAuthToken {
}
