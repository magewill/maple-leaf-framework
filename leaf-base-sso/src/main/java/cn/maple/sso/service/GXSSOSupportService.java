package cn.maple.sso.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCookieHelperUtil;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.enums.GXSSOTokenStateEnums;
import cn.maple.sso.enums.GXTokenFlag;
import cn.maple.sso.metrics.GXSSOMetrics;
import cn.maple.sso.plugins.GXSSOPlugin;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.utils.GXBrowserUtil;
import cn.maple.sso.utils.GXIpHelperUtil;
import cn.maple.sso.utils.GXSSOHelperUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Shared SSO support logic (token lookup, cookie generation, logout).
 */
@Slf4j
public abstract class GXSSOSupportService {
    public GXSSOProperties getConfig() {
        return GXSSOProperties.getInstance();
    }

    public Dict attrSSOToken(HttpServletRequest request) {
        Object attribute = request.getAttribute(GXSSOConstant.SSO_TOKEN_ATTR);
        if (attribute instanceof Dict dict) {
            return dict;
        }
        if (attribute instanceof String json && CharSequenceUtil.isNotBlank(json)) {
            try {
                return JSONUtil.toBean(json, Dict.class);
            } catch (Exception e) {
                log.debug("event=sso.token.attr.invalid error={}", e.getMessage());
                return Dict.create();
            }
        }
        return Dict.create();
    }

    protected Dict cacheSSOToken(HttpServletRequest request, @Nullable GXSSOCache cache) {
        Dict requestToken = getSSOTokenFromCookie(request);
        if (CollUtil.isEmpty(requestToken)) {
            log.debug("event=sso.token.absent");
            markState(request, GXSSOTokenStateEnums.ABSENT);
            return Dict.create();
        }

        if (Objects.equals(requestToken.getInt("flag"), GXTokenFlag.CACHE_SHUT.value())) {
            markState(request, GXSSOTokenStateEnums.LOCAL_ONLY);
            return requestToken;
        }

        if (cache == null) {
            if (!getConfig().isCacheFallbackEnabled()) {
                markState(request, GXSSOTokenStateEnums.CACHE_UNAVAILABLE);
                log.error("event=sso.token.reject reason=cache-unavailable");
                return Dict.create();
            }
            markState(request, GXSSOTokenStateEnums.LOCAL_ONLY);
            GXSSOMetrics.getInstance().incrementCacheFallback();
            return requestToken;
        }

        Dict cacheToken = cache.get(getConfig().getCacheExpires(), requestToken);
        if (CollUtil.isEmpty(cacheToken)) {
            markState(request, GXSSOTokenStateEnums.CACHE_MISS);
            log.debug("event=sso.token.reject reason=cache-miss");
            return Dict.create();
        }
        if (!Objects.equals(cacheToken.getInt("flag"), GXTokenFlag.CACHE_SHUT.value())) {
            if (cache.verifyTokenConsistency(cacheToken, requestToken)) {
                markState(request, GXSSOTokenStateEnums.ACTIVE);
                return cacheToken;
            }
            log.debug("event=sso.token.reject reason=kicked");
            request.setAttribute(GXSSOConstant.SSO_KICK_FLAG, GXSSOConstant.SSO_KICK_USER);
            markState(request, GXSSOTokenStateEnums.KICKED);
            return Dict.create();
        }
        markState(request, GXSSOTokenStateEnums.LOCAL_ONLY);
        return requestToken;
    }

    protected Dict getSSOToken(HttpServletRequest request, String cookieName) {
        String token = request.getHeader(getConfig().getTokenName());
        if (CharSequenceUtil.isBlank(token)) {
            Cookie cookie = GXCookieHelperUtil.findCookieByName(request, cookieName);
            if (null == cookie) {
                log.debug("event=sso.token.source.none ip={}", GXIpHelperUtil.getIpAddr(request));
                return Dict.create();
            }
            log.debug("event=sso.token.source cookie");
            return GXSSOHelperUtil.parser(cookie.getValue(), false);
        }
        log.debug("event=sso.token.source header");
        return GXSSOHelperUtil.parser(token, true);
    }

    protected Dict checkIpBrowser(HttpServletRequest request, Dict ssoToken) {
        if (null == ssoToken || ssoToken.isEmpty()) {
            return Dict.create();
        }
        if (getConfig().isCookieBrowser() && !GXBrowserUtil.isLegalUserAgent(request, ssoToken.getStr("userAgent"))) {
            log.debug("event=sso.token.reject reason=browser-mismatch");
            return Dict.create();
        }
        if (getConfig().isCookieCheckIp()) {
            String ip = GXIpHelperUtil.getIpAddr(request);
            if (CharSequenceUtil.isBlank(ip) || !ip.equals(ssoToken.getStr("ip"))) {
                log.debug("event=sso.token.reject reason=ip-mismatch tokenIp={} requestIp={}", ssoToken.getStr("ip"), ip);
                return Dict.create();
            }
        }
        return ssoToken;
    }

    public Dict getSSOTokenFromCookie(HttpServletRequest request) {
        Dict token = attrSSOToken(request);
        if (token.isEmpty()) {
            token = getSSOToken(request, getConfig().getCookieName());
        }
        if (log.isDebugEnabled()) {
            log.debug("event=sso.token.parsed {}", summarizeToken(token));
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
            Object attrMaxAge = request.getAttribute(GXSSOConstant.SSO_COOKIE_MAX_AGE);
            if (attrMaxAge instanceof Number number) {
                maxAge = number.intValue();
            } else if (attrMaxAge != null) {
                log.warn("event=sso.config.invalid key=sso_cookie_max_age type={}", attrMaxAge.getClass().getName());
            }
            if (maxAge >= 0) {
                cookie.setMaxAge(maxAge);
            }
            return cookie;
        } catch (Exception e) {
            throw new GXBusinessException("Generate sso cookie exception ", e);
        }
    }

    protected boolean logout(HttpServletRequest request, HttpServletResponse response, @Nullable GXSSOCache cache) {
        boolean deleted = true;
        Dict token = getSSOTokenFromCookie(request);
        boolean tokenPresent = token != null && !token.isEmpty();

        if (tokenPresent && cache != null
                && !GXSSOConstant.SSO_KICK_USER.equals(request.getAttribute(GXSSOConstant.SSO_KICK_FLAG))) {
            deleted = cache.delete(token);
            if (!deleted) {
                log.warn("event=sso.logout.cache.delete.failed");
            }
        }

        if (tokenPresent) {
            List<GXSSOPlugin> pluginList = getConfig().getPluginList();
            if (pluginList != null) {
                for (GXSSOPlugin plugin : pluginList) {
                    boolean logout = plugin.logout(request, response);
                    if (!logout) {
                        log.warn("event=sso.logout.plugin.failed plugin={}", plugin.getClass().getName());
                    }
                }
            }
            log.info("event=sso.logout reason=explicit result={}", deleted);
        } else {
            log.debug("event=sso.logout.skip reason=not-authenticated");
        }

        clearCookie(request, response);
        return deleted;
    }

    /**
     * Remove the token cookie without touching the server side session.
     *
     * <p>The rejection path uses this method instead of {@link #logout}: a request that is not
     * allowed to continue must not delete cache entries, must not run plugin callbacks and must
     * not write INFO logs (FR-013).</p>
     *
     * @param request current request
     * @param response current response
     */
    protected void clearCookie(HttpServletRequest request, HttpServletResponse response) {
        boolean cleared = GXCookieHelperUtil.clearCookieByName(request, response,
                getConfig().getCookieName(), getConfig().getCookieDomain(), getConfig().getCookiePath());
        if (!cleared) {
            log.debug("event=sso.cookie.absent");
        }
    }

    private void markState(HttpServletRequest request, GXSSOTokenStateEnums state) {
        request.setAttribute(GXSSOConstant.SSO_TOKEN_STATE, state.code());
    }
}
