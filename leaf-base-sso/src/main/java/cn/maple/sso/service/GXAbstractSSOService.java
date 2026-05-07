package cn.maple.sso.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXAuthCodeUtils;
import cn.maple.core.framework.util.GXCookieHelperUtil;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.enums.GXTokenFlag;
import cn.maple.sso.enums.GXRandomType;
import cn.maple.sso.plugins.GXSSOPlugin;
import cn.maple.sso.utils.GXBrowserUtil;
import cn.maple.sso.utils.GXHttpUtil;
import cn.maple.sso.utils.GXIpHelperUtil;
import cn.maple.sso.utils.GXRandomUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Slf4j
public abstract class GXAbstractSSOService extends GXSSOSupportService implements GXSSOService {
    @Override
    public Dict getSSOToken(HttpServletRequest request) {
        Dict cacheSSOToken = cacheSSOToken(request, getConfig().getCache());
        Dict token = checkIpBrowser(request, cacheSSOToken);
        if (CollUtil.isEmpty(token)) {
            return Dict.create();
        }
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

    @Override
    public boolean kickLogin(Object userId) {
        GXSSOCache cache = getConfig().getCache();
        if (cache != null) {
            Dict ssoToken = Dict.create().set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, userId);
            return cache.delete(ssoToken);
        } else {
            log.debug(" kickLogin! please implements GXSsoCache class.");
        }
        return false;
    }

    @Override
    public void setCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken) {
        try {
            Dict cookieToken = new Dict(ssoToken);
            GXSSOCache cache = getConfig().getCache();
            if (cache != null) {
                boolean tokenProvided = CharSequenceUtil.isNotBlank(cookieToken.getStr(getConfig().getTokenName()));
                cookieToken.put("createTime", System.currentTimeMillis());
                cookieToken.put("userAgent", GXBrowserUtil.getUserAgent(request));
                cookieToken.put("ip", GXIpHelperUtil.getIpAddr(request));

                Dict existingCookieToken = getSSOTokenFromCookie(request);
                if (existingCookieToken != null && !existingCookieToken.isEmpty()) {
                    Dict mergedToken = new Dict(existingCookieToken);
                    mergedToken.putAll(cookieToken);
                    cookieToken = mergedToken;
                }
                if (!tokenProvided) {
                    cookieToken.remove(getConfig().getTokenName());
                }

                fillTokenValueIfNecessary(cookieToken);

                boolean rlt = cache.set(cookieToken, getConfig().getCacheExpires());
                if (!rlt) {
                    cookieToken.put("flag", GXTokenFlag.CACHE_SHUT.value());
                    cookieToken.remove(getConfig().getTokenName());
                    fillTokenValueIfNecessary(cookieToken);
                    log.warn("SSO cache service unavailable, token uses local mode.");
                }
            }

            fillTokenValueIfNecessary(cookieToken);

            Cookie ck = this.generateCookie(request, cookieToken);

            if (getConfig().isCookieHttpOnly()) {
                ck.setHttpOnly(true);
            }

            StringBuilder cookieBuilder = new StringBuilder();
            cookieBuilder.append(ck.getName()).append("=").append(ck.getValue());

            if (ck.getPath() != null) {
                cookieBuilder.append("; Path=").append(ck.getPath());
            }

            if (ck.getMaxAge() >= 0) {
                cookieBuilder.append("; Max-Age=").append(ck.getMaxAge());
            }

            if (ck.getDomain() != null) {
                cookieBuilder.append("; Domain=").append(ck.getDomain());
            }

            if (ck.getSecure()) {
                cookieBuilder.append("; Secure");
            }

            if (ck.isHttpOnly()) {
                cookieBuilder.append("; HttpOnly");
            }
            if (CharSequenceUtil.isNotBlank(getConfig().getCookieSameSite())) {
                cookieBuilder.append("; SameSite=").append(getConfig().getCookieSameSite());
            }

            response.addHeader("Set-Cookie", cookieBuilder.toString());

            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-XSS-Protection", "1; mode=block");

            List<GXSSOPlugin> pluginList = getConfig().getPluginList();
            if (pluginList != null) {
                for (GXSSOPlugin plugin : pluginList) {
                    boolean login = plugin.login(request, response);
                    if (!login) {
                        log.warn("SSO plugin login failed: {}", plugin.getClass().getName());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Set SSO cookie failed: {}", e.getMessage(), e);
            throw new GXBusinessException("Set login cookie failed, please try again later");
        }
    }

    private void fillTokenValueIfNecessary(Dict ssoToken) {
        if (CharSequenceUtil.isNotBlank(ssoToken.getStr(getConfig().getTokenName()))) {
            return;
        }

        GXTokenConfigService tokenConfigService = GXSpringContextUtils.getBean(GXTokenConfigService.class);
        if (tokenConfigService == null) {
            throw new GXBusinessException("GXTokenConfigService implementation not found");
        }

        ssoToken.putIfAbsent(GXTokenConstant.LOGIN_AT_FIELD_NAME, System.currentTimeMillis() / 1000);
        Dict tokenData = new Dict(ssoToken);
        tokenData.remove(getConfig().getTokenName());
        int expires = Math.max(getConfig().getCacheExpires(), 0);
        ssoToken.set(getConfig().getTokenName(),
                GXAuthCodeUtils.authCodeEncode(JSONUtil.toJsonStr(tokenData), tokenConfigService.getTokenSecret(), expires));
    }

    public void authCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken) {
        String sessionId = GXRandomUtil.getSecureText(GXRandomType.MIX, 32);
        try {
            log.debug("Regenerated session id [{}] for session fixation protection.", sessionId.substring(0, 4) + "***");

            GXCookieHelperUtil.authJSESSIONID(request, sessionId);
        } catch (Exception e) {
            log.error("Regenerate session failed: {}", e.getMessage(), e);
            log.warn("Session regeneration failed, falling back to basic cookie setup.");
        }

        this.setCookie(request, response, ssoToken);
        response.setHeader("X-Frame-Options", "DENY");

        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");

        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        if (log.isDebugEnabled()) {
            log.debug("User login security headers applied.");
        }
    }

    @Override
    public boolean clearLogin(HttpServletRequest request, HttpServletResponse response) {
        return logout(request, response, getConfig().getCache());
    }

    @Override
    public void clearRedirectLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        clearLogin(request, response);
        if (response.isCommitted()) {
            log.debug("Response is committed, skip login redirect handling: {}", request.getRequestURI());
            return;
        }

        String loginUrl = getConfig().getLoginUrl();
        if ("api".equalsIgnoreCase(request.getHeader("X-RESPONSE-TYPE")) || CharSequenceUtil.isBlank(loginUrl)) {
            Dict data = Dict.create().set("code", HttpStatus.HTTP_UNAUTHORIZED).set("msg", "Please login").set("data", null);
            response.setStatus(HttpStatus.HTTP_UNAUTHORIZED);
            response.setCharacterEncoding(getConfig().getEncoding());
            response.setContentType("application/json;charset=" + getConfig().getEncoding());
            JsonMapper jsonMapper = GXSpringContextUtils.getBean(JsonMapper.class);
            if (jsonMapper == null) {
                throw new GXBusinessException("JsonMapper bean not found");
            }
            jsonMapper.writeValue(response.getWriter(), data);
        } else {
            String retUrl = GXHttpUtil.getRequestUrl(request);
            log.debug("loginAgain redirect pageUrl: {}", retUrl);
            response.sendRedirect(GXHttpUtil.encodeRetURL(loginUrl, getConfig().getParamReturnUrl(), retUrl));
        }
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        logout(request, response, getConfig().getCache());

        String logoutUrl = getConfig().getLogoutUrl();
        if ("".equals(logoutUrl)) {
            response.getWriter().write("sso.yml Must include: sso.config.logout.url");
        } else {
            response.sendRedirect(logoutUrl);
        }
    }
}
