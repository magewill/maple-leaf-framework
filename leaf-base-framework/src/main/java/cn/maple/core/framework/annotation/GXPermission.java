package cn.maple.core.framework.annotation;

import java.lang.annotation.*;

/**
 * 权限控制注解
 * <p>
 * 该注解用于方法级别的权限控制，通过指定权限名称和权限码，
 * 可以在拦截器或AOP中进行权限验证，确保只有具备特定权限的用户才能访问被注解的方法。
 * </p>
 * 
 * <p>
 * 使用场景：
 * - 控制API接口的访问权限
 * - 实现细粒度的功能权限控制
 * - 管理后台的权限管理
 * - 多租户系统的权限隔离
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 在控制器方法上使用
 * @RestController
 * @RequestMapping("/api/admin")
 * public class AdminController {
 *     
 *     @GetMapping("/users")
 *     @GXPermission(permissionName = "用户列表查看", permissionCode = "user:list", 
 *                  moduleCode = "user", moduleName = "用户管理")
 *     public Result listUsers() {
 *         // 业务逻辑
 *         return Result.success();
 *     }
 *     
 *     @PostMapping("/users")
 *     @GXPermission(permissionName = "用户创建", permissionCode = "user:create", 
 *                  moduleCode = "user", moduleName = "用户管理")
 *     public Result createUser(@RequestBody UserDTO userDTO) {
 *         // 业务逻辑
 *         return Result.success();
 *     }
 * }
 * 
 * // 2. 在拦截器中实现权限验证
 * @Component
 * public class PermissionInterceptor implements HandlerInterceptor {
 *     
 *     @Autowired
 *     private UserService userService;
 *     
 *     @Override
 *     public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
 *         if (!(handler instanceof HandlerMethod)) {
 *             return true;
 *         }
 *         
 *         HandlerMethod handlerMethod = (HandlerMethod) handler;
 *         GXPermission permission = handlerMethod.getMethodAnnotation(GXPermission.class);
 *         
 *         if (permission == null) {
 *             return true;
 *         }
 *         
 *         // 获取当前用户
 *         Long userId = getCurrentUserId(request);
 *         // 获取权限码
 *         String permissionCode = permission.permissionCode();
 *         
 *         // 验证用户是否有权限
 *         boolean hasPermission = userService.hasPermission(userId, permissionCode);
 *         
 *         if (!hasPermission) {
 *             // 无权限，返回错误信息
 *             response.setStatus(HttpStatus.FORBIDDEN.value());
 *             return false;
 *         }
 *         
 *         return true;
 *     }
 * }
 * </pre>
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.METHOD)
public @interface GXPermission {
    /**
     * 权限名字
     * <p>
     * 权限的显示名称，用于在界面上展示，如"用户管理"、"订单查看"等。
     * 应当使用易于理解的中文名称，便于管理员分配权限时理解。
     * </p>
     * 
     * @return 权限名称字符串
     */
    String permissionName();

    /**
     * 权限码
     * <p>
     * 权限的唯一标识符，用于系统内部权限验证，如"user:list"、"order:create"等。
     * 建议使用资源名:操作名的格式，便于系统解析和权限管理。
     * </p>
     * 
     * @return 权限码字符串
     */
    String permissionCode();

    /**
     * 权限所属模块
     * <p>
     * 权限所属的功能模块编码，用于对权限进行分组管理，如"system"、"user"、"order"等。
     * 可选参数，默认为空字符串。
     * </p>
     * 
     * @return 模块编码字符串
     */
    String moduleCode() default "";

    /**
     * 权限所属模块名字
     * <p>
     * 权限所属的功能模块名称，用于在界面上展示，如"系统管理"、"用户管理"、"订单管理"等。
     * 可选参数，默认为空字符串。
     * </p>
     * 
     * @return 模块名称字符串
     */
    String moduleName() default "";
}
