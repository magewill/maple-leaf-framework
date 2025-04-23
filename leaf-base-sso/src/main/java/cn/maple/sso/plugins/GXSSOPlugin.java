package cn.maple.sso.plugins;

import cn.hutool.core.lang.Dict;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * <p>
 * SSO 插件接口
 * </p>
 * 
 * <p>
 * 该接口定义了SSO系统的插件扩展机制，允许开发者在登录、验证和登出过程中注入自定义逻辑。
 * 通过实现该接口，可以扩展SSO框架的功能，如添加额外的安全验证、集成第三方认证系统、
 * 实现多因素认证等。
 * </p>
 * 
 * <p>
 * 插件工作流程：
 * 1. 用户登录时，框架会调用所有注册插件的login方法
 * 2. 获取Token时，框架会调用所有注册插件的validateToken方法进行验证
 * 3. 用户登出时，框架会调用所有注册插件的logout方法
 * </p>
 * 
 * <p>
 * 安全说明：
 * - 插件应当遵循最小权限原则，只执行必要的操作
 * - 建议在插件中添加适当的日志记录，便于安全审计
 * - 插件的执行顺序可能影响系统行为，应当注意插件之间的依赖关系
 * - 插件中的异常处理应当谨慎，避免泄露敏感信息
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 实现双因素认证插件
 * @Component
 * public class TwoFactorAuthPlugin implements GXSSOPlugin {
 *     @Autowired
 *     private TwoFactorService twoFactorService;
 *     
 *     @Override
 *     public boolean login(HttpServletRequest request, HttpServletResponse response) {
 *         // 获取用户提交的验证码
 *         String code = request.getParameter("authCode");
 *         String username = request.getParameter("username");
 *         
 *         // 验证双因素认证码
 *         return twoFactorService.verifyCode(username, code);
 *     }
 *     
 *     @Override
 *     public boolean validateToken(Dict ssoToken) {
 *         // 检查Token是否包含双因素认证标记
 *         return ssoToken.getBool("twoFactorVerified", false);
 *     }
 *     
 *     @Override
 *     public boolean logout(HttpServletRequest request, HttpServletResponse response) {
 *         // 清理双因素认证相关的会话数据
 *         return true;
 *     }
 * }
 * 
 * // 2. 登录日志记录插件
 * @Component
 * public class LoginLogPlugin implements GXSSOPlugin {
 *     @Autowired
 *     private LoginLogService logService;
 *     
 *     @Override
 *     public boolean login(HttpServletRequest request, HttpServletResponse response) {
 *         // 记录登录信息
 *         String username = request.getParameter("username");
 *         String ip = GXIpHelperUtil.getIpAddr(request);
 *         String userAgent = request.getHeader("User-Agent");
 *         
 *         logService.recordLogin(username, ip, userAgent, true);
 *         return true;
 *     }
 *     
 *     @Override
 *     public boolean logout(HttpServletRequest request, HttpServletResponse response) {
 *         // 记录登出信息
 *         Dict token = GXCurrentRequestContextUtils.getLoginCredentials();
 *         if (token != null) {
 *             String username = token.getStr("username");
 *             String ip = GXIpHelperUtil.getIpAddr(request);
 *             
 *             logService.recordLogout(username, ip);
 *         }
 *         return true;
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 * @see cn.maple.sso.service.GXAbstractSSOService SSO服务抽象实现
 */
public interface GXSSOPlugin {
    /**
     * 登录时调用该方法
     * <p>
     * 在用户登录过程中，框架会调用该方法执行自定义的登录逻辑。
     * 可以在此方法中实现额外的登录验证、记录登录日志、触发登录事件等功能。
     * </p>
     * <p>
     * 该方法在用户凭证验证通过后、生成Token之前被调用。
     * 如果返回false，表示插件验证失败，可能会导致登录流程中断。
     * </p>
     *
     * @param request  HTTP请求对象，包含用户提交的登录信息
     * @param response HTTP响应对象，可用于设置响应头或写入响应内容
     * @return 验证结果，true表示验证通过，false表示验证失败
     */
    boolean login(HttpServletRequest request, HttpServletResponse response);

    /**
     * 登录后获取Token时调用该方法
     * <p>
     * 用于验证Token的合法性，可以实现额外的Token验证逻辑，如：
     * - 检查Token是否过期
     * - 验证Token中的设备信息
     * - 检查用户状态是否正常
     * - 实现Token的多设备互斥登录
     * </p>
     * <p>
     * 该方法在每次请求获取Token时被调用，是实现动态Token验证的关键点。
     * 默认实现直接返回true，表示不做额外验证。
     * </p>
     *
     * @param ssoToken 登录票据，包含用户ID、登录时间等信息
     * @return 验证结果，true表示验证通过，false表示验证失败
     */
    default boolean validateToken(Dict ssoToken) {
        return true;
    }

    /**
     * 退出登录时调用该方法
     * <p>
     * 在用户登出过程中，框架会调用该方法执行自定义的登出逻辑。
     * 可以在此方法中实现清理用户会话、记录登出日志、触发登出事件等功能。
     * </p>
     * <p>
     * 该方法在清除Token缓存之后被调用。
     * 如果返回false，表示插件处理失败，但通常不会影响登出流程的继续执行。
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象，可用于设置响应头或写入响应内容
     * @return 处理结果，true表示处理成功，false表示处理失败
     */
    boolean logout(HttpServletRequest request, HttpServletResponse response);
}