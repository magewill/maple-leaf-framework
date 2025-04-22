package cn.maple.sso.service;

import cn.hutool.core.lang.Dict;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <p>
 * SSO 单点登录服务接口
 * </p>
 * 
 * 定义了SSO系统的核心功能接口，包括：
 * 1. 获取和验证Token
 * 2. 设置和清理登录状态
 * 3. 用户强制下线
 * 4. 登录和注销流程
 * 
 * 使用示例：
 * <pre>
 * // 获取当前用户的Token信息
 * Dict userToken = ssoService.getSSOToken(request);
 * Long userId = userToken.getLong("userId");
 * 
 * // 设置用户登录状态
 * Dict loginInfo = Dict.create()
 *     .set("userId", 10001L)
 *     .set("username", "张三")
 *     .set("roles", "admin,user");
 * ssoService.setCookie(request, response, loginInfo);
 * 
 * // 注销用户登录
 * ssoService.clearLogin(request, response);
 * 
 * // 强制某用户下线
 * ssoService.kickLogin(10001L);
 * </pre>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
public interface GXSSOService {
    /**
     * <p>
     * 获取当前请求的登录Token
     * </p>
     * <p>
     * 从请求中获取并验证SSO Token，包括：
     * 1. 从Cookie或请求头中解析Token
     * 2. 验证Token的有效性（IP、浏览器等）
     * 3. 通过插件机制进行扩展验证
     * </p>
     * 
     * @param request HTTP请求对象
     * @return 包含用户登录信息的Dict对象，验证失败则返回空Dict
     */
    Dict getSSOToken(HttpServletRequest request);

    /**
     * <p>
     * 踢出指定用户ID的登录用户，强制其退出系统
     * </p>
     * <p>
     * 通过删除用户的Token缓存实现强制注销，适用场景：
     * 1. 管理员强制下线用户
     * 2. 检测到异常登录时的安全措施
     * 3. 用户在其他设备上登录后，踢出之前的登录会话
     * </p>
     *
     * @param userId 要踢出的用户ID
     * @return 操作是否成功，成功返回true，失败返回false
     */
    boolean kickLogin(Object userId);

    /**
     * <p>
     * 设置用户登录状态，将登录信息写入Cookie
     * </p>
     * <p>
     * 将用户登录信息写入加密Cookie并同步到缓存系统，包括：
     * 1. 加密用户信息并设置到Cookie
     * 2. 配置Cookie的安全属性（HttpOnly、Secure等）
     * 3. 将Token信息同步到缓存系统
     * 4. 执行SSO插件的登录逻辑
     * </p>
     * 
     * @param request HTTP请求对象
     * @param response HTTP响应对象
     * @param ssoToken 包含用户登录信息的Token数据
     */
    void setCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken);

    /**
     * <p>
     * 清理用户登录状态
     * </p>
     * <p>
     * 完整清理用户的登录信息，但不进行页面重定向，包括：
     * 1. 清除客户端Cookie
     * 2. 清除服务端缓存
     * 3. 执行SSO插件的注销逻辑
     * </p>
     * 
     * @param request HTTP请求对象
     * @param response HTTP响应对象
     * @return 操作是否成功，成功返回true，失败返回false
     */
    boolean clearLogin(HttpServletRequest request, HttpServletResponse response);

    /**
     * <p>
     * 退出登录并重定向到登录页
     * </p>
     * <p>
     * 清理当前登录状态，然后将用户重定向到登录页面，适用场景：
     * 1. 会话过期时自动跳转登录
     * 2. 用户主动退出后跳转登录
     * 3. 检测到安全问题强制重新登录
     * </p>
     * 
     * @param request HTTP请求对象
     * @param response HTTP响应对象
     * @throws IOException 如果重定向过程中发生I/O错误
     */
    void clearRedirectLogin(HttpServletRequest request, HttpServletResponse response) throws IOException;
}