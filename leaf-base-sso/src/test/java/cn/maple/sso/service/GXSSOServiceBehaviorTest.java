package cn.maple.sso.service;

import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;

import cn.maple.core.framework.service.GXBaseCacheService;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.enums.GXSSOTokenStateEnums;
import cn.maple.sso.enums.GXTokenFlag;
import cn.maple.sso.plugins.GXSSOPlugin;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.support.GXSSOTestDoubles;
import cn.maple.sso.utils.GXSSOHelperUtil;
import cn.maple.sso.web.handler.GXSSODefaultHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("sso-global-config")
class GXSSOServiceBehaviorTest {
    private final TestSSOService service = new TestSSOService();
    private ApplicationContext originalApplicationContext;

    @BeforeEach
    void setUp() {
        originalApplicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        GXSSOTestDoubles.restoreApplicationContext(null);
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
        registerContext(new InMemoryCacheService(), new GXTokenConfigService() {
        });
        GXSSOProperties config = new GXSSOProperties().setCookieSameSite("Strict").setCache(passThroughCache());
        GXSSOHelperUtil.setSsoConfig(config);
        Dict token = Dict.create().set(config.getTokenName(), "token-value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.setCookie(new MockHttpServletRequest(), response, token);

        assertEquals(Dict.create().set(config.getTokenName(), "token-value"), token);
        assertTrue(response.getHeader("Set-Cookie").contains("SameSite=Strict"));
    }

    @Test
    void setCookieKeepsLaxAsDefaultSameSite() {
        registerContext(new InMemoryCacheService(), new GXTokenConfigService() {
        });
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setCache(passThroughCache()));
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
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GXSSOConstant.SSO_TOKEN_ATTR,
                Dict.create().set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, 7L));

        service.exposeLogout(request, new MockHttpServletResponse(), null);

        assertEquals(1, logoutCount.get());
    }

    @Test
    void anonymousLogoutSkipsPlugins() {
        AtomicInteger logoutCount = new AtomicInteger();
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setPluginList(List.of(new GXSSOPlugin() {
            @Override
            public boolean logout(HttpServletRequest request, HttpServletResponse response) {
                logoutCount.incrementAndGet();
                return true;
            }
        })));

        assertTrue(service.clearLogin(new MockHttpServletRequest(), new MockHttpServletResponse()));

        assertEquals(0, logoutCount.get(), "a request without a token has no session to log out");
    }

    @Test
    void anonymousLogoutWithoutCookieWritesNoSetCookie() {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(service.clearLogin(new MockHttpServletRequest(), response));

        assertNull(response.getHeader("Set-Cookie"), "a request without a cookie must not emit Set-Cookie");
    }

    @Test
    void degradedModeClampsCookieMaxAgeToFallbackTtl() {
        registerContext(new InMemoryCacheService(), new GXTokenConfigService() {
        });
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setCacheExpires(-1).setCacheFallbackEnabled(true));

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.setCookie(new MockHttpServletRequest(), response, Dict.create().set("userId", 7L));

        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=900"),
                "no-expiry config must degrade to the fallback ceiling, not a one second cookie");
    }

    @Test
    void logoutDoesNotRetryFailedCacheDelete() {
        AtomicInteger deleteCount = new AtomicInteger();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GXSSOConstant.SSO_TOKEN_ATTR, Dict.create().set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, 7L));

        service.exposeLogout(request, new MockHttpServletResponse(), new GXSSOCache() {
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
        registerContext(new InMemoryCacheService(), new GXTokenConfigService() {
        });
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setCache(passThroughCache()));
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
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        registerContext(new InMemoryCacheService(), new GXTokenConfigService() {
            @Override
            public boolean verifyTokenEffectiveness() {
                return false;
            }
        });
        GXSSOCache cache = new GXSSOCache() {
        };
        Dict token = Dict.create().set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, 7L);

        Dict result = cache.get(30, token);

        assertEquals(Dict.create(), result, "an ineffective token resolves to an empty dict");
        assertEquals(GXSSOTokenStateEnums.EXPIRED.code(), servletRequest.getAttribute(GXSSOConstant.SSO_TOKEN_STATE));
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
        assertEquals("application/json;charset=UTF-8", response.getContentType(),
                "the unauthorized body must be produced by the unified response builder");
        JSONObject body = JSONUtil.parseObj(response.getContentAsString());
        assertEquals(401, body.getInt("code"));
        assertEquals("Already logged out, please login again.", body.getStr("msg"));
        assertTrue(body.containsKey("data"), "GXResultUtils keeps data even when it is null");
    }

    @Test
    void loginFailsWhenCacheUnavailable() {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());

        assertThrows(GXBusinessException.class,
                () -> service.setCookie(new MockHttpServletRequest(), new MockHttpServletResponse(),
                        Dict.create().set("userId", 7L)),
                "no cache implementation and no explicit fallback must fail closed");
    }

    @Test
    void loginDegradesWithBoundedTtlWhenFallbackEnabled() {
        registerContext(new InMemoryCacheService(), new GXTokenConfigService() {
        });
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setCacheFallbackEnabled(true).setCookieMaxAge(3600));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.setCookie(new MockHttpServletRequest(), response, Dict.create().set("userId", 7L));

        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie != null && setCookie.contains("Max-Age="), "degraded token must be bounded");
        int maxAge = Integer.parseInt(setCookie.replaceAll(".*Max-Age=(\\d+).*", "$1"));
        assertTrue(maxAge > 0 && maxAge <= 900, "degraded token TTL must not exceed 900 seconds, was " + maxAge);
    }

    @Test
    void pluginLoginFailureRollsBackCacheAndThrows() {
        registerContext(new InMemoryCacheService(), new GXTokenConfigService() {
        });
        AtomicInteger retained = new AtomicInteger();
        GXSSOCache cache = new GXSSOCache() {
            @Override
            public boolean set(Dict ssoToken, int expires) {
                retained.incrementAndGet();
                return true;
            }

            @Override
            public boolean delete(Dict ssoToken) {
                retained.decrementAndGet();
                return true;
            }
        };
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setCache(cache).setPluginList(List.of(new GXSSOPlugin() {
            @Override
            public boolean login(HttpServletRequest request, HttpServletResponse response) {
                return false;
            }
        })));

        assertThrows(GXBusinessException.class,
                () -> service.setCookie(new MockHttpServletRequest(), new MockHttpServletResponse(),
                        Dict.create().set("userId", 7L)));
        assertEquals(0, retained.get(), "a rejected login must roll back the cache entry");
    }

    @Test
    void logoutIsIdempotentAndReturnsTrue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(service.clearLogin(request, response));
        assertTrue(service.clearLogin(request, response), "second logout of a missing cookie is still a success");
    }

    @Test
    void invalidSameSiteCombinationIsRejected() {
        registerContext(new InMemoryCacheService(), new GXTokenConfigService() {
        });
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties()
                .setCookieSameSite("None")
                .setCookieSecure(false)
                .setCache(new GXSSOCache() {
                }));

        GXBusinessException exception = assertThrows(GXBusinessException.class,
                () -> service.setCookie(new MockHttpServletRequest(), new MockHttpServletResponse(),
                        Dict.create().set("userId", 7L)),
                "SameSite=None requires Secure");
        assertTrue(exception.getMessage().contains("SameSite"),
                "the failure must come from the SameSite validation, not from another guard");
    }

    @Test
    void unsupportedSameSiteValueIsRejected() {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties()
                .setCookieSameSite("Laxx")
                .setCache(new GXSSOCache() {
                }));

        GXBusinessException exception = assertThrows(GXBusinessException.class,
                () -> service.setCookie(new MockHttpServletRequest(), new MockHttpServletResponse(),
                        Dict.create().set("userId", 7L)),
                "an unsupported SameSite value must not reach the Set-Cookie header");
        assertTrue(exception.getMessage().contains("SameSite"),
                "the failure must come from the SameSite validation, not from another guard");
    }

    @Test
    void logoutWithoutLogoutUrlReturnsUnifiedJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.logout(new MockHttpServletRequest(), response);

        assertEquals("application/json;charset=UTF-8", response.getContentType(),
                "logout must answer with the unified response instead of a raw text body");
        JSONObject body = JSONUtil.parseObj(response.getContentAsString());
        assertEquals(200, body.getInt("code"));
        assertEquals("Logged out", body.getStr("msg"));
    }

    @Test
    void authCookieInvalidatesExistingSessionOnly() {
        registerContext(new InMemoryCacheService(), new GXTokenConfigService() {
        });
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setCache(passThroughCache()));
        Dict token = Dict.create().set(GXSSOProperties.getInstance().getTokenName(), "token-value");

        MockHttpServletRequest withSession = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        withSession.setSession(session);
        service.authCookie(withSession, new MockHttpServletResponse(), token);
        assertTrue(session.isInvalid(), "an existing session must be invalidated on login");

        MockHttpServletRequest withoutSession = new MockHttpServletRequest();
        service.authCookie(withoutSession, new MockHttpServletResponse(), token);
        assertNull(withoutSession.getSession(false), "login must not create a session");
    }

    @Test
    void blankRequestIpIsRejectedWhenIpCheckEnabled() {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setCookieCheckIp(true));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("");
        Dict token = Dict.create().set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, 7L).set("ip", "10.0.0.1");

        assertTrue(service.exposeCheckIpBrowser(request, token).isEmpty(), "a blank request ip must be rejected");

        request.setRemoteAddr("10.0.0.1");
        assertFalse(service.exposeCheckIpBrowser(request, token).isEmpty(), "a matching ip must be accepted");
    }

    @Test
    void cookieMaxAgeAttributeAcceptsNonIntegerTypes() {
        registerContext(new InMemoryCacheService(), new GXTokenConfigService() {
        });
        GXSSOProperties config = new GXSSOProperties().setCache(passThroughCache()).setCookieMaxAge(600);
        GXSSOHelperUtil.setSsoConfig(config);
        Dict token = Dict.create().set(config.getTokenName(), "token-value");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GXSSOConstant.SSO_COOKIE_MAX_AGE, 120L);
        MockHttpServletResponse longResponse = new MockHttpServletResponse();
        service.setCookie(request, longResponse, token);
        assertTrue(longResponse.getHeader("Set-Cookie").contains("Max-Age=120"),
                "a Number attribute must be honoured");

        request.setAttribute(GXSSOConstant.SSO_COOKIE_MAX_AGE, "not-a-number");
        MockHttpServletResponse fallbackResponse = new MockHttpServletResponse();
        service.setCookie(request, fallbackResponse, token);
        assertTrue(fallbackResponse.getHeader("Set-Cookie").contains("Max-Age=600"),
                "an unsupported attribute type must fall back to the configured value");
    }

    private static GXSSOCache passThroughCache() {
        return new GXSSOCache() {
            @Override
            public boolean set(Dict ssoToken, int expires) {
                return true;
            }

            @Override
            public boolean delete(Dict ssoToken) {
                return true;
            }
        };
    }

    private static void registerContext(GXBaseCacheService cacheService, GXTokenConfigService tokenConfigService) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(GXBaseCacheService.class, () -> cacheService);
        context.registerBean(GXTokenConfigService.class, () -> tokenConfigService);
        context.refresh();
        GXSSOTestDoubles.installApplicationContext(context);
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

        private Dict exposeCheckIpBrowser(MockHttpServletRequest request, Dict token) {
            return checkIpBrowser(request, token);
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
