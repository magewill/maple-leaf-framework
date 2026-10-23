package cn.maple.sso.utils;

import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.util.GXAuthCodeUtils;
import cn.maple.sso.metrics.GXSSOMetrics;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.service.GXTokenConfigService;
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

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the static helper: bean lookup caching and metrics.
 */
@ResourceLock("sso-global-config")
class GXSSOHelperUtilTest {
    private ApplicationContext originalApplicationContext;

    @BeforeEach
    void setUp() {
        originalApplicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        GXSSOMetrics.reset();
        GXSSOHelperUtil.setBeanResolverForTests(null);
        GXSSOHelperUtil.resetBeanLookupCount();
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());
    }

    @AfterEach
    void tearDown() {
        ApplicationContext applicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        if (applicationContext instanceof GenericApplicationContext context) {
            context.close();
        }
        RequestContextHolder.resetRequestAttributes();
        GXSSOTestDoubles.restoreApplicationContext(originalApplicationContext);
        GXSSOHelperUtil.setBeanResolverForTests(null);
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());
        GXSSOMetrics.reset();
    }

    @Test
    void beanLookupIsCachedAfterWarmup() {
        GXSSOTestDoubles.FixedTokenConfigService tokenConfigService = new GXSSOTestDoubles.FixedTokenConfigService();
        AtomicInteger resolutions = new AtomicInteger();
        GXSSOHelperUtil.setBeanResolverForTests(type -> {
            resolutions.incrementAndGet();
            return GXTokenConfigService.class.equals(type) ? tokenConfigService : null;
        });

        GXTokenConfigService first = GXSSOHelperUtil.bean(GXTokenConfigService.class);
        GXTokenConfigService second = GXSSOHelperUtil.bean(GXTokenConfigService.class);

        assertEquals(first, second);
        assertEquals(1, resolutions.get(), "a resolved bean must be cached for the request path");
    }

    @Test
    void setSsoConfigInvalidatesBeanCache() {
        AtomicInteger resolutions = new AtomicInteger();
        GXSSOHelperUtil.setBeanResolverForTests(type -> {
            resolutions.incrementAndGet();
            return null;
        });

        GXSSOHelperUtil.bean(GXTokenConfigService.class);
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());
        GXSSOHelperUtil.bean(GXTokenConfigService.class);

        assertEquals(2, resolutions.get(), "configuration replacement must drop cached beans");
    }

    @Test
    void cacheOperationsReuseCachedBeans() {
        GXSSOTestDoubles.InMemoryCacheService cacheService = new GXSSOTestDoubles.InMemoryCacheService();
        GXTokenConfigService tokenConfigService = new GXSSOTestDoubles.FixedTokenConfigService();
        AtomicInteger resolutions = new AtomicInteger();
        GXSSOHelperUtil.setBeanResolverForTests(type -> {
            resolutions.incrementAndGet();
            if (GXTokenConfigService.class.equals(type)) {
                return tokenConfigService;
            }
            if (cn.maple.core.framework.service.GXBaseCacheService.class.equals(type)) {
                return cacheService;
            }
            return null;
        });
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        cn.maple.sso.cache.GXSSOCache cache = new cn.maple.sso.cache.GXSSOCache() {
        };
        Dict token = Dict.create()
                .set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, 7L)
                .set(GXTokenConstant.LOGIN_AT_FIELD_NAME, 100L);

        cache.set(token, 7200);
        int warmLookups = resolutions.get();
        int warmCounted = GXSSOHelperUtil.beanLookupCount();
        assertTrue(warmCounted >= 2, "both collaborators must be resolved once during warm-up");
        assertEquals(1L, GXSSOMetrics.snapshot().activeTokens(), "the cache size must be published after a write");

        cache.set(token, 7200);
        assertTrue(!cache.get(7200, token).isEmpty());
        cache.delete(token);
        assertEquals(0L, GXSSOMetrics.snapshot().activeTokens(), "the cache size must follow a delete");

        assertEquals(warmLookups, resolutions.get(),
                "the request path must reuse cached bean references instead of resolving per call");
        assertEquals(warmCounted, GXSSOHelperUtil.beanLookupCount(),
                "the bean lookup counter must not grow after warm-up");
    }

    @Test
    void metricsIncrementOnParseSuccess() {
        GXSSOTestDoubles.FixedTokenConfigService tokenConfigService = new GXSSOTestDoubles.FixedTokenConfigService();
        registerContext(tokenConfigService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        String token = GXAuthCodeUtils.authCodeEncode(
                JSONUtil.toJsonStr(Dict.create().set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, 7L)
                        .set(GXTokenConstant.LOGIN_AT_FIELD_NAME, 100L)),
                tokenConfigService.getTokenSecret(), 7200);

        Dict parsed = GXSSOHelperUtil.parser(token, true);

        assertEquals(7L, parsed.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME));
        assertEquals(1, GXSSOMetrics.snapshot().parseSuccess());
        assertEquals(0, GXSSOMetrics.snapshot().parseFailure());
    }

    @Test
    void metricsIncrementOnParseFailure() {
        GXSSOTestDoubles.FixedTokenConfigService tokenConfigService = new GXSSOTestDoubles.FixedTokenConfigService();
        registerContext(tokenConfigService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Dict parsed = GXSSOHelperUtil.parser("not-a-token", true);

        assertTrue(parsed.isEmpty());
        assertEquals(1, GXSSOMetrics.snapshot().parseFailure());
    }

    @Test
    void parserSkipsRpcContext() {
        registerContext(new GXSSOTestDoubles.FixedTokenConfigService());
        RequestContextHolder.resetRequestAttributes();

        Dict parsed = GXSSOHelperUtil.parser("any-token", true);

        assertTrue(parsed.isEmpty(), "no HTTP context means no identity (documented behaviour)");
    }

    @Test
    void nullConfigIsRejected() {
        boolean thrown = false;
        try {
            GXSSOHelperUtil.setSsoConfig(null);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown, "setSsoConfig(null) must fail fast");
    }

    private void registerContext(GXTokenConfigService tokenConfigService) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(GXTokenConfigService.class, () -> tokenConfigService);
        context.refresh();
        GXSSOTestDoubles.installApplicationContext(context);
    }
}
