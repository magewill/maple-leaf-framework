package cn.maple.sso.utils;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXAuthCodeUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.plugins.GXSSOPlugin;
import cn.maple.sso.properties.GXSSOConfigProperties;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.service.GXAbstractSSOService;
import cn.maple.sso.service.GXTokenConfigService;
import cn.maple.sso.service.impl.GXConfigurableAbstractSSOServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class GXSSOHelperUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXSSOHelperUtil.class);

    protected static volatile GXSSOProperties ssoConfig;

    protected static volatile GXAbstractSSOService ssoService;

    private GXSSOHelperUtil() {

    }

    public static GXSSOProperties getSSOConfig() {
        if (Objects.isNull(ssoConfig)) {
            synchronized (GXSSOHelperUtil.class) {
                if (Objects.isNull(ssoConfig)) {
                    try {
                        GXSSOConfigProperties configProperties = GXSpringContextUtils.getBean(GXSSOConfigProperties.class);
                        if (Objects.nonNull(configProperties) && Objects.nonNull(configProperties.getConfig())) {
                            ssoConfig = configProperties.getConfig();
                        } else {
                            ssoConfig = new GXSSOProperties();
                        }

                        Map<String, GXSSOPlugin> ssoPluginMap = GXSpringContextUtils.getBeans(GXSSOPlugin.class);
                        if (ssoPluginMap != null && !ssoPluginMap.isEmpty()) {
                            List<GXSSOPlugin> plugins = new ArrayList<>(ssoPluginMap.values());
                            ssoConfig.setPluginList(plugins);
                        }

                        GXSSOCache ssoCache = GXSpringContextUtils.getBean(GXSSOCache.class);
                        if (Objects.nonNull(ssoCache)) {
                            ssoConfig.setCache(ssoCache);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Initialize SSO config failed.", e);
                        if (Objects.isNull(ssoConfig)) {
                            ssoConfig = new GXSSOProperties();
                        }
                    }
                }
            }
        }
        return ssoConfig;
    }

    public static GXSSOProperties setSsoConfig(GXSSOProperties ssoConfig) {
        Objects.requireNonNull(ssoConfig, "ssoConfig must not be null");
        GXSSOHelperUtil.ssoConfig = ssoConfig;
        return GXSSOHelperUtil.ssoConfig;
    }

    public static GXAbstractSSOService getSSOService() {
        if (Objects.isNull(ssoService)) {
            synchronized (GXSSOHelperUtil.class) {
                if (Objects.isNull(ssoService)) {
                    GXAbstractSSOService service = GXSpringContextUtils.getBean(GXAbstractSSOService.class);
                    ssoService = Objects.nonNull(service) ? service : new GXConfigurableAbstractSSOServiceImpl();
                }
            }
        }
        return ssoService;
    }

    public static void setCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken, boolean invalidate) {
        if (invalidate) {
            getSSOService().authCookie(request, response, ssoToken);
        } else {
            getSSOService().setCookie(request, response, ssoToken);
        }
    }

    public static void setCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken) {
        setCookie(request, response, ssoToken, false);
    }

    public static Dict getSSOToken(HttpServletRequest request) {
        return getSSOService().getSSOToken(request);
    }

    public static Dict attrToken(HttpServletRequest request) {
        return getSSOService().attrSSOToken(request);
    }

    public static void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        getSSOService().logout(request, response);
    }

    public static boolean clearLogin(HttpServletRequest request, HttpServletResponse response) {
        return getSSOService().clearLogin(request, response);
    }

    public static void clearRedirectLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        getSSOService().clearRedirectLogin(request, response);
    }

    public static String getTokenCacheKey(HttpServletRequest request) {
        GXTokenConfigService tokenConfigService = GXSpringContextUtils.getBean(GXTokenConfigService.class);
        if (tokenConfigService == null) {
            throw new GXBusinessException("GXTokenConfigService implementation not found");
        }
        String platform = Optional.ofNullable(request.getHeader(GXTokenConstant.PLATFORM)).orElse("");
        Dict data = Dict.create().set(GXTokenConstant.PLATFORM, platform);
        Dict loginCredentials = GXCurrentRequestContextUtils.getLoginCredentials(getSSOConfig().getTokenName(), tokenConfigService.getTokenSecret());
        Long userId = Optional.ofNullable(loginCredentials.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME)).orElse(0L);
        return tokenConfigService.getTokenCacheKey(userId, data);
    }

    public static String getTokenCacheKey(Object userId) {
        return GXSSOProperties.toCacheKey(userId);
    }

    public static boolean kickLogin(Object userId) {
        return getSSOService().kickLogin(userId);
    }

    public static Dict parser(String token) {
        return parser(token, false);
    }

    public static Dict parser(String token, boolean header) {
        if (GXCurrentRequestContextUtils.isRPC()) {
            return Dict.create();
        }

        if (CharSequenceUtil.isBlank(token)) {
            LOGGER.warn("Received blank token.");
            return Dict.create();
        }

        if (header) {
            LOGGER.debug("Token source: header");
        } else {
            LOGGER.debug("Token source: cookie");
        }

        try {
            GXTokenConfigService tokenSecretService = GXSpringContextUtils.getBean(GXTokenConfigService.class);
            if (Objects.isNull(tokenSecretService)) {
                throw new GXBusinessException("GXTokenConfigService implementation not found in Spring context");
            }

            String tokenSecret = tokenSecretService.getTokenSecret();
            if (CharSequenceUtil.isBlank(tokenSecret)) {
                LOGGER.error("Token secret is blank.");
                throw new GXBusinessException("Token secret config error");
            }

            String decodedToken;
            try {
                decodedToken = GXAuthCodeUtils.authCodeDecode(token, tokenSecret);
                if (CharSequenceUtil.isBlank(decodedToken)) {
                    LOGGER.warn("Token decode result is blank.");
                    return Dict.create();
                }
            } catch (Exception e) {
                LOGGER.error("Token decode failed: {}", e.getMessage());
                return Dict.create();
            }

            Dict requestToken;
            try {
                requestToken = JSONUtil.toBean(decodedToken, Dict.class);
            } catch (Exception e) {
                LOGGER.error("Token JSON parse failed: {}", e.getMessage());
                return Dict.create();
            }

            String clientIP = GXCurrentRequestContextUtils.getClientIP();
            requestToken.putIfAbsent(GXSSOConstant.TOKEN_USER_IP, clientIP);

            return requestToken;
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Unexpected token parse error.", e);
            return Dict.create();
        }
    }
}
