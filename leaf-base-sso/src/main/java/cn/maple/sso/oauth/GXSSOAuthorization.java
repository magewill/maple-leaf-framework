package cn.maple.sso.oauth;

import cn.hutool.core.lang.Dict;

/**
 * <p>
 * SSO 权限授权接口
 * </p>
 * 
 * <p>
 * 该接口定义了SSO系统中权限验证的核心方法，用于判断用户是否拥有特定权限。
 * 框架会在权限拦截器(GXSSOPermissionInterceptor)中调用该接口的实现类，
 * 根据返回结果决定是否允许用户访问受保护的资源。
 * </p>
 * 
 * <p>
 * 权限验证流程：
 * 1. 拦截器获取当前请求的Token信息
 * 2. 从请求或注解中获取所需的权限标识
 * 3. 调用isPermitted方法进行权限验证
 * 4. 根据返回结果决定是否放行请求
 * </p>
 * 
 * <p>
 * 安全说明：
 * - 实现类应当提供高效且安全的权限验证逻辑
 * - 建议实现细粒度的权限控制，支持多种权限表达式
 * - 可以集成RBAC、ABAC等权限模型
 * - 应当包含适当的日志记录，便于安全审计
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * @Component
 * public class CustomAuthorization implements GXSSOAuthorization {
 *     @Autowired
 *     private PermissionService permissionService;
 *     
 *     @Override
 *     public boolean isPermitted(Dict token, String permission) {
 *         // 获取用户ID
 *         Long userId = token.getLong("userId");
 *         if (userId == null) {
 *             return false;
 *         }
 *         
 *         // 超级管理员拥有所有权限
 *         if (permissionService.isAdmin(userId)) {
 *             return true;
 *         }
 *         
 *         // 检查用户是否拥有指定权限
 *         return permissionService.hasPermission(userId, permission);
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 * @see cn.maple.sso.web.interceptor.GXSSOPermissionInterceptor 权限拦截器
 * @see cn.maple.sso.annotation.GXPermissionAnnotation 权限注解
 */
public interface GXSSOAuthorization {
    /**
     * 判断用户是否拥有指定权限
     * <p>
     * 该方法是权限验证的核心逻辑，用于判断Token代表的用户是否拥有访问特定资源的权限。
     * 实现类可以根据业务需求，设计不同的权限验证策略，如：
     * - 基于角色的访问控制(RBAC)
     * - 基于属性的访问控制(ABAC)
     * - 自定义的权限表达式解析
     * </p>
     * 
     * @param token      用户Token信息，包含用户ID、角色等身份数据
     * @param permission 权限标识，通常是一个字符串表达式，如"user:view"、"system:config:edit"等
     * @return 如果用户拥有该权限返回true，否则返回false
     */
    boolean isPermitted(Dict token, String permission);
}