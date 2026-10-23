package cn.maple.sso.support;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.service.GXBaseCacheService;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.oauth.GXSSOAuthorization;
import cn.maple.sso.plugins.GXSSOPlugin;
import cn.maple.sso.service.GXTokenConfigService;
import cn.maple.sso.service.GXUUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test doubles for the SSO module.
 *
 * <p>All collaboration points ({@link GXSSOCache}, {@link GXTokenConfigService},
 * {@link GXBaseCacheService}, {@link GXSSOPlugin}, {@link GXSSOAuthorization},
 * {@link GXUUserService}) are injectable on purpose: regression tests must never rely on
 * reflection to build failure scenarios.</p>
 */
public final class GXSSOTestDoubles {
    private GXSSOTestDoubles() {
    }

    /**
     * Register a Spring context in the framework singleton, replacing any active one.
     *
     * <p>Uses the public API of {@link GXApplicationContextSingleton} so that tests never need
     * {@code ReflectionTestUtils} to reach the private field (FR-023). The singleton refuses to
     * replace an active context, so the current one is cleared first.</p>
     *
     * @param context context to install, must not be {@code null}
     */
    public static void installApplicationContext(ApplicationContext context) {
        ApplicationContext current = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        if (current != null && current != context) {
            GXApplicationContextSingleton.INSTANCE.clearApplicationContext(current);
        }
        GXApplicationContextSingleton.INSTANCE.setApplicationContext(context);
    }

    /**
     * Restore the context captured before {@link #installApplicationContext(ApplicationContext)}.
     *
     * @param previous context captured in {@code @BeforeEach}, may be {@code null}
     */
    public static void restoreApplicationContext(@Nullable ApplicationContext previous) {
        if (previous != null) {
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(previous);
            return;
        }
        ApplicationContext current = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        if (current != null) {
            GXApplicationContextSingleton.INSTANCE.clearApplicationContext(current);
        }
    }

    /**
     * In memory {@link GXBaseCacheService}.
     */
    public static class InMemoryCacheService implements GXBaseCacheService {
        private final Map<String, Object> data = new ConcurrentHashMap<>();

        public Map<String, Object> data() {
            return data;
        }

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

    /**
     * {@link GXSSOCache} backed by {@link InMemoryCacheService}, with call counters.
     */
    public static class CountingSsoCache implements GXSSOCache {
        private final InMemoryCacheService cacheService = new InMemoryCacheService();

        private final AtomicInteger getCount = new AtomicInteger();

        private final AtomicInteger setCount = new AtomicInteger();

        private final AtomicInteger deleteCount = new AtomicInteger();

        private final Map<String, Dict> store = new ConcurrentHashMap<>();

        @Override
        public Dict get(int expires, Dict requestToken) {
            getCount.incrementAndGet();
            Long userId = requestToken == null ? null : requestToken.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME);
            if (userId == null) {
                return Dict.create();
            }
            Dict cached = store.get(String.valueOf(userId));
            return cached == null ? Dict.create() : new Dict(cached);
        }

        @Override
        public boolean set(Dict ssoToken, int expires) {
            setCount.incrementAndGet();
            Long userId = ssoToken.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME);
            if (userId == null) {
                return false;
            }
            store.put(String.valueOf(userId), new Dict(ssoToken));
            return true;
        }

        @Override
        public boolean delete(Dict ssoToken) {
            deleteCount.incrementAndGet();
            Long userId = ssoToken.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME);
            if (userId == null) {
                return true;
            }
            store.remove(String.valueOf(userId));
            return true;
        }

        public int getCount() {
            return getCount.get();
        }

        public int setCount() {
            return setCount.get();
        }

        public int deleteCount() {
            return deleteCount.get();
        }

        public Dict stored(Long userId) {
            Dict value = store.get(String.valueOf(userId));
            return value == null ? null : new Dict(value);
        }

        public InMemoryCacheService cacheService() {
            return cacheService;
        }
    }

    /**
     * {@link GXTokenConfigService} with switchable behaviour.
     */
    public static class FixedTokenConfigService implements GXTokenConfigService {
        private String secret = "unit-test-secret-3f7a9c1b";

        private boolean loginStatus = true;

        private boolean tokenEffective = true;

        public FixedTokenConfigService secret(String value) {
            this.secret = value;
            return this;
        }

        public FixedTokenConfigService loginStatus(boolean value) {
            this.loginStatus = value;
            return this;
        }

        public FixedTokenConfigService tokenEffective(boolean value) {
            this.tokenEffective = value;
            return this;
        }

        @Override
        public String getTokenSecret() {
            return secret;
        }

        @Override
        public boolean checkLoginStatus() {
            return loginStatus;
        }

        @Override
        public boolean verifyTokenEffectiveness() {
            return tokenEffective;
        }
    }

    /**
     * {@link GXSSOAuthorization} with a fixed answer and call counter.
     */
    public static class RecordingAuthorization implements GXSSOAuthorization {
        private volatile boolean permitted;

        private final AtomicInteger callCount = new AtomicInteger();

        public RecordingAuthorization(boolean permitted) {
            this.permitted = permitted;
        }

        public RecordingAuthorization permitted(boolean value) {
            this.permitted = value;
            return this;
        }

        @Override
        public boolean isPermitted(Dict token, String permission) {
            callCount.incrementAndGet();
            return permitted;
        }

        public int callCount() {
            return callCount.get();
        }
    }

    /**
     * {@link GXSSOPlugin} recording every callback, with configurable answers.
     */
    public static class RecordingPlugin implements GXSSOPlugin {
        private volatile boolean loginResult = true;

        private volatile boolean validateResult = true;

        private volatile boolean logoutResult = true;

        private final AtomicInteger loginCount = new AtomicInteger();

        private final AtomicInteger validateCount = new AtomicInteger();

        private final AtomicInteger logoutCount = new AtomicInteger();

        public RecordingPlugin loginResult(boolean value) {
            this.loginResult = value;
            return this;
        }

        public RecordingPlugin validateResult(boolean value) {
            this.validateResult = value;
            return this;
        }

        public RecordingPlugin logoutResult(boolean value) {
            this.logoutResult = value;
            return this;
        }

        @Override
        public boolean login(HttpServletRequest request, HttpServletResponse response) {
            loginCount.incrementAndGet();
            return loginResult;
        }

        @Override
        public boolean validateToken(Dict ssoToken) {
            validateCount.incrementAndGet();
            return validateResult;
        }

        @Override
        public boolean logout(HttpServletRequest request, HttpServletResponse response) {
            logoutCount.incrementAndGet();
            return logoutResult;
        }

        public int loginCount() {
            return loginCount.get();
        }

        public int validateCount() {
            return validateCount.get();
        }

        public int logoutCount() {
            return logoutCount.get();
        }
    }

    /**
     * {@link GXUUserService} returning a fixed user, or nothing.
     */
    public static class StubUserService implements GXUUserService {
        private volatile boolean userExists = true;

        public StubUserService userExists(boolean value) {
            this.userExists = value;
            return this;
        }

        @Override
        public Dict getUserByUserId(Long userId) {
            if (!userExists) {
                return Dict.create();
            }
            return Dict.create().set("userId", userId).set("username", "maple");
        }
    }
}
