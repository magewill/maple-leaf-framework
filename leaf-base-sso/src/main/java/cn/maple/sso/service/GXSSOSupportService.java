package cn.maple.sso.service;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.enums.GXTokenFlag;
import cn.maple.sso.plugins.GXSSOPlugin;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.utils.GXBrowserUtil;
import cn.maple.core.framework.util.GXCookieHelperUtil;
import cn.maple.sso.utils.GXIpHelperUtil;
import cn.maple.sso.utils.GXSSOHelperUtil;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * SSO 单点登录服务支持类
 * </p>
 * 
 * 提供SSO服务的基础功能支持，包括：
 * 1. 配置管理
 * 2. Token获取和验证
 * 3. Cookie处理
 * 4. IP和浏览器验证
 * 5. 登录状态管理
 * 
 * 该类作为SSO服务的基础类，为子类提供通用功能实现
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
@Slf4j
public abstract class GXSSOSupportService {
    /**
     * 获取SSO系统配置
     * 
     * @return SSO配置对象，包含系统所有配置参数
     */
    public GXSSOProperties getConfig() {
        return GXSSOProperties.getInstance();
    }

    // ------------------------------- 客户端相关方法 -------------------------------

    /**
     * 获取当前请求中的SSOToken
     * <p>
     * 从请求属性中获取Token，此属性通常在过滤器或拦截器中设置
     * 此方法主要用于业务系统中获取已验证的Token，避免重复解密
     * </p>
     *
     * @param request HTTP请求对象
     * @return 包含用户登录信息的Dict对象，如果不存在则返回null
     */
    public Dict attrSSOToken(HttpServletRequest request) {
        Object attribute = request.getAttribute(GXSSOConstant.SSO_TOKEN_ATTR);
        return Convert.convert(Dict.class, attribute);
    }

    /**
     * 处理SSOToken的缓存逻辑
     * <p>
     * 判断SSOToken是否在缓存中存在并有效，主要流程：
     * 1. 从Cookie或请求头中获取Token
     * 2. 从缓存中查询对应的Token数据
     * 3. 验证缓存Token与请求Token的一致性
     * 4. 处理缓存宕机等异常情况
     * </p>
     *
     * @param request HTTP请求对象
     * @param cache SSO缓存实现对象
     * @return 验证通过返回有效的Token数据，否则返回空Dict
     */
    protected Dict cacheSSOToken(HttpServletRequest request, GXSSOCache cache) {
        // 如果缓存组件存在则使用缓存中存储的token
        if (cache != null) {
            Dict requestToken = getSSOTokenFromCookie(request);
            if (requestToken == null) {
                // 未登录
                log.info("SSO 用户未登录....");
                return Dict.create();
            }

            Dict cacheToken = cache.get(getConfig().getCacheExpires(), requestToken);
            if (cacheToken.isEmpty()) {
                // 开启缓存且失效，清除 Cookie 退出 , 返回 null
                log.info("cacheSSOToken GXSsoToken is null.");
                return Dict.create();
            } else {
                // 开启缓存，判断是否宕机：
                // 1、缓存正常，返回 tk
                // 2、缓存宕机，执行读取 Cookie 逻辑
                if (!Objects.equals(cacheToken.getInt("flag"), GXTokenFlag.CACHE_SHUT.value())) {
                    if (cache.verifyTokenConsistency(cacheToken, requestToken)) {
                        return cacheToken;
                    } else {
                        log.info("Login time is not consistent or kicked out.");
                        request.setAttribute(GXSSOConstant.SSO_KICK_FLAG, GXSSOConstant.SSO_KICK_USER);
                        return Dict.create();
                    }
                }
            }
        }

        // GXSsoCache 为 null 执行以下逻辑
        return getSSOToken(request, getConfig().getCookieName());
    }

    /**
     * <p>
     * 获取当前请求中的SSOToken原始数据
     * </p>
     * <p>
     * 获取Token的优先级：
     * 1. 先从请求头中获取（适用于API调用场景）
     * 2. 如果请求头中不存在，则从Cookie中获取（适用于浏览器场景）
     * </p>
     * <p>
     * 安全说明：
     * - 支持多种Token传递方式，适应不同客户端场景
     * - 对获取的Token进行解析和基本验证
     * - 记录详细日志，便于问题排查
     * </p>
     *
     * @param request    HTTP请求对象
     * @param cookieName Cookie名称，用于从Cookie中查找Token
     * @return 解析后的Token数据，如果不存在则返回空Dict
     */
    protected Dict getSSOToken(HttpServletRequest request, String cookieName) {
        String token = request.getHeader(getConfig().getTokenName());
        log.info("SSO从header中获取token : {}", token);
        if (CharSequenceUtil.isBlank(token)) {
            Cookie cookie = GXCookieHelperUtil.findCookieByName(request, cookieName);
            if (null == cookie) {
                log.info("Unauthorized login request, ip=" + GXIpHelperUtil.getIpAddr(request));
                return Dict.create();
            }
            return GXSSOHelperUtil.parser(cookie.getValue(), false);
        }
        return GXSSOHelperUtil.parser(token, true);
    }

    /**
     * <p>
     * 校验SSOToken的IP和浏览器信息与登录时是否一致
     * </p>
     * <p>
     * 安全验证措施：
     * 1. 验证请求的浏览器信息与Token中记录的是否一致
     * 2. 验证请求的IP地址与Token中记录的是否一致
     * </p>
     * <p>
     * 这些验证可以有效防止Token被盗用的风险，提高系统安全性
     * 验证是否启用可通过配置控制，便于不同环境和场景的灵活应用
     * </p>
     *
     * @param request  HTTP请求对象
     * @param ssoToken 待验证的登录票据
     * @return 验证通过返回原Token，否则返回空Dict
     */
    protected Dict checkIpBrowser(HttpServletRequest request, Dict ssoToken) {
        if (null == ssoToken) {
            return Dict.create();
        }
        // 判断请求浏览器是否合法
        if (getConfig().isCookieBrowser() && !GXBrowserUtil.isLegalUserAgent(request, ssoToken.getStr("userAgent"))) {
            log.debug("The request browser is inconsistent.");
            return Dict.create();
        }
        // 判断请求 IP 是否合法
        if (getConfig().isCookieCheckIp()) {
            String ip = GXIpHelperUtil.getIpAddr(request);
            if (ip != null && !ip.equals(ssoToken.getStr("ip"))) {
                log.debug(String.format("ip inconsistent! return SSOToken null, SSOToken userIp:%s, reqIp:%s", ssoToken.getStr("ip"), ip));
                return Dict.create();
            }
        }
        return ssoToken;
    }

    /**
     * 从Cookie或请求属性中获取SSOToken
     * <p>
     * 获取Token的优先级：
     * 1. 先从请求属性中获取（通常由拦截器设置）
     * 2. 如果属性中不存在，则从Cookie或请求头中获取并解析
     * </p>
     * <p>
     * 注意：该方法仅获取Token数据，不验证IP等安全信息
     * 完整的安全验证应使用getSSOToken方法
     * </p>
     *
     * @param request HTTP请求对象
     * @return 解析后的Token数据，如果不存在则返回null
     */
    public Dict getSSOTokenFromCookie(HttpServletRequest request) {
        Dict token = attrSSOToken(request);
        if (token == null) {
            log.info("SSO组件从request的属性中未获取到");
            token = getSSOToken(request, getConfig().getCookieName());
        }
        log.info("SSO组件最终解码出来的token: {}", token);
        return token;
    }

    // ------------------------------- 登录相关方法 -------------------------------

    /**
     * 根据SSOToken生成登录信息Cookie
     * <p>
     * 将Token信息写入Cookie，设置相关安全属性：
     * 1. 设置Cookie路径
     * 2. 配置Secure属性（是否仅通过HTTPS传输）
     * 3. 设置Cookie域名范围
     * 4. 配置Cookie过期时间
     * </p>
     * <p>
     * 安全配置说明：
     * - 支持配置Cookie的域名范围，控制Cookie的可见范围
     * - 可设置Secure标志，要求Cookie仅通过HTTPS传输
     * - 支持动态设置Cookie的有效期
     * - 对localhost域名特殊处理，避免开发环境问题
     * </p>
     *
     * @param request 请求对象
     * @param token   SSO登录信息票据
     * @return 生成的Cookie对象
     * @throws GXBusinessException 如果Cookie生成过程中发生错误
     */
    protected Cookie generateCookie(HttpServletRequest request, Dict token) {
        try {
            Cookie cookie = new Cookie(getConfig().getCookieName(), token.getStr("token"));
            cookie.setPath(getConfig().getCookiePath());
            cookie.setSecure(getConfig().isCookieSecure());
            // domain 提示
            // 有些浏览器 localhost 无法设置 cookie
            String domain = getConfig().getCookieDomain();
            if (null != domain) {
                cookie.setDomain(domain);
                if ("".equals(domain) || domain.contains("localhost")) {
                    log.warn("if you can't login, please enter normal domain. instead:" + domain);
                }
            }

            // 设置Cookie超时时间
            int maxAge = getConfig().getCookieMaxAge();
            Integer attrMaxAge = (Integer) request.getAttribute(GXSSOConstant.SSO_COOKIE_MAX_AGE);
            if (attrMaxAge != null) {
                maxAge = attrMaxAge;
            }
            if (maxAge >= 0) {
                cookie.setMaxAge(maxAge);
            }
            return cookie;
        } catch (Exception e) {
            throw new GXBusinessException("Generate sso cookie exception ", e);
        }
    }

    /**
     * <p>
     * 退出当前登录状态
     * </p>
     * <p>
     * 完整的注销流程：
     * 1. 清除缓存中的Token数据
     * 2. 执行所有SSO插件的注销逻辑
     * 3. 删除浏览器中的Cookie
     * </p>
     * <p>
     * 安全说明：
     * - 确保服务端和客户端的登录状态同时清除
     * - 支持特殊的踢出用户标记处理
     * - 对缓存操作失败进行重试，提高可靠性
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param cache    SSO缓存实现对象
     * @return 操作是否成功，成功返回true，失败返回false
     */
    protected boolean logout(HttpServletRequest request, HttpServletResponse response, GXSSOCache cache) {
        // SSOToken 如果开启了缓存，删除缓存记录
        if (cache != null && !GXSSOConstant.SSO_KICK_USER.equals(request.getAttribute(GXSSOConstant.SSO_KICK_FLAG))) {
            Dict token = getSSOTokenFromCookie(request);
            if (token != null) {
                boolean rlt = cache.delete(token);
                if (!rlt) {
                    cache.delete(token);
                }
            }
        }

        // 执行插件逻辑
        List<GXSSOPlugin> pluginList = getConfig().getPluginList();
        if (pluginList != null) {
            for (GXSSOPlugin plugin : pluginList) {
                boolean logout = plugin.logout(request, response);
                if (!logout) {
                    plugin.logout(request, response);
                }
            }
        }

        // 删除登录 Cookie
        return GXCookieHelperUtil.clearCookieByName(request, response, getConfig().getCookieName(), getConfig().getCookieDomain(), getConfig().getCookiePath());
    }
}
