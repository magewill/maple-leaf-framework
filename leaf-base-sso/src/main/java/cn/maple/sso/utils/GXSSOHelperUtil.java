package cn.maple.sso.utils;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXAuthCodeUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.metrics.GXSSOMetrics;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class GXSSOHelperUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXSSOHelperUtil.class);

    private static final Map<Class<?>, CachedBean> BEAN_CACHE = new ConcurrentHashMap<>();

    private static final AtomicInteger BEAN_LOOKUP_COUNT = new AtomicInteger();

    private static final AtomicInteger RESOLVER_VERSION = new AtomicInteger();

    private static volatile Function<Class<?>, Object> beanResolver = GXSpringContextUtils::getBean;

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
                            LOGGER.error("event=sso.config.invalid key=config-source reason=bean-absent; using defaults without cache and plugins.");
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
        if (ssoConfig == null) {
            throw new IllegalArgumentException("ssoConfig must not be null");
        }
        GXSSOHelperUtil.ssoConfig = ssoConfig;
        invalidateBeanCache();
        return GXSSOHelperUtil.ssoConfig;
    }

    /**
     * Cached bean lookup used on the request path.
     *
     * @param type bean type
     * @param <T>  bean type
     * @return the bean or {@code null} when absent
     */
    public static <T> T bean(Class<T> type) {
        Object context = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        int version = RESOLVER_VERSION.get();
        CachedBean cached = BEAN_CACHE.get(type);
        if (cached != null && cached.context() == context && cached.version() == version) {
            return type.cast(cached.bean());
        }

        BEAN_LOOKUP_COUNT.incrementAndGet();
        Object resolved;
        try {
            resolved = beanResolver.apply(type);
        } catch (Exception e) {
            LOGGER.debug("event=sso.bean.lookup.failed type={} error={}", type.getSimpleName(), e.getMessage());
            return null;
        }
        if (resolved == null) {
            return null;
        }

        T bean = type.cast(resolved);
        BEAN_CACHE.put(type, new CachedBean(context, version, bean));
        return bean;
    }

    static void setBeanResolverForTests(Function<Class<?>, Object> resolver) {
        beanResolver = resolver == null ? GXSpringContextUtils::getBean : resolver;
        RESOLVER_VERSION.incrementAndGet();
        invalidateBeanCache();
    }

    private record CachedBean(Object context, int version, Object bean) {
    }

    static int beanLookupCount() {
        return BEAN_LOOKUP_COUNT.get();
    }

    static void resetBeanLookupCount() {
        BEAN_LOOKUP_COUNT.set(0);
    }

    private static void invalidateBeanCache() {
        BEAN_CACHE.clear();
    }

    public static GXAbstractSSOService getSSOService() {
        if (Objects.isNull(ssoService)) {
            synchronized (GXSSOHelperUtil.class) {
                if (Objects.isNull(ssoService)) {
                    GXAbstractSSOService service = bean(GXAbstractSSOService.class);
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
        GXTokenConfigService tokenConfigService = bean(GXTokenConfigService.class);
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
            LOGGER.debug("event=sso.token.parse.skip reason=blank-token");
            return Dict.create();
        }

        if (header) {
            LOGGER.debug("event=sso.token.source header");
        } else {
            LOGGER.debug("event=sso.token.source cookie");
        }

        try {
            GXTokenConfigService tokenSecretService = bean(GXTokenConfigService.class);
            if (Objects.isNull(tokenSecretService)) {
                throw new GXBusinessException("GXTokenConfigService implementation not found in Spring context");
            }

            String tokenSecret = tokenSecretService.getTokenSecret();
            if (CharSequenceUtil.isBlank(tokenSecret)) {
                LOGGER.error("event=sso.config.invalid key=token-secret reason=blank");
                throw new GXBusinessException("Token secret config error");
            }

            String decodedToken;
            try {
                decodedToken = GXAuthCodeUtils.authCodeDecode(token, tokenSecret);
            } catch (Exception e) {
                LOGGER.debug("event=sso.token.parse.failed reason=decode-error error={}", e.getMessage());
                GXSSOMetrics.getInstance().incrementParseFailure();
                return Dict.create();
            }

            if (CharSequenceUtil.isBlank(decodedToken) || "{}".equals(decodedToken)) {
                LOGGER.debug("event=sso.token.parse.failed reason=invalid-token");
                GXSSOMetrics.getInstance().incrementParseFailure();
                return Dict.create();
            }

            Dict requestToken;
            try {
                requestToken = JSONUtil.toBean(decodedToken, Dict.class);
            } catch (Exception e) {
                LOGGER.debug("event=sso.token.parse.failed reason=json-error error={}", e.getMessage());
                GXSSOMetrics.getInstance().incrementParseFailure();
                return Dict.create();
            }

            if (requestToken.isEmpty()) {
                GXSSOMetrics.getInstance().incrementParseFailure();
                return requestToken;
            }

            String clientIP = GXCurrentRequestContextUtils.getClientIP();
            requestToken.putIfAbsent(GXSSOConstant.TOKEN_USER_IP, clientIP);

            GXSSOMetrics.getInstance().incrementParseSuccess();
            return requestToken;
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("event=sso.token.parse.failed reason=unexpected error={}", e.getMessage());
            GXSSOMetrics.getInstance().incrementParseFailure();
            return Dict.create();
        }
    }
}
