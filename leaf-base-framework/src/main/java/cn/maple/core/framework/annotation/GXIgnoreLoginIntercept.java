package cn.maple.core.framework.annotation;

import java.lang.annotation.*;

/**
 * 忽略登录认证注解
 * <p>
 * 该注解用于标记不需要进行登录验证的接口或控制器。
 * 在需要登录认证的系统中，被该注解标记的方法或类将跳过登录验证拦截器，
 * 允许未登录用户直接访问，常用于登录接口、注册接口、公开API等场景。
 * </p>
 * 
 * <p>
 * 使用场景：
 * - 登录、注册、找回密码等身份认证相关接口
 * - 公开的API接口（如获取公共配置、公开内容等）
 * - 健康检查、监控接口
 * - 静态资源访问
 * - 第三方回调接口
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 在方法上使用，只对特定方法生效
 * @RestController
 * @RequestMapping("/api/user")
 * public class UserController {
 *     
 *     // 登录接口不需要登录验证
 *     @PostMapping("/login")
 *     @GXIgnoreLoginIntercept
 *     public Result login(@RequestBody LoginDTO loginDTO) {
 *         // 登录逻辑
 *         return Result.success();
 *     }
 *     
 *     // 注册接口不需要登录验证
 *     @PostMapping("/register")
 *     @GXIgnoreLoginIntercept
 *     public Result register(@RequestBody RegisterDTO registerDTO) {
 *         // 注册逻辑
 *         return Result.success();
 *     }
 *     
 *     // 需要登录验证的接口
 *     @GetMapping("/profile")
 *     public Result getUserProfile() {
 *         // 获取用户信息逻辑
 *         return Result.success();
 *     }
 * }
 * 
 * // 2. 在类上使用，对整个控制器的所有方法生效
 * @RestController
 * @RequestMapping("/api/public")
 * @GXIgnoreLoginIntercept
 * public class PublicController {
 *     
 *     @GetMapping("/config")
 *     public Result getPublicConfig() {
 *         // 获取公共配置
 *         return Result.success();
 *     }
 *     
 *     @GetMapping("/articles")
 *     public Result listPublicArticles() {
 *         // 获取公开文章
 *         return Result.success();
 *     }
 * }
 * 
 * // 3. 在拦截器中实现登录验证逻辑
 * @Component
 * public class LoginInterceptor implements HandlerInterceptor {
 *     
 *     @Override
 *     public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
 *         // 如果不是处理方法，直接放行
 *         if (!(handler instanceof HandlerMethod)) {
 *             return true;
 *         }
 *         
 *         HandlerMethod handlerMethod = (HandlerMethod) handler;
 *         Class<?> clazz = handlerMethod.getBeanType();
 *         Method method = handlerMethod.getMethod();
 *         
 *         // 检查类或方法上是否有GXIgnoreLoginIntercept注解
 *         if (clazz.isAnnotationPresent(GXIgnoreLoginIntercept.class) || 
 *             method.isAnnotationPresent(GXIgnoreLoginIntercept.class)) {
 *             // 有注解，不需要登录验证，直接放行
 *             return true;
 *         }
 *         
 *         // 获取当前用户登录状态
 *         String token = request.getHeader("Authorization");
 *         if (StringUtils.isEmpty(token)) {
 *             // 未登录，返回错误信息
 *             response.setStatus(HttpStatus.UNAUTHORIZED.value());
 *             return false;
 *         }
 *         
 *         // 验证token有效性
 *         // ...
 *         
 *         return true;
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author britton gapleaf@63.com
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GXIgnoreLoginIntercept {
}
