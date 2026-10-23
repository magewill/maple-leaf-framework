package cn.maple.sso.service;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.sso.captcha.CaptchaStoreSession;
import cn.maple.sso.metrics.GXSSOMetrics;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.support.GXSSOTestDoubles;
import cn.maple.sso.utils.GXSSOHelperUtil;
import cn.maple.sso.web.interceptor.GXHandlerMethodAnnotationUtilsTestAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency and shared state tests.
 */
@ResourceLock("sso-global-config")
class GXSSOConcurrencyTest {
    private ApplicationContext originalApplicationContext;

    @BeforeEach
    void setUp() {
        originalApplicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        GXSSOMetrics.reset();
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());
    }

    @AfterEach
    void tearDown() {
        ApplicationContext applicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        if (applicationContext instanceof GenericApplicationContext context) {
            context.close();
        }
        GXSSOTestDoubles.restoreApplicationContext(originalApplicationContext);
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());
        GXSSOMetrics.reset();
    }

    @Test
    void concurrentLoginsKeepMergedFields() throws Exception {
        GXSSOTestDoubles.CountingSsoCache cache = new GXSSOTestDoubles.CountingSsoCache();
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setCache(cache));
        registerContext(new GXSSOTestDoubles.InMemoryCacheService(), new GXSSOTestDoubles.FixedTokenConfigService());
        TestSSOService service = new TestSSOService();

        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    MockHttpServletRequest request = new MockHttpServletRequest();
                    request.addHeader("User-Agent", "agent-" + index);
                    service.setCookie(request, new MockHttpServletResponse(),
                            Dict.create().set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, 9L));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Dict stored = cache.stored(9L);
        assertNotNull(stored, "a complete token must remain in the cache");
        assertEquals(9L, stored.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME));
        assertNotNull(stored.getStr("userAgent"), "userAgent must not be lost by a concurrent writer");
        assertNotNull(stored.getStr("createTime"));
        assertNotNull(stored.getLong(GXTokenConstant.LOGIN_AT_FIELD_NAME));
        assertTrue(stored.getStr(GXSSOProperties.getInstance().getTokenName()) != null);
    }

    @Test
    void concurrentAnnotationLookupIsStable() throws Exception {
        int threads = 8;
        int iterations = 200;
        CaptchaHandlers handlers = new CaptchaHandlers();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int j = 0; j < iterations; j++) {
                        HandlerMethod handlerMethod = handlers.handler(j % 4);
                        if (!GXHandlerMethodAnnotationUtilsTestAccess.hasIgnoreAnnotation(handlerMethod)) {
                            errors.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0, errors.get(), "annotation lookup must be stable under concurrency");
    }

    @Test
    void concurrentKickAndValidateDoesNotThrow() throws Exception {
        GXSSOTestDoubles.CountingSsoCache cache = new GXSSOTestDoubles.CountingSsoCache();
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setCache(cache));
        registerContext(new GXSSOTestDoubles.InMemoryCacheService(), new GXSSOTestDoubles.FixedTokenConfigService());
        TestSSOService service = new TestSSOService();
        cache.set(Dict.create().set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, 5L)
                .set(GXTokenConstant.LOGIN_AT_FIELD_NAME, 100L), 7200);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit((Callable<Void>) () -> {
                    start.await();
                    service.kickLogin(5L);
                    return null;
                }));
                futures.add(pool.submit((Callable<Void>) () -> {
                    start.await();
                    Dict token = Dict.create()
                            .set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, 5L)
                            .set(GXTokenConstant.LOGIN_AT_FIELD_NAME, 100L)
                            .set("createTime", 1L);
                    cache.get(7200, token);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentCaptchaVerificationConsumesOnce() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        int rounds = 50;
        int doubleConsumed = 0;

        for (int round = 0; round < rounds; round++) {
            String ticket = "ticket-" + round;
            new CaptchaStoreSession(request).put(ticket, "ABCD", 60);
            CaptchaStoreSession store = new CaptchaStoreSession(request);

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<String>> futures = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        return store.consume(ticket);
                    }));
                }
                start.countDown();
                int success = 0;
                for (Future<String> future : futures) {
                    if (future.get(5, TimeUnit.SECONDS) != null) {
                        success++;
                    }
                }
                if (success != 1) {
                    doubleConsumed++;
                }
            } finally {
                pool.shutdownNow();
            }
        }

        assertEquals(0, doubleConsumed, "a captcha ticket must be consumable exactly once");
    }

    private void registerContext(GXSSOTestDoubles.InMemoryCacheService cacheService,
                                 GXTokenConfigService tokenConfigService) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(cn.maple.core.framework.service.GXBaseCacheService.class, () -> cacheService);
        context.registerBean(GXTokenConfigService.class, () -> tokenConfigService);
        context.refresh();
        GXSSOTestDoubles.installApplicationContext(context);
    }

    private static class TestSSOService extends GXAbstractSSOService {
    }

    private static class CaptchaHandlers {
        private final Object[] controllers = new Object[4];

        CaptchaHandlers() {
            for (int i = 0; i < controllers.length; i++) {
                controllers[i] = new HandlerHolder();
            }
        }

        HandlerMethod handler(int index) throws NoSuchMethodException {
            return new HandlerMethod(controllers[index],
                    HandlerHolder.class.getMethod("handle" + index));
        }
    }

    private static class HandlerHolder {
        @cn.maple.core.framework.annotation.GXIgnoreLoginIntercept
        public void handle0() {
        }

        @cn.maple.core.framework.annotation.GXIgnoreLoginIntercept
        public void handle1() {
        }

        @cn.maple.core.framework.annotation.GXIgnoreLoginIntercept
        public void handle2() {
        }

        @cn.maple.core.framework.annotation.GXIgnoreLoginIntercept
        public void handle3() {
        }
    }
}
