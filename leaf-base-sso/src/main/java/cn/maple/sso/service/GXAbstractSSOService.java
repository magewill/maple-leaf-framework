package cn.maple.sso.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXAuthCodeUtils;
import cn.maple.core.framework.util.GXResultUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.enums.GXSSOTokenStateEnums;
import cn.maple.sso.enums.GXTokenFlag;
import cn.maple.sso.metrics.GXSSOMetrics;
import cn.maple.sso.plugins.GXSSOPlugin;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.utils.GXBrowserUtil;
import cn.maple.sso.utils.GXHttpUtil;
import cn.maple.sso.utils.GXIpHelperUtil;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Default SSO service implementation.
 *
 * <p>Login is fail closed: without a server side cache the token is rejected unless
 * {@code sso.config.cache-fallback-enabled} is explicitly enabled.</p>
 */
@Slf4j
public abstract class GXAbstractSSOService extends GXSSOSupportService implements GXSSOService {
    private static final String NONE_SAME_SITE = "None";

    private static final String PRODUCTION_PROFILE_MARK = "prod";

    private static final Set<String> ALLOWED_SAME_SITE = Set.of("lax", "strict", "none");

    /**
     * Startup validation hook.
     *
     * <p>Security relevant configuration (token secret, cookie attribute combination,
     * token expiry) is validated once at startup so that a misconfigured deployment
     * fails fast instead of silently accepting forged or non-expiring tokens.</p>
     */
    @PostConstruct
    protected void validateConfiguration() {
        GXSSOProperties config = getConfig();
        validateSameSite(config);
        if (config.getCacheExpires() <= 0) {
            log.error("event=sso.config.invalid key=cache-expires reason=non-positive value={}", config.getCacheExpires());
        }
        if (config.getCache() == null && !config.isCacheFallbackEnabled()) {
            log.warn("event=sso.config.warn key=cache reason=no-cache-bean; login will be rejected until GXSSOCache is provided.");
        } else if (config.getCache() == null) {
            log.error("event=sso.config.invalid key=cache reason=local-fallback-enabled tokens are not validated server side.");
        }

        GXTokenConfigService tokenConfigService = GXSpringContextUtils.getBean(GXTokenConfigService.class);
        if (tokenConfigService == null) {
            log.warn("event=sso.config.warn key=token-secret reason=service-not-ready; secret validation is deferred.");
            return;
        }
        if (tokenConfigService.isDefaultTokenSecret()) {
            String profile = currentProfile();
            if (isProductionProfile(profile)) {
                log.error("event=sso.config.invalid key=token-secret reason=default-secret profile={}", profile);
                throw new GXBusinessException("SSO token secret must be configured in production.");
            }
            log.error("event=sso.config.invalid key=token-secret reason=default-secret profile={}; development use only.", profile);
        }
    }

    /**
     * Validate the {@code cookie-same-site} attribute.
     *
     * <p>Only {@code Lax} / {@code Strict} / {@code None} are accepted; an unknown value would be
     * emitted verbatim into the {@code Set-Cookie} header. {@code None} additionally requires
     * {@code cookie-secure=true}.</p>
     *
     * @param config current configuration
     */
    private void validateSameSite(GXSSOProperties config) {
        String sameSite = config.getCookieSameSite();
        if (CharSequenceUtil.isBlank(sameSite)
                || !ALLOWED_SAME_SITE.contains(sameSite.trim().toLowerCase(Locale.ROOT))) {
            log.error("event=sso.config.invalid key=cookie-same-site reason=unsupported value={}", sameSite);
            throw new GXBusinessException("SSO cookie SameSite must be one of Lax/Strict/None.");
        }
        if (NONE_SAME_SITE.equalsIgnoreCase(sameSite) && !config.isCookieSecure()) {
            log.error("event=sso.config.invalid key=cookie-same-site reason=none-requires-secure");
            throw new GXBusinessException("SSO cookie SameSite=None requires cookie-secure=true.");
        }
    }

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
                    markTokenState(request, GXSSOTokenStateEnums.PLUGIN_REJECTED);
                    log.debug("event=sso.token.reject reason=plugin-rejected plugin={}", plugin.getClass().getName());
                    return Dict.create();
                }
            }
        }
        return token;
    }

    @Override
    public boolean kickLogin(Object userId) {
        GXSSOCache cache = getConfig().getCache();
        if (cache == null) {
            log.debug("kickLogin! please implements GXSSOCache class.");
            return false;
        }
        if (userId == null) {
            GXSSOMetrics.getInstance().incrementInvalidToken();
            log.debug("event=sso.kick.reject reason=null-user-id");
            return false;
        }
        Dict ssoToken = Dict.create().set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, userId);
        return cache.delete(ssoToken);
    }

    @Override
    public void setCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken) {
        GXSSOProperties config = getConfig();
        GXSSOCache cache = config.getCache();

        if (cache == null && !config.isCacheFallbackEnabled()) {
            GXSSOMetrics.getInstance().incrementLoginFailure();
            log.error("event=sso.login.failure reason=cache-unavailable");
            throw new GXBusinessException("SSO cache service is unavailable, login is rejected.");
        }
        try {
            validateSameSite(config);
        } catch (GXBusinessException e) {
            GXSSOMetrics.getInstance().incrementLoginFailure();
            log.error("event=sso.login.failure reason=invalid-cookie-config");
            throw e;
        }

        Dict cookieToken = new Dict(ssoToken);
        boolean degraded = false;
        boolean cached = false;

        cookieToken.put("createTime", System.currentTimeMillis());
        cookieToken.put("userAgent", GXBrowserUtil.getUserAgent(request));
        cookieToken.put("ip", GXIpHelperUtil.getIpAddr(request));

        if (cache != null) {
            boolean tokenProvided = CharSequenceUtil.isNotBlank(cookieToken.getStr(config.getTokenName()));
            Dict existingCookieToken = getSSOTokenFromCookie(request);
            if (existingCookieToken != null && !existingCookieToken.isEmpty()) {
                Dict mergedToken = new Dict(existingCookieToken);
                mergedToken.putAll(cookieToken);
                cookieToken = mergedToken;
            }
            if (!tokenProvided) {
                cookieToken.remove(config.getTokenName());
            }

            fillTokenValueIfNecessary(cookieToken, config.getCacheExpires());
            cached = cache.set(cookieToken, config.getCacheExpires());
            if (!cached) {
                if (!config.isCacheFallbackEnabled()) {
                    GXSSOMetrics.getInstance().incrementLoginFailure();
                    log.error("event=sso.login.failure reason=cache-write-failed");
                    throw new GXBusinessException("SSO cache write failed, login is rejected.");
                }
                degraded = true;
                cookieToken.put("flag", GXTokenFlag.CACHE_SHUT.value());
                cookieToken.remove(config.getTokenName());
                fillTokenValueIfNecessary(cookieToken, GXSSOProperties.MAX_FALLBACK_TTL_SECONDS);
                log.error("event=sso.login.degraded reason=cache-write-failed");
                GXSSOMetrics.getInstance().incrementCacheFallback();
            }
        } else {
            degraded = true;
            cookieToken.put("flag", GXTokenFlag.CACHE_SHUT.value());
            cookieToken.remove(config.getTokenName());
            fillTokenValueIfNecessary(cookieToken, GXSSOProperties.MAX_FALLBACK_TTL_SECONDS);
            log.error("event=sso.login.degraded reason=cache-unavailable");
            GXSSOMetrics.getInstance().incrementCacheFallback();
        }

        int maxAge = degraded ? degradedCookieMaxAge(config) : resolveCookieMaxAge(request, config);
        writeCookie(response, config, cookieToken, maxAge);

        List<GXSSOPlugin> pluginList = config.getPluginList();
        if (pluginList != null) {
            for (GXSSOPlugin plugin : pluginList) {
                boolean login = plugin.login(request, response);
                if (!login) {
                    if (cached && cache != null) {
                        cache.delete(cookieToken);
                    }
                    GXSSOMetrics.getInstance().incrementLoginFailure();
                    log.warn("event=sso.login.failure reason=plugin-rejected plugin={}", plugin.getClass().getName());
                    throw new GXBusinessException("SSO plugin rejected this login.");
                }
            }
        }

        log.info("event=sso.login.success userId={} degraded={}", cookieToken.getStr(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME), degraded);
    }

    public void authCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken) {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
                log.debug("event=sso.login.session.invalidate");
            }
        } catch (IllegalStateException e) {
            log.debug("event=sso.login.session.invalidate reason=already-invalid");
        }

        this.setCookie(request, response, ssoToken);
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        if (log.isDebugEnabled()) {
            log.debug("event=sso.login.security-headers.applied");
        }
    }

    @Override
    public boolean clearLogin(HttpServletRequest request, HttpServletResponse response) {
        return logout(request, response, getConfig().getCache());
    }

    @Override
    public void clearRedirectLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Rejection path: only drop the browser cookie. Reusing clearLogin here would delete the
        // cache entry (possibly the current session of the same user) and fire every plugin
        // logout callback, which contradicts the zero side effect contract (FR-013).
        clearCookie(request, response);
        if (response.isCommitted()) {
            log.debug("Response is committed, skip login redirect handling: {}", request.getRequestURI());
            return;
        }

        String loginUrl = getConfig().getLoginUrl();
        if (GXHttpUtil.isAjax(request) || CharSequenceUtil.isBlank(loginUrl)) {
            response.setStatus(HttpStatus.HTTP_UNAUTHORIZED);
            response.setCharacterEncoding(getConfig().getEncoding());
            response.setContentType("application/json;charset=" + getConfig().getEncoding());
            response.getWriter().write(JSONUtil.toJsonStr(
                    GXResultUtils.error(HttpStatus.HTTP_UNAUTHORIZED, "Please login")));
        } else {
            String retUrl = GXHttpUtil.getRequestUrl(request);
            log.debug("loginAgain redirect pageUrl: {}", retUrl);
            response.sendRedirect(GXHttpUtil.encodeRetURL(loginUrl, getConfig().getParamReturnUrl(), retUrl));
        }
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        logout(request, response, getConfig().getCache());

        String logoutUrl = getConfig().getLogoutUrl();
        if (CharSequenceUtil.isBlank(logoutUrl)) {
            log.warn("event=sso.logout.redirect.missing key=sso.config.logout-url");
            response.setStatus(HttpStatus.HTTP_OK);
            response.setCharacterEncoding(getConfig().getEncoding());
            response.setContentType("application/json;charset=" + getConfig().getEncoding());
            response.getWriter().write(JSONUtil.toJsonStr(GXResultUtils.ok("Logged out")));
        } else {
            response.sendRedirect(logoutUrl);
        }
    }

    /**
     * Fill the token value with a signed payload when it is still blank.
     *
     * @param ssoToken token payload
     * @param ttlSeconds absolute time-to-live in seconds, {@code <= 0} means no expiry
     */
    private void fillTokenValueIfNecessary(Dict ssoToken, int ttlSeconds) {
        if (CharSequenceUtil.isNotBlank(ssoToken.getStr(getConfig().getTokenName()))) {
            return;
        }

        GXTokenConfigService tokenConfigService = GXSpringContextUtils.getBean(GXTokenConfigService.class);
        if (tokenConfigService == null) {
            throw new GXBusinessException("GXTokenConfigService implementation not found");
        }
        String tokenSecret = tokenConfigService.getTokenSecret();
        if (CharSequenceUtil.isBlank(tokenSecret)) {
            throw new GXBusinessException("SSO token secret is not configured.");
        }

        ssoToken.putIfAbsent(GXTokenConstant.LOGIN_AT_FIELD_NAME, System.currentTimeMillis() / 1000);
        Dict tokenData = new Dict(ssoToken);
        tokenData.remove(getConfig().getTokenName());
        int expires = Math.max(ttlSeconds, 0);
        ssoToken.set(getConfig().getTokenName(),
                GXAuthCodeUtils.authCodeEncode(JSONUtil.toJsonStr(tokenData), tokenSecret, expires));
    }

    /**
     * Cookie lifetime used by the degraded (no cache) mode.
     *
     * <p>A non positive {@code cache-expires} means "no expiry" for the signed token, but the
     * degraded token must stay short lived, so it is clamped to the fallback ceiling instead of
     * collapsing to a one second cookie.</p>
     *
     * @param config current configuration
     * @return cookie {@code Max-Age} in seconds, never greater than {@link GXSSOProperties#MAX_FALLBACK_TTL_SECONDS}
     */
    private int degradedCookieMaxAge(GXSSOProperties config) {
        int configured = config.getCacheExpires();
        if (configured <= 0) {
            return GXSSOProperties.MAX_FALLBACK_TTL_SECONDS;
        }
        return Math.min(configured, GXSSOProperties.MAX_FALLBACK_TTL_SECONDS);
    }

    private int resolveCookieMaxAge(HttpServletRequest request, GXSSOProperties config) {
        Object attrMaxAge = request.getAttribute(GXSSOConstant.SSO_COOKIE_MAX_AGE);
        if (attrMaxAge instanceof Number number) {
            return number.intValue();
        }
        return config.getCookieMaxAge();
    }

    private void writeCookie(HttpServletResponse response, GXSSOProperties config, Dict cookieToken, int maxAge) {
        String tokenValue = cookieToken.getStr(config.getTokenName());
        if (CharSequenceUtil.isBlank(tokenValue)) {
            throw new GXBusinessException("SSO token value is blank, cookie was not written.");
        }

        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(config.getCookieName(), tokenValue)
                .path(CharSequenceUtil.isBlank(config.getCookiePath()) ? "/" : config.getCookiePath())
                .secure(config.isCookieSecure())
                .httpOnly(config.isCookieHttpOnly())
                .sameSite(config.getCookieSameSite());

        if (CharSequenceUtil.isNotBlank(config.getCookieDomain())) {
            builder.domain(config.getCookieDomain());
        }
        if (maxAge >= 0) {
            builder.maxAge(Duration.ofSeconds(maxAge));
        }

        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
        log.debug("event=sso.cookie.write cookieName={} maxAge={}", config.getCookieName(), maxAge);
    }

    private String currentProfile() {
        String profile = System.getProperty("spring.profiles.active");
        if (CharSequenceUtil.isNotBlank(profile)) {
            return profile;
        }
        org.springframework.core.env.Environment environment =
                GXSpringContextUtils.getBean(org.springframework.core.env.Environment.class);
        if (environment != null) {
            String[] activeProfiles = environment.getActiveProfiles();
            if (activeProfiles.length > 0) {
                return String.join(",", activeProfiles);
            }
            String[] defaultProfiles = environment.getDefaultProfiles();
            if (defaultProfiles.length > 0) {
                return String.join(",", defaultProfiles);
            }
        }
        return "";
    }

    private boolean isProductionProfile(String profile) {
        if (CharSequenceUtil.isBlank(profile)) {
            return false;
        }
        for (String item : profile.split(",")) {
            if (item.trim().toLowerCase(java.util.Locale.ROOT).startsWith(PRODUCTION_PROFILE_MARK)) {
                return true;
            }
        }
        return false;
    }

    private void markTokenState(HttpServletRequest request, GXSSOTokenStateEnums state) {
        request.setAttribute(GXSSOConstant.SSO_TOKEN_STATE, state.code());
    }
}
