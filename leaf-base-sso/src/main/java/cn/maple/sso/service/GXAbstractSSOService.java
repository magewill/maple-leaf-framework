package cn.maple.sso.service;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.enums.GXTokenFlag;
import cn.maple.sso.plugins.GXSSOPlugin;
import cn.maple.core.framework.util.GXCookieHelperUtil;
import cn.maple.sso.utils.GXHttpUtil;
import cn.maple.sso.utils.GXRandomUtil;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 * @author britton britton@126.com
 * @since 2021-09-16
 */
@Slf4j
public abstract class GXAbstractSSOService extends GXSSOSupportService implements GXSSOService {
    /**
     * 获取当前请求的SSO Token
     * <p>
     * 从Cookie或请求头中解密获取SSO Token，主要用于拦截器场景
     * 非拦截器场景建议使用attrSSOToken方法减少重复解密开销
     * </p>
     * <p>
     * 处理流程：
     * 1. 从缓存中获取Token
     * 2. 验证Token的IP和浏览器信息
     * 3. 通过插件机制进行额外验证
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
     * 在当前访问域下设置登录Cookie
     * <p>
     * 将用户登录信息写入Cookie并同步到缓存系统
     * Cookie的超时时间可通过以下方式设置：
     * request.setAttribute(GXSsoConfig.SSO_COOKIE_MAX_AGE, -1);
     * </p>
     * <p>
     * 超时时间说明：
     * -1: 浏览器关闭时自动删除（会话Cookie）
     * 0: 立即删除Cookie
     * 正整数: 表示Cookie有效期（以秒为单位），如120表示2分钟
     * </p>
     * <p>
     * 安全措施：
     * 1. 支持HttpOnly选项，防止XSS攻击获取Cookie
     * 2. 可配置Secure选项，要求通过HTTPS传输Cookie
     * 3. 执行SSO插件的登录逻辑
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param ssoToken 包含用户登录信息的Token数据
     */
    @Override
    public void setCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken) {
        // 判断 GXSSOCache 是否缓存处理失效
        // cache 缓存宕机，flag 设置为失效
        GXSSOCache cache = getConfig().getCache();
        if (cache != null) {
            Dict cookieSSOToken = getSSOTokenFromCookie(GXCurrentRequestContextUtils.getHttpServletRequest());
            ssoToken.putAll(cookieSSOToken);
            boolean rlt = cache.set(ssoToken, getConfig().getCacheExpires());
            if (!rlt) {
                ssoToken.put("flag", GXTokenFlag.CACHE_SHUT.value());
            }
        }

        // 设置加密 Cookie
        Cookie ck = this.generateCookie(request, ssoToken);

        //执行插件逻辑
        List<GXSSOPlugin> pluginList = getConfig().getPluginList();
        if (pluginList != null) {
            for (GXSSOPlugin plugin : pluginList) {
                boolean login = plugin.login(request, response);
                if (!login) {
                    plugin.login(request, response);
                }
            }
        }

        // Cookie设置HttpOnly
        if (getConfig().isCookieHttpOnly()) {
            GXCookieHelperUtil.addHttpOnlyCookie(response, ck);
        } else {
            response.addCookie(ck);
        }
    }

    /**
     * 在当前访问域下设置登录Cookie并防止伪造SESSION_ID攻击
     * <p>
     * 在设置登录Cookie的同时，重新生成JSESSIONID，防止会话固定攻击
     * 这是一种增强的安全措施，特别适用于敏感操作场景
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param ssoToken 包含用户登录信息的Token数据
     */
    public void authCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken) {
        GXCookieHelperUtil.authJSESSIONID(request, GXRandomUtil.getCharacterAndNumber(8));
        this.setCookie(request, response, ssoToken);
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