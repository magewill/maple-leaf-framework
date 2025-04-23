package cn.maple.sso.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * <p>
 * 授权拦截服务接口
 * </p>
 * 
 * <p>
 * 该接口用于自定义授权拦截规则，实现该接口可以根据业务需求定制不同的授权验证逻辑。
 * 在SSO框架中，该接口通常与GXAuthorizationInterceptor配合使用，用于实现细粒度的权限控制。
 * </p>
 * 
 * <p>
 * 安全说明：
 * - 可以在实现类中集成多种验证机制，如IP白名单、时间限制、设备验证等
 * - 建议在实现类中添加日志记录，便于安全审计和问题排查
 * - 对于敏感操作，可以增加额外的验证步骤
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * @Component
 * public class CustomAuthorizationService implements GXAuthorizationInterceptorService {
 *     @Override
 *     public boolean interceptor(HttpServletRequest request, HttpServletResponse response) {
 *         // 获取当前用户信息
 *         Dict userInfo = GXCurrentRequestContextUtils.getLoginCredentials();
 *         
 *         // 检查用户角色
 *         String role = userInfo.getStr("role");
 *         if ("admin".equals(role)) {
 *             return true; // 管理员直接放行
 *         }
 *         
 *         // 检查IP白名单
 *         String ipAddr = GXIpHelperUtil.getIpAddr(request);
 *         if (!isIpAllowed(ipAddr)) {
 *             throw new GXBusinessException("IP地址不在白名单内");
 *         }
 *         
 *         // 其他业务验证逻辑
 *         return checkOtherConditions(request);
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
public interface GXAuthorizationInterceptorService {
    /**
     * 自定义拦截规则
     * <p>
     * 该方法用于实现具体的授权验证逻辑，可以根据请求信息和当前用户状态进行判断。
     * </p>
     * <p>
     * 返回值说明：
     * - 返回 true：表示验证通过，请求将被放行
     * - 返回 false：表示需要继续执行后续的验证逻辑
     * - 抛出异常：如果请求不满足业务条件，可以直接抛出异常，框架会捕获并处理
     * </p>
     *
     * @param request    HTTP请求对象，包含请求的所有信息
     * @param response   HTTP响应对象，可用于设置响应头或写入响应内容
     * @return 验证结果，true表示放行，false表示继续验证
     * @throws cn.maple.core.framework.exception.GXBusinessException 当验证不通过时可抛出业务异常
     */
    boolean interceptor(HttpServletRequest request, HttpServletResponse response);
}
