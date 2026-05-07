package cn.maple.sso.service;

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
import cn.maple.sso.plugins.GXSSOPlugin;
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
        if (Objects.isNull(token)) {
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
            GXSSOCache cache = getConfig().getCache();
            if (cache != null) {
                boolean tokenProvided = CharSequenceUtil.isNotBlank(ssoToken.getStr(getConfig().getTokenName()));
                ssoToken.put("createTime", System.currentTimeMillis());
                ssoToken.put("userAgent", request.getHeader("User-Agent"));
                ssoToken.put("ip", GXIpHelperUtil.getIpAddr(request));

                Dict cookieSSOToken = getSSOTokenFromCookie(request);
                if (cookieSSOToken != null && !cookieSSOToken.isEmpty()) {
                    cookieSSOToken.putAll(ssoToken);
                    ssoToken.clear();
                    ssoToken.putAll(cookieSSOToken);
                }
                if (!tokenProvided) {
                    ssoToken.remove(getConfig().getTokenName());
                }

                fillTokenValueIfNecessary(ssoToken);

                boolean rlt = cache.set(ssoToken, getConfig().getCacheExpires());
                if (!rlt) {
                    ssoToken.put("flag", GXTokenFlag.CACHE_SHUT.value());
                    ssoToken.remove(getConfig().getTokenName());
                    fillTokenValueIfNecessary(ssoToken);
                    log.warn("缓存服务不可用，Token将使用本地模式，可能影响分布式会话同步");
                }
            }

            fillTokenValueIfNecessary(ssoToken);

            Cookie ck = this.generateCookie(request, ssoToken);

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
            cookieBuilder.append("; SameSite=Lax");

            response.addHeader("Set-Cookie", cookieBuilder.toString());

            response.setHeader("X-Content-Type-Options", "nosniff"); // 防止MIME类型嗅探
            response.setHeader("X-XSS-Protection", "1; mode=block"); // 启用XSS过滤

            List<GXSSOPlugin> pluginList = getConfig().getPluginList();
            if (pluginList != null) {
                for (GXSSOPlugin plugin : pluginList) {
                    boolean login = plugin.login(request, response);
                    if (!login) {
                        log.warn("插件[{}]登录处理失败，请检查插件配置", plugin.getClass().getSimpleName());
                    }
                }
            }
        } catch (Exception e) {
            log.error("设置SSO Cookie时发生错误: {}", e.getMessage(), e);
            throw new GXBusinessException("设置登录Cookie失败，请稍后重试");
        }
    }

    private void fillTokenValueIfNecessary(Dict ssoToken) {
        if (CharSequenceUtil.isNotBlank(ssoToken.getStr(getConfig().getTokenName()))) {
            return;
        }

        GXTokenConfigService tokenConfigService = GXSpringContextUtils.getBean(GXTokenConfigService.class);
        if (tokenConfigService == null) {
            throw new GXBusinessException("未找到GXTokenConfigService实现类");
        }

        ssoToken.putIfAbsent(GXTokenConstant.LOGIN_AT_FIELD_NAME, System.currentTimeMillis() / 1000);
        Dict tokenData = new Dict(ssoToken);
        tokenData.remove(getConfig().getTokenName());
        int expires = Math.max(getConfig().getCacheExpires(), 0);
        ssoToken.set(getConfig().getTokenName(),
                GXAuthCodeUtils.authCodeEncode(JSONUtil.toJsonStr(tokenData), tokenConfigService.getTokenSecret(), expires));
    }

    public void authCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken) {
        try {
            String sessionId = GXRandomUtil.getCharacterAndNumber(16);

            log.debug("重新生成会话ID[{}]，防止会话固定攻击", sessionId.substring(0, 4) + "***");

            GXCookieHelperUtil.authJSESSIONID(request, sessionId);

            this.setCookie(request, response, ssoToken);
            response.setHeader("X-Frame-Options", "DENY");

            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");

            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

            if (log.isDebugEnabled()) {
                log.debug("用户登录安全增强完成，应用了会话固定攻击防护和安全响应头");
            }
        } catch (Exception e) {
            log.error("重新生成会话时发生错误: {}", e.getMessage(), e);

            log.warn("会话重新生成失败，将使用基本Cookie设置，安全性可能降低");
            this.setCookie(request, response, ssoToken);
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
            log.debug("响应已提交，跳过重新登录响应处理: {}", request.getRequestURI());
            return;
        }

        String loginUrl = getConfig().getLoginUrl();
        if ("api".equalsIgnoreCase(request.getHeader("X-RESPONSE-TYPE")) || CharSequenceUtil.isBlank(loginUrl)) {
            Dict data = Dict.create().set("code", HttpStatus.HTTP_UNAUTHORIZED).set("msg", "Please login").set("data", null);
            response.setStatus(HttpStatus.HTTP_UNAUTHORIZED);
            response.setCharacterEncoding(getConfig().getEncoding());
            response.setContentType("application/json;charset=" + getConfig().getEncoding());
            JsonMapper jsonMapper = GXSpringContextUtils.getBean(JsonMapper.class);
            assert jsonMapper != null;
            jsonMapper.writeValue(response.getWriter(), data);
            //response.getWriter().write(JSONUtil.toJsonStr(data, JSONConfig.create().setIgnoreNullValue(false)));
        } else {
            String retUrl = GXHttpUtil.getRequestUrl(request);
            log.debug("loginAgain redirect pageUrl.." + retUrl);
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
