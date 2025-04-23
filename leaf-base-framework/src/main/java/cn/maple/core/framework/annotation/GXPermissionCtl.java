package cn.maple.core.framework.annotation;

import java.lang.annotation.*;

/**
 * 权限控制注解
 * <p>
 * 该注解用于在控制器类上标注，指定该控制器所属的权限模块信息。
 * 通过这些信息，系统可以自动构建权限体系，实现细粒度的权限控制。
 * </p>
 * 
 * <p>
 * 主要功能：
 * - 定义控制器所属的权限模块名称和编码
 * - 与权限拦截器配合使用，实现自动化的权限检查
 * - 支持权限的动态配置和管理
 * - 便于构建系统的权限树结构
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 在控制器类上使用注解
 * @RestController
 * @RequestMapping("/api/user")
 * @GXPermissionCtl(moduleName = "用户管理", moduleCode = "system:user")
 * public class UserController {
 *     
 *     @GetMapping("/list")
 *     public Result list() {
 *         // 获取用户列表的业务逻辑
 *         return Result.success(userList);
 *     }
 *     
 *     @PostMapping("/add")
 *     public Result add(@RequestBody UserDTO userDTO) {
 *         // 添加用户的业务逻辑
 *         return Result.success();
 *     }
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 权限拦截器示例：
 * <pre>
 * @Component
 * public class PermissionInterceptor implements HandlerInterceptor {
 *     
 *     @Override
 *     public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
 *         // 获取处理器方法所在的控制器类
 *         Class<?> controllerClass = ((HandlerMethod) handler).getBeanType();
 *         
 *         // 获取权限注解
 *         GXPermissionCtl permissionCtl = controllerClass.getAnnotation(GXPermissionCtl.class);
 *         if (permissionCtl != null) {
 *             // 获取模块编码
 *             String moduleCode = permissionCtl.moduleCode();
 *             
 *             // 检查当前用户是否有该模块的权限
 *             boolean hasPermission = checkPermission(moduleCode);
 *             if (!hasPermission) {
 *                 // 无权限，返回错误信息
 *                 response.setStatus(HttpStatus.FORBIDDEN.value());
 *                 return false;
 *             }
 *         }
 *         
 *         return true;
 *     }
 *     
 *     private boolean checkPermission(String moduleCode) {
 *         // 实现权限检查逻辑
 *         // ...
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author britton
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
public @interface GXPermissionCtl {
    /**
     * 权限所属模块的名字
     * <p>
     * 用于在权限管理界面中显示的模块名称，应当使用易于理解的中文名称
     * </p>
     *
     * @return 模块名称
     */
    String moduleName();

    /**
     * 权限所属模块的code
     * <p>
     * 模块的唯一标识码，通常使用冒号分隔的形式，如：system:user:list
     * 用于在代码中进行权限判断和校验
     * </p>
     *
     * @return 模块编码
     */
    String moduleCode();
}
