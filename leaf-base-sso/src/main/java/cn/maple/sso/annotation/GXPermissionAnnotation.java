package cn.maple.sso.annotation;

import cn.maple.sso.enums.GXAction;

import java.lang.annotation.*;

/**
 * <p>
 * SSO权限控制注解
 * </p>
 * 
 * <p>
 * 该注解用于标记需要进行权限验证的方法，通常应用在Controller层的方法上。
 * 框架会在请求到达被注解的方法前，通过GXSSOPermissionInterceptor拦截器进行权限验证。
 * </p>
 * 
 * <p>
 * 权限验证流程：
 * 1. 拦截器检测到方法上的GXPermissionAnnotation注解
 * 2. 获取注解中的权限值(value)和动作类型(action)
 * 3. 根据动作类型决定是否跳过权限验证
 * 4. 如果不跳过，则调用GXSSOAuthorization接口验证用户是否拥有指定权限
 * </p>
 * 
 * <p>
 * 安全说明：
 * - 建议对所有需要权限控制的接口都添加此注解
 * - 可以通过action属性灵活控制验证行为
 * - 权限值(value)应当遵循一定的命名规范，便于管理和审计
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 基本用法 - 要求用户拥有"user:view"权限
 * @GetMapping("/users")
 * @GXPermissionAnnotation("user:view")
 * public List<User> listUsers() {
 *     return userService.findAll();
 * }
 * 
 * // 2. 跳过权限验证 - 适用于公开接口
 * @GetMapping("/public/info")
 * @GXPermissionAnnotation(action = GXAction.Skip)
 * public Dict publicInfo() {
 *     return Dict.create().set("version", "1.0");
 * }
 * 
 * // 3. 组合权限控制 - 复杂权限表达式
 * @PostMapping("/users/{id}/lock")
 * @GXPermissionAnnotation("user:lock")
 * public void lockUser(@PathVariable Long id) {
 *     userService.lockUser(id);
 * }
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 * @see cn.maple.sso.web.interceptor.GXSSOPermissionInterceptor 权限拦截器
 * @see cn.maple.sso.oauth.GXSSOAuthorization 权限验证接口
 * @see cn.maple.sso.enums.GXAction 权限动作枚举
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GXPermissionAnnotation {
    /**
     * 权限内容
     * <p>
     * 用于指定访问该方法所需的权限标识，通常采用冒号分隔的形式，如：
     * - 模块:操作 - 例如 "user:create"
     * - 模块:子模块:操作 - 例如 "system:user:delete"
     * </p>
     * <p>
     * 当值为空字符串时，权限验证的行为由GXSSOPermissionInterceptor的nothingAnnotationPass属性决定
     * </p>
     *
     * @return 权限标识字符串
     */
    String value() default "";

    /**
     * 执行动作
     * <p>
     * 用于控制权限验证的行为：
     * - GXAction.Normal: 正常验证权限
     * - GXAction.Skip: 跳过权限验证，适用于公开接口
     * </p>
     *
     * @return 权限动作枚举值
     * @see GXAction
     */
    GXAction action() default GXAction.Normal;
}