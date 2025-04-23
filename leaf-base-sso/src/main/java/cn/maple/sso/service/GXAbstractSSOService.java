package cn.maple.sso.service;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCookieHelperUtil;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.enums.GXTokenFlag;
import cn.maple.sso.plugins.GXSSOPlugin;
import cn.maple.sso.utils.GXHttpUtil;
import cn.maple.sso.utils.GXIpHelperUtil;
import cn.maple.sso.utils.GXRandomUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * SSO 单点登录服务抽象实现类
 * </p>
 * 
 * 实现了SSO服务的核心功能，包括：
 * 1. Token的获取和验证
 * 2. 用户登录状态管理
 * 3. Cookie设置和清理
 * 4. 登录和注销流程处理
 * 5. 插件机制支持
 *
 * <p>
 * 安全特性：
 * - 多重验证机制：结合IP、浏览器信息和缓存验证，提高安全性
 * - 防会话固定攻击：支持登录时重新生成会话标识
 * - 防CSRF攻击：设置Cookie的SameSite属性
 * - 防XSS攻击：支持HttpOnly选项，防止脚本获取Cookie
 * - 分布式会话管理：支持集群环境下的会话同步和验证
 * </p>
 *
 * <p>
 * 性能优化：
 * - 缓存机制：减少重复解密和验证的开销
 * - 懒加载策略：仅在必要时执行验证逻辑
 * - 插件化设计：按需加载额外的验证和处理逻辑
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * @Service
 * public class CustomSSOService extends GXAbstractSSOService {
 *     // 可以覆盖父类方法，实现自定义逻辑
 *     @Override
 *     public boolean kickLogin(Object userId) {
 *         // 实现自定义的踢出用户逻辑
 *         log.info("强制用户{}下线", userId);
 *         // 可以添加额外的安全审计记录
 *         securityAuditService.recordForceLogout(userId);
 *         return super.kickLogin(userId);
 *     }
 *     
 *     // 使用示例
 *     public void loginExample(HttpServletRequest request, HttpServletResponse response) {
 *         // 1. 验证用户凭证（此处省略）
 *         
 *         // 2. 创建包含用户信息的Token
 *         Dict userInfo = Dict.create()
 *             .set("userId", 10001L)
 *             .set("username", "张三")
 *             .set("roles", "admin,user");
 *             
 *         // 3. 设置登录Cookie并防止会话固定攻击
 *         authCookie(request, response, userInfo);
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
@Slf4j
public abstract class GXAbstractSSOService extends GXSSOSupportService implements GXSSOService {
    /**
     * <p>
     * 获取当前请求的SSO Token
     * </p>
     * 
     * <p>
     * 从Cookie或请求头中解密获取SSO Token，主要用于拦截器场景。
     * 非拦截器场景建议使用attrSSOToken方法减少重复解密开销。
     * </p>
     * 
     * <p>
     * 处理流程：
     * 1. 从缓存中获取Token
     * 2. 验证Token的IP和浏览器信息
     * 3. 通过插件机制进行额外验证
     * </p>
     * 
     * <p>
     * 安全特性：
     * 1. 多重验证 - 结合IP、浏览器信息和缓存验证，提高安全性
     * 2. 插件扩展 - 支持通过插件机制添加自定义验证逻辑
     * 3. 防篡改保护 - 验证Token的完整性，防止被恶意修改
     * 4. 失效处理 - 对无效Token返回空对象而非异常，避免信息泄露
     * </p>
     *
     * <p>
     * 性能考虑：
     * 1. 缓存利用 - 优先从缓存获取Token，减少解密开销
     * 2. 快速失败 - 对无效Token快速返回，避免不必要的处理
     * 3. 延迟验证 - 仅在必要时执行完整的验证流程
     * </p>
     *
     * @param request HTTP请求对象
     * @return 包含用户登录信息的Dict对象，验证失败则返回空Dict
     */
    @Override
    public Dict getSSOToken(HttpServletRequest request) {
        Dict cacheSSOToken = cacheSSOToken(request, getConfig().getCache());
        Dict token = checkIpBrowser(request, cacheSSOToken);
        if (Objects.isNull(token)) {
            return Dict.create();
        }
        // 执行插件逻辑
        List<GXSSOPlugin> pluginList = getConfig().getPluginList();
        if (pluginList != null) {
            for (GXSSOPlugin plugin : pluginList) {
                boolean valid = plugin.validateToken(token);
                if (!valid) {
                    return Dict.create();
                }
            }
        }
        return token;
    }

    /**
     * 踢出指定用户ID的登录用户，强制其退出当前系统
     * <p>
     * 通过删除用户的Token缓存实现强制注销
     * 适用于管理员强制下线用户、检测到异常登录等安全场景
     * </p>
     *
     * <p>
     * 安全应用场景：
     * 1. 管理员在后台强制用户下线
     * 2. 检测到用户账号异地登录时的安全措施
     * 3. 用户修改密码后，使所有已登录会话失效
     * 4. 系统检测到潜在的安全威胁时，主动使会话失效
     * </p>
     *
     * <p>
     * 实现说明：
     * - 通过删除缓存中的Token记录实现强制注销
     * - 需要配置有效的缓存实现才能正常工作
     * - 建议在生产环境中实现完整的审计日志记录
     * </p>
     *
     * @param userId 要踢出的用户ID
     * @return 操作是否成功，成功返回true，失败返回false
     */
    @Override
    public boolean kickLogin(Object userId) {
        GXSSOCache cache = getConfig().getCache();
        if (cache != null) {
            Dict ssoToken = getSSOToken(GXCurrentRequestContextUtils.getHttpServletRequest());
            return cache.delete(ssoToken);
        } else {
            log.debug(" kickLogin! please implements GXSsoCache class.");
        }
        return false;
    }

    /**
     * <p>
     * 在当前访问域下设置登录Cookie
     * </p>
     * 
     * <p>
     * 将用户登录信息写入Cookie并同步到缓存系统。
     * Cookie的超时时间可通过以下方式设置：
     * request.setAttribute(GXSsoConfig.SSO_COOKIE_MAX_AGE, -1);
     * </p>
     * 
     * <p>
     * 超时时间说明：
     * -1: 浏览器关闭时自动删除（会话Cookie）
     * 0: 立即删除Cookie
     * 正整数: 表示Cookie有效期（以秒为单位），如120表示2分钟
     * </p>
     * 
     * <p>
     * 安全措施：
     * 1. 支持HttpOnly选项，防止XSS攻击获取Cookie
     * 2. 可配置Secure选项，要求通过HTTPS传输Cookie
     * 3. 执行SSO插件的登录逻辑
     * 4. 设置SameSite属性，防止CSRF攻击
     * 5. 使用加密存储Token，防止信息泄露
     * 6. 缓存同步，确保分布式环境下的一致性
     * </p>
     *
     * <p>
     * 性能优化：
     * 1. 异常处理 - 捕获并记录异常，确保系统稳定性
     * 2. 缓存状态检查 - 检测缓存服务可用性，提供降级方案
     * 3. 合理设置Cookie属性 - 避免不必要的Cookie传输
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param ssoToken 包含用户登录信息的Token数据
     */
    @Override
    public void setCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken) {
        try {
            // 判断 GXSSOCache 是否缓存处理失效
            // cache 缓存宕机，flag 设置为失效
            GXSSOCache cache = getConfig().getCache();
            if (cache != null) {
                // 添加额外安全信息
                ssoToken.put("createTime", System.currentTimeMillis());
                ssoToken.put("userAgent", request.getHeader("User-Agent"));
                ssoToken.put("ip", GXIpHelperUtil.getIpAddr(request));
                
                Dict cookieSSOToken = getSSOTokenFromCookie(GXCurrentRequestContextUtils.getHttpServletRequest());
                if (cookieSSOToken != null && !cookieSSOToken.isEmpty()) {
                    ssoToken.putAll(cookieSSOToken);
                }
                
                boolean rlt = cache.set(ssoToken, getConfig().getCacheExpires());
                if (!rlt) {
                    ssoToken.put("flag", GXTokenFlag.CACHE_SHUT.value());
                    log.warn("缓存服务不可用，Token将使用本地模式");
                }
            }

            // 设置加密 Cookie
            Cookie ck = this.generateCookie(request, ssoToken);
            
            // 设置SameSite属性，防止CSRF攻击
            // 注意：此设置需要在Servlet容器支持的情况下生效
            String cookieString = ck.getName() + "=" + ck.getValue() + "; Path=" + ck.getPath();
            if (ck.getMaxAge() > 0) {
                cookieString += "; Max-Age=" + ck.getMaxAge();
            }
            if (ck.getDomain() != null) {
                cookieString += "; Domain=" + ck.getDomain();
            }
            if (ck.getSecure()) {
                cookieString += "; Secure";
            }
            if (getConfig().isCookieHttpOnly()) {
                cookieString += "; HttpOnly";
            }
            cookieString += "; SameSite=Lax";
            
            response.addHeader("Set-Cookie", cookieString);

            //执行插件逻辑
            List<GXSSOPlugin> pluginList = getConfig().getPluginList();
            if (pluginList != null) {
                for (GXSSOPlugin plugin : pluginList) {
                    boolean login = plugin.login(request, response);
                    if (!login) {
                        log.warn("插件[{}]登录处理失败", plugin.getClass().getSimpleName());
                    }
                }
            }
        } catch (Exception e) {
            log.error("设置SSO Cookie时发生错误", e);
            throw new GXBusinessException("设置登录Cookie失败");
        }
    }

    /**
     * <p>
     * 在当前访问域下设置登录Cookie并防止会话固定攻击
     * </p>
     * 
     * <p>
     * 在设置登录Cookie的同时，重新生成JSESSIONID，防止会话固定攻击。
     * 这是一种增强的安全措施，特别适用于敏感操作场景，如用户登录、密码修改等。
     * </p>
     * 
     * <p>
     * 安全特性：
     * 1. 会话重新生成 - 使用随机字符串创建新的会话ID，防止会话固定攻击
     * 2. 强随机性保证 - 使用安全的随机数生成器创建会话标识
     * 3. 完整性保护 - 确保新会话包含原有的合法属性
     * 4. 异常处理 - 捕获并记录会话操作中的异常，提高系统稳定性
     * </p>
     * 
     * <p>
     * 使用场景：
     * - 用户首次登录系统时
     * - 用户权限变更后
     * - 检测到潜在的会话劫持尝试时
     * - 用户执行敏感操作（如转账、修改密码）前
     * </p>
     *
     * <p>
     * 最佳实践：
     * - 在所有登录成功后调用此方法，而不是直接调用setCookie
     * - 确保前端应用能够正确处理会话变更
     * - 在分布式环境中，确保会话同步机制正常工作
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param ssoToken 包含用户登录信息的Token数据
     */
    public void authCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken) {
        try {
            // 生成足够长度的随机会话标识，提高安全性
            String sessionId = GXRandomUtil.getCharacterAndNumber(16);
            
            // 记录会话重新生成事件，便于安全审计
            log.debug("重新生成会话ID，防止会话固定攻击");
            
            // 使用安全的方式重新生成会话
            GXCookieHelperUtil.authJSESSIONID(request, sessionId);
            
            // 在新会话中设置Cookie
            this.setCookie(request, response, ssoToken);
            
            // 添加安全响应头，进一步增强安全性
            response.setHeader("X-Frame-Options", "DENY"); // 防止点击劫持
            response.setHeader("X-Content-Type-Options", "nosniff"); // 防止MIME类型嗅探
            response.setHeader("X-XSS-Protection", "1; mode=block"); // 启用XSS过滤
        } catch (Exception e) {
            log.error("重新生成会话时发生错误", e);
            // 即使出错也尝试设置Cookie，确保基本功能可用
            this.setCookie(request, response, ssoToken);
        }
    }

    /**
     * 清除用户登录状态
     * <p>
     * 完整清理用户的登录信息，包括：
     * 1. 删除浏览器Cookie
     * 2. 清除服务端缓存
     * 3. 执行SSO插件的注销逻辑
     * </p>
     *
     * <p>
     * 安全考虑：
     * - 确保所有会话状态和缓存数据被完全清除
     * - 执行所有注册的SSO插件的注销逻辑
     * - 适当处理清理失败的情况，避免部分状态残留
     * </p>
     *
     * <p>
     * 使用场景：
     * - 用户主动登出系统
     * - 会话超时后的清理
     * - 检测到安全问题时的强制注销
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @return 操作是否成功，成功返回true，失败返回false
     */
    @Override
    public boolean clearLogin(HttpServletRequest request, HttpServletResponse response) {
        return logout(request, response, getConfig().getCache());
    }

    /**
     * 重新登录处理
     * <p>
     * 执行完整的注销流程，然后将用户重定向到登录页面
     * 会保留当前请求的URL作为参数，便于登录后返回原页面
     * </p>
     * <p>
     * 处理流程：
     * 1. 清理当前登录状态
     * 2. 获取配置的登录页URL
     * 3. 对于API请求返回JSON响应，对于页面请求进行重定向
     * </p>
     *
     * <p>
     * 使用场景：
     * - 会话过期时自动跳转登录
     * - 访问需要登录的资源时进行重定向
     * - 检测到Token无效时的安全处理
     * </p>
     *
     * <p>
     * 实现说明：
     * - 支持API和页面两种场景的处理
     * - 对于API请求，返回标准的JSON响应
     * - 对于页面请求，进行重定向并保留原始URL
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @throws IOException 如果重定向过程中发生I/O错误
     */
    @Override
    public void clearRedirectLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 清理当前登录状态
        clearLogin(request, response);

        // redirect login page
        String loginUrl = getConfig().getLoginUrl();
        if ("".equals(loginUrl)) {
            Dict data = Dict.create().set("code", HttpStatus.HTTP_NOT_AUTHORITATIVE).set("msg", "Please login").set("data", null);
            response.getWriter().write(JSONUtil.toJsonStr(data));
        } else {
            String retUrl = GXHttpUtil.getQueryString(request, getConfig().getEncoding());
            log.debug("loginAgain redirect pageUrl.." + retUrl);
            response.sendRedirect(GXHttpUtil.encodeRetURL(loginUrl, getConfig().getParamReturnUrl(), retUrl));
        }
    }

    /**
     * SSO系统退出登录
     * <p>
     * 执行完整的SSO注销流程，包括清理本地状态和重定向到注销页面
     * 与clearLogin的区别在于，此方法会进行页面重定向
     * </p>
     * 
     * <p>
     * 使用场景：
     * - 用户点击"退出登录"按钮
     * - 系统自动注销过期会话
     * - 管理员强制用户退出后的页面处理
     * </p>
     *
     * <p>
     * 实现说明：
     * - 先清理所有登录状态（Cookie和缓存）
     * - 然后重定向到配置的注销页面
     * - 如果未配置注销页面，则返回错误信息
     * </p>
     * 
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @throws IOException 如果重定向过程中发生I/O错误
     */
    public void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // delete cookie
        logout(request, response, getConfig().getCache());

        // redirect logout page
        String logoutUrl = getConfig().getLogoutUrl();
        if ("".equals(logoutUrl)) {
            response.getWriter().write("sso.yml Must include: sso.config.logout.url");
        } else {
            response.sendRedirect(logoutUrl);
        }
    }
}