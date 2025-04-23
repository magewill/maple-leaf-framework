package cn.maple.sso.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>
 * 登录用户信息注解
 * </p>
 * 
 * <p>
 * 该注解用于标记需要注入当前登录用户信息的方法参数或局部变量。
 * 通常与参数解析器(ArgumentResolver)配合使用，自动将当前登录用户的信息注入到Controller方法的参数中。
 * </p>
 * 
 * <p>
 * 工作原理：
 * 1. 在Controller方法参数上添加该注解
 * 2. 框架的参数解析器识别到该注解
 * 3. 从当前请求上下文中获取登录用户信息
 * 4. 将用户信息注入到对应的参数中
 * </p>
 * 
 * <p>
 * 安全说明：
 * - 使用该注解可以避免在每个方法中重复获取用户信息的代码
 * - 确保只在已登录的接口中使用，否则可能注入空值
 * - 建议与GXPermissionAnnotation配合使用，确保接口的安全性
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 基本用法 - 注入当前登录用户对象
 * @GetMapping("/user/profile")
 * @GXPermissionAnnotation("user:profile")
 * public Dict getUserProfile(@GXLoginUserAnnotation LoginUser user) {
 *     return Dict.create()
 *         .set("id", user.getId())
 *         .set("username", user.getUsername())
 *         .set("lastLoginTime", user.getLastLoginTime());
 * }
 * 
 * // 2. 与其他参数混合使用
 * @PostMapping("/user/update")
 * @GXPermissionAnnotation("user:update")
 * public void updateUserInfo(@GXLoginUserAnnotation LoginUser currentUser, 
 *                           @RequestBody UserUpdateDTO updateDTO) {
 *     // 验证操作权限，确保用户只能修改自己的信息
 *     if (!currentUser.getId().equals(updateDTO.getUserId())) {
 *         throw new GXBusinessException("无权修改他人信息");
 *     }
 *     userService.updateUser(updateDTO);
 * }
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 * @see cn.maple.sso.service.GXSSOService SSO服务接口
 */
@Target({ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
@Retention(RetentionPolicy.RUNTIME)
public @interface GXLoginUserAnnotation {
}
