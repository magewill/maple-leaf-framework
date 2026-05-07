package cn.maple.sso.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCookieHelperUtil;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.enums.GXTokenFlag;
import cn.maple.sso.plugins.GXSSOPlugin;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.utils.GXBrowserUtil;
import cn.maple.sso.utils.GXIpHelperUtil;
import cn.maple.sso.utils.GXSSOHelperUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

@Slf4j
public abstract class GXSSOSupportService {
    public GXSSOProperties getConfig() {
        return GXSSOProperties.getInstance();
    }

    public Dict attrSSOToken(HttpServletRequest request) {
        Object attribute = request.getAttribute(GXSSOConstant.SSO_TOKEN_ATTR);
        return Convert.convert(Dict.class, attribute);
    }

    protected Dict cacheSSOToken(HttpServletRequest request, GXSSOCache cache) {
        Dict requestToken = getSSOTokenFromCookie(request);
        if (CollUtil.isEmpty(requestToken)) {
            log.info("SSO user is not logged in.");
            return Dict.create();
        }

        if (Objects.equals(requestToken.getInt("flag"), GXTokenFlag.CACHE_SHUT.value())) {
            return requestToken;
        }

        if (cache != null) {
            Dict cacheToken = cache.get(getConfig().getCacheExpires(), requestToken);
            if (CollUtil.isEmpty(cacheToken)) {
                log.info("cacheSSOToken GXSsoToken is null.");
                return Dict.create();
            }
            if (!Objects.equals(cacheToken.getInt("flag"), GXTokenFlag.CACHE_SHUT.value())) {
                if (cache.verifyTokenConsistency(cacheToken, requestToken)) {
                    return cacheToken;
                }
                log.info("Login time is not consistent or kicked out.");
                request.setAttribute(GXSSOConstant.SSO_KICK_FLAG, GXSSOConstant.SSO_KICK_USER);
                return Dict.create();
            }
        }
        return requestToken;
    }

    protected Dict getSSOToken(HttpServletRequest request, String cookieName) {
        String token = request.getHeader(getConfig().getTokenName());
        if (CharSequenceUtil.isBlank(token)) {
            Cookie cookie = GXCookieHelperUtil.findCookieByName(request, cookieName);
            if (null == cookie) {
                log.info("Unauthorized login request, ip={}", GXIpHelperUtil.getIpAddr(request));
                return Dict.create();
            }
            log.debug("SSO token source: cookie");
            return GXSSOHelperUtil.parser(cookie.getValue(), false);
        }
        log.debug("SSO token source: header");
        return GXSSOHelperUtil.parser(token, true);
    }

    protected Dict checkIpBrowser(HttpServletRequest request, Dict ssoToken) {
        if (null == ssoToken || ssoToken.isEmpty()) {
            return Dict.create();
        }
        if (getConfig().isCookieBrowser() && !GXBrowserUtil.isLegalUserAgent(request, ssoToken.getStr("userAgent"))) {
            log.debug("The request browser is inconsistent.");
            return Dict.create();
        }
        if (getConfig().isCookieCheckIp()) {
            String ip = GXIpHelperUtil.getIpAddr(request);
            if (ip != null && !ip.equals(ssoToken.getStr("ip"))) {
                log.debug("ip inconsistent! return SSOToken null, SSOToken userIp:{}, reqIp:{}", ssoToken.getStr("ip"), ip);
                return Dict.create();
            }
        }
        return ssoToken;
    }

    public Dict getSSOTokenFromCookie(HttpServletRequest request) {
        Dict token = attrSSOToken(request);
        if (token == null) {
            log.debug("SSO token request attribute is empty.");
            token = getSSOToken(request, getConfig().getCookieName());
        }
        if (log.isDebugEnabled()) {
            log.debug("SSO token parsed: {}", summarizeToken(token));
        }
        return token;
    }

    private String summarizeToken(Dict token) {
        if (CollUtil.isEmpty(token)) {
            return "empty";
        }
        return "userId=" + token.getStr(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME)
                + ", loginAt=" + token.getStr(GXTokenConstant.LOGIN_AT_FIELD_NAME)
                + ", flag=" + token.getStr("flag");
    }

    protected Cookie generateCookie(HttpServletRequest request, Dict token) {
        try {
            Cookie cookie = new Cookie(getConfig().getCookieName(), token.getStr(getConfig().getTokenName()));
            cookie.setPath(getConfig().getCookiePath());
            cookie.setSecure(getConfig().isCookieSecure());
            String domain = getConfig().getCookieDomain();
            if (null != domain) {
                cookie.setDomain(domain);
                if (domain.isEmpty() || domain.contains("localhost")) {
                    log.warn("if you can't login, please enter normal domain. instead:{}", domain);
                }
            }

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

    protected boolean logout(HttpServletRequest request, HttpServletResponse response, GXSSOCache cache) {
        if (cache != null && !GXSSOConstant.SSO_KICK_USER.equals(request.getAttribute(GXSSOConstant.SSO_KICK_FLAG))) {
            Dict token = getSSOTokenFromCookie(request);
            if (token != null) {
                boolean rlt = cache.delete(token);
                if (!rlt) {
                    log.warn("SSO token cache delete failed.");
                }
            }
        }

        List<GXSSOPlugin> pluginList = getConfig().getPluginList();
        if (pluginList != null) {
            for (GXSSOPlugin plugin : pluginList) {
                boolean logout = plugin.logout(request, response);
                if (!logout) {
                    log.warn("SSO plugin logout failed: {}", plugin.getClass().getName());
                }
            }
        }

        return GXCookieHelperUtil.clearCookieByName(request, response, getConfig().getCookieName(), getConfig().getCookieDomain(), getConfig().getCookiePath());
    }
}
