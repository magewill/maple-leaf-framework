package cn.maple.sso.cache;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.service.GXBaseCacheService;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.enums.GXSSOTokenStateEnums;
import cn.maple.sso.metrics.GXSSOMetrics;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.support.GXSSOTestDoubles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for {@link GXSSOCache} default methods.
 */
@ResourceLock("sso-global-config")
class GXSSOCacheContractTest {
    private final GXSSOCache cache = new GXSSOCache() {
    };

    private ApplicationContext originalApplicationContext;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        originalApplicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        GXSSOMetrics.reset();
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        ApplicationContext applicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        if (applicationContext instanceof GenericApplicationContext context) {
            context.close();
        }
        RequestContextHolder.resetRequestAttributes();
        GXSSOTestDoubles.restoreApplicationContext(originalApplicationContext);
        GXSSOMetrics.reset();
    }

    @Test
    void getNeverThrowsAndReturnsEmptyDictWithReason() {
        registerContext(new GXSSOTestDoubles.InMemoryCacheService(),
                new GXSSOTestDoubles.FixedTokenConfigService().tokenEffective(false));

        Dict result = cache.get(7200, token(7L));

        assertTrue(result.isEmpty(), "an ineffective token must resolve to an empty dict instead of an exception");
        assertEquals(GXSSOTokenStateEnums.EXPIRED.code(), request.getAttribute(GXSSOConstant.SSO_TOKEN_STATE));
    }

    @Test
    void deleteIsIdempotent() {
        registerContext(new GXSSOTestDoubles.InMemoryCacheService(),
                new GXSSOTestDoubles.FixedTokenConfigService());

        assertTrue(cache.delete(token(7L)), "deleting a missing key is a successful logout");
        assertTrue(cache.delete(token(7L)), "second delete must stay successful");
    }

    @Test
    void kickLoginWithNullReturnsFalse() {
        registerContext(new GXSSOTestDoubles.InMemoryCacheService(),
                new GXSSOTestDoubles.FixedTokenConfigService());
        cn.maple.sso.utils.GXSSOHelperUtil.setSsoConfig(
                new GXSSOProperties().setCache(new GXSSOCache() {
                }));

        assertFalse(new cn.maple.sso.service.impl.GXConfigurableAbstractSSOServiceImpl().kickLogin(null),
                "kickLogin(null) must return false instead of throwing");
    }

    @Test
    void cacheReadFailureIsReportedAsUnavailable() {
        GXSSOTestDoubles.InMemoryCacheService failingCache = new GXSSOTestDoubles.InMemoryCacheService() {
            @Override
            public Object getCache(String bucketName, String key) {
                throw new IllegalStateException("redis is down");
            }
        };
        registerContext(failingCache, new GXSSOTestDoubles.FixedTokenConfigService());

        Dict result = cache.get(7200, token(7L));

        assertTrue(result.isEmpty(), "a cache read failure resolves to an empty dict");
        assertEquals(GXSSOTokenStateEnums.CACHE_UNAVAILABLE.code(), request.getAttribute(GXSSOConstant.SSO_TOKEN_STATE));
    }

    @Test
    void defaultConfigCarriesPositiveTtl() {
        GXSSOProperties properties = new GXSSOProperties();

        assertTrue(properties.getCacheExpires() > 0, "default cache-expires must be an absolute positive TTL");
        assertFalse(properties.isCacheFallbackEnabled(), "fallback must be disabled by default");
    }

    private Dict token(Long userId) {
        return Dict.create()
                .set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, userId)
                .set(GXTokenConstant.LOGIN_AT_FIELD_NAME, 1700000000L);
    }

    private void registerContext(GXBaseCacheService cacheService,
                                 cn.maple.sso.service.GXTokenConfigService tokenConfigService) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(GXBaseCacheService.class, () -> cacheService);
        context.registerBean(cn.maple.sso.service.GXTokenConfigService.class, () -> tokenConfigService);
        context.refresh();
        GXSSOTestDoubles.installApplicationContext(context);
    }
}
