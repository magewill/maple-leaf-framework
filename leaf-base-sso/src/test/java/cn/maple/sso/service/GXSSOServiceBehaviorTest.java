package cn.maple.sso.service;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXTokenInvalidException;
import cn.maple.core.framework.service.GXBaseCacheService;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.enums.GXTokenFlag;
import cn.maple.sso.plugins.GXSSOPlugin;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.utils.GXSSOHelperUtil;
import cn.maple.sso.web.handler.GXSSODefaultHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXSSOServiceBehaviorTest {
    private final TestSSOService service = new TestSSOService();
    private ApplicationContext originalApplicationContext;

    @BeforeEach
    void setUp() {
        originalApplicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        replaceApplicationContext(null);
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());
    }

    @AfterEach
    void tearDown() {
        ApplicationContext applicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        if (applicationContext instanceof GenericApplicationContext context) {
            context.close();
        }
        RequestContextHolder.resetRequestAttributes();
        replaceApplicationContext(originalApplicationContext);
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());
    }

    @Test
    void cacheShutTokenBypassesCacheLookup() {
        Dict localToken = Dict.create().set("flag", GXTokenFlag.CACHE_SHUT.value());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GXSSOConstant.SSO_TOKEN_ATTR, localToken);
        AtomicInteger cacheGetCount = new AtomicInteger();

        Dict result = service.exposeCacheSSOToken(request, new GXSSOCache() {
            @Override
            public Dict get(int expires, Dict requestToken) {
                cacheGetCount.incrementAndGet();
                return Dict.create();
            }
        });

        assertSame(localToken, result);
        assertEquals(0, cacheGetCount.get());
    }

    @Test
    void setCookieDoesNotMutateCallerTokenAndUsesConfiguredSameSite() {
        GXSSOProperties config = new GXSSOProperties().setCookieSameSite("Strict");
        GXSSOHelperUtil.setSsoConfig(config);
        Dict token = Dict.create().set(config.getTokenName(), "token-value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.setCookie(new MockHttpServletRequest(), response, token);

        assertEquals(Dict.create().set(config.getTokenName(), "token-value"), token);
        assertTrue(response.getHeader("Set-Cookie").contains("SameSite=Strict"));
    }

    @Test
    void setCookieKeepsLaxAsDefaultSameSite() {
        Dict token = Dict.create().set(GXSSOProperties.getInstance().getTokenName(), "token-value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.setCookie(new MockHttpServletRequest(), response, token);

        assertTrue(response.getHeader("Set-Cookie").contains("SameSite=Lax"));
    }

    @Test
    void setCookieStoresBrowserFingerprintForBrowserValidation() {
        AtomicReference<Dict> cachedToken = new AtomicReference<>();
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setCache(new GXSSOCache() {
            @Override
            public boolean set(Dict ssoToken, int expires) {
                cachedToken.set(new Dict(ssoToken));
                return true;
            }
        }));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 Maple Test");
        Dict token = Dict.create().set(GXSSOProperties.getInstance().getTokenName(), "token-value");

        service.setCookie(request, new MockHttpServletResponse(), token);

        assertEquals(64, cachedToken.get().getStr("userAgent").length());
        assertFalse("Mozilla/5.0 Maple Test".equals(cachedToken.get().getStr("userAgent")));
    }

    @Test
    void emptyTokenDoesNotReachValidationPlugins() {
        AtomicInteger validationCount = new AtomicInteger();
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setPluginList(List.of(new GXSSOPlugin() {
            @Override
            public boolean validateToken(Dict ssoToken) {
                validationCount.incrementAndGet();
                return true;
            }
        })));

        Dict token = service.getSSOToken(new MockHttpServletRequest());

        assertEquals(Dict.create(), token);
        assertEquals(0, validationCount.get());
    }

    @Test
    void logoutDoesNotRetryFailedPluginLogout() {
        AtomicInteger logoutCount = new AtomicInteger();
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setPluginList(List.of(new GXSSOPlugin() {
            @Override
            public boolean logout(HttpServletRequest request, HttpServletResponse response) {
                logoutCount.incrementAndGet();
                return false;
            }
        })));

        service.exposeLogout(new MockHttpServletRequest(), new MockHttpServletResponse(), null);

        assertEquals(1, logoutCount.get());
    }

    @Test
    void logoutDoesNotRetryFailedCacheDelete() {
        AtomicInteger deleteCount = new AtomicInteger();

        service.exposeLogout(new MockHttpServletRequest(), new MockHttpServletResponse(), new GXSSOCache() {
            @Override
            public boolean delete(Dict ssoToken) {
                deleteCount.incrementAndGet();
                return false;
            }
        });

        assertEquals(1, deleteCount.get());
    }

    @Test
    void authCookieUsesSecureLongerSessionId() {
        Dict token = Dict.create().set(GXSSOProperties.getInstance().getTokenName(), "token-value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.authCookie(new MockHttpServletRequest(), response, token);

        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie.contains("uid=token-value"));
        assertFalse(setCookie.contains("JSESSIONID"));
    }

    @Test
    void authCookieDoesNotRetryWhenSetCookieFails() {
        AtomicInteger setCookieCount = new AtomicInteger();
        GXAbstractSSOService failingService = new GXAbstractSSOService() {
            @Override
            public void setCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken) {
                setCookieCount.incrementAndGet();
                throw new GXBusinessException("setCookie failed");
            }
        };

        assertThrows(GXBusinessException.class,
                () -> failingService.authCookie(new MockHttpServletRequest(), new MockHttpServletResponse(), Dict.create()));
        assertEquals(1, setCookieCount.get());
    }

    @Test
    void defaultCacheReadsWritesAndDeletesServerSideToken() {
        bindRequest();
        InMemoryCacheService cacheService = new InMemoryCacheService();
        registerContext(cacheService, new GXTokenConfigService() {
        });
        GXSSOCache cache = new GXSSOCache() {
        };
        Dict token = Dict.create()
                .set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, 7L)
                .set(GXTokenConstant.LOGIN_AT_FIELD_NAME, 123L);

        assertTrue(cache.set(token, 30));
        Dict cachedToken = cache.get(30, token);
        assertEquals(7L, cachedToken.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME));
        assertEquals(123L, cachedToken.getLong(GXTokenConstant.LOGIN_AT_FIELD_NAME));
        assertTrue(cache.delete(token));
        assertEquals(Dict.create(), cache.get(30, token));
    }

    @Test
    void defaultCacheUsesTokenEffectivenessHook() {
        bindRequest();
        registerContext(new InMemoryCacheService(), new GXTokenConfigService() {
            @Override
            public boolean verifyTokenEffectiveness() {
                return false;
            }
        });
        GXSSOCache cache = new GXSSOCache() {
        };
        Dict token = Dict.create().set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, 7L);

        assertThrows(GXTokenInvalidException.class, () -> cache.get(30, token));
    }

    @Test
    void tokenConfigProvidesDefaultCacheKey() {
        GXTokenConfigService tokenConfigService = new GXTokenConfigService() {
        };

        assertEquals("ssoTokenKey_7", tokenConfigService.getTokenCacheKey(7L, Dict.create()));
    }

    @Test
    void tokenConfigDefaultSecretRemainsCompatibleButVisible() {
        GXTokenConfigService tokenConfigService = new GXTokenConfigService() {
        };

        assertEquals(GXTokenConstant.TOKEN_SECRET_KEY, tokenConfigService.getTokenSecret());
    }

    @Test
    void userServiceDefaultsFailFast() {
        GXUUserService userService = new GXUUserService() {
        };

        assertThrows(UnsupportedOperationException.class, () -> userService.getUserByUserId(7L));
        assertThrows(UnsupportedOperationException.class, () -> userService.login(Dict.create()));
        assertThrows(UnsupportedOperationException.class, () -> userService.verifyUserToken("token"));
        assertThrows(UnsupportedOperationException.class, userService::loginOut);
    }

    @Test
    void defaultHandlerReturnsAsciiUnauthorizedResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = GXSSODefaultHandler.getInstance()
                .preTokenIsNullAjax(new MockHttpServletRequest(), response);

        assertFalse(result);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Already logged out, please login again."));
    }

    private static void registerContext(GXBaseCacheService cacheService, GXTokenConfigService tokenConfigService) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(GXBaseCacheService.class, () -> cacheService);
        context.registerBean(GXTokenConfigService.class, () -> tokenConfigService);
        context.refresh();
        replaceApplicationContext(context);
    }

    private static void replaceApplicationContext(ApplicationContext applicationContext) {
        ReflectionTestUtils.setField(GXApplicationContextSingleton.INSTANCE, "applicationContext", applicationContext);
    }

    private static void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private static class TestSSOService extends GXAbstractSSOService {
        private Dict exposeCacheSSOToken(MockHttpServletRequest request, GXSSOCache cache) {
            return cacheSSOToken(request, cache);
        }

        private boolean exposeLogout(MockHttpServletRequest request, MockHttpServletResponse response, GXSSOCache cache) {
            return logout(request, response, cache);
        }
    }

    private static class InMemoryCacheService implements GXBaseCacheService {
        private final Map<String, Object> data = new ConcurrentHashMap<>();

        @Override
        public Object setCache(String bucketName, String key, Object value, int expired, TimeUnit timeUnit) {
            data.put(cacheKey(bucketName, key), value);
            return value;
        }

        @Override
        public Object setCache(String bucketName, String key, Object value) {
            data.put(cacheKey(bucketName, key), value);
            return value;
        }

        @Override
        public Object setCache(String bucketName, String key, String value, int expired, TimeUnit timeUnit) {
            data.put(cacheKey(bucketName, key), value);
            return value;
        }

        @Override
        public Object setCache(String bucketName, String key, String value) {
            data.put(cacheKey(bucketName, key), value);
            return value;
        }

        @Override
        public Object getCache(String bucketName, String key) {
            return data.get(cacheKey(bucketName, key));
        }

        @Override
        public Object deleteCache(String bucketName, String key) {
            return data.remove(cacheKey(bucketName, key));
        }

        @Override
        public Long getCacheRemainTimeToLive(String bucketName, String keyName) {
            return null;
        }

        @Override
        public boolean updateCacheExpiredTime(String bucketName, String keyName, Integer expired, Integer refreshThreshold) {
            return false;
        }

        @Override
        public void clear(String bucketName) {
            data.clear();
        }

        @Override
        public Integer size(String bucketName) {
            return data.size();
        }

        @Override
        public boolean exists(String bucketName, String key) {
            return data.containsKey(cacheKey(bucketName, key));
        }

        private String cacheKey(String bucketName, String key) {
            return bucketName + ":" + key;
        }
    }
}
