package cn.maple.sso.cache;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.service.GXBaseCacheService;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;

import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.enums.GXSSOTokenStateEnums;
import cn.maple.sso.metrics.GXSSOMetrics;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.service.GXTokenConfigService;
import cn.maple.sso.utils.GXSSOHelperUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Server side token cache contract.
 *
 * <p>Failures this contract can detect by itself (missing cache bean, missing cache service,
 * invalid user id, read/write error on the cache service) never surface as an exception from
 * {@code get} / {@code delete}: the call returns an empty {@code Dict} or a boolean and the
 * reason is published through the request attribute {@link GXSSOConstant#SSO_TOKEN_STATE}.</p>
 *
 * <p>Exceptions raised by a custom {@code GXTokenConfigService} implementation (for example
 * {@code getEfficaciousToken} or {@code getCacheBucketName}) are not swallowed and remain the
 * responsibility of that implementation.</p>
 */
public interface GXSSOCache {
    Logger LOG = LoggerFactory.getLogger(GXSSOCache.class);

    default Dict get(int expires, Dict requestToken) {
        if (GXCurrentRequestContextUtils.isRPC() || CollUtil.isEmpty(requestToken)) {
            markState(GXSSOTokenStateEnums.ABSENT);
            return Dict.create();
        }

        GXTokenConfigService tokenConfigService = GXSSOHelperUtil.bean(GXTokenConfigService.class);
        GXBaseCacheService cacheService = GXSSOHelperUtil.bean(GXBaseCacheService.class);
        if (tokenConfigService == null || cacheService == null) {
            markState(GXSSOTokenStateEnums.CACHE_UNAVAILABLE);
            GXSSOMetrics.getInstance().incrementCacheMiss();
            LOG.debug("SSO cache is unavailable: tokenConfigServicePresent={}, cacheServicePresent={}",
                    tokenConfigService != null, cacheService != null);
            return Dict.create();
        }

        if (!tokenConfigService.checkLoginStatus() || !tokenConfigService.verifyTokenEffectiveness()) {
            markState(GXSSOTokenStateEnums.EXPIRED);
            return Dict.create();
        }

        Long id = resolveUserId(requestToken);
        if (id == null) {
            markState(GXSSOTokenStateEnums.ABSENT);
            GXSSOMetrics.getInstance().incrementInvalidToken();
            return Dict.create();
        }

        String tokenCacheKey = tokenConfigService.getTokenCacheKey(id, requestToken);
        String cacheBucketName = tokenConfigService.getCacheBucketName();
        Object cachedToken;
        try {
            cachedToken = cacheService.getCache(cacheBucketName, tokenCacheKey);
        } catch (Exception e) {
            LOG.warn("Read SSO token cache failed: bucket={}, key={}, error={}",
                    cacheBucketName, tokenCacheKey, e.getMessage());
            markState(GXSSOTokenStateEnums.CACHE_UNAVAILABLE);
            return Dict.create();
        }

        if (Objects.isNull(cachedToken)) {
            markState(GXSSOTokenStateEnums.CACHE_MISS);
            GXSSOMetrics.getInstance().incrementCacheMiss();
            return Dict.create();
        }

        markState(GXSSOTokenStateEnums.ACTIVE);
        GXSSOMetrics.getInstance().incrementCacheHit();
        return tokenConfigService.getEfficaciousToken(toDict(cachedToken));
    }

    default boolean set(Dict ssoToken, int expires) {
        GXTokenConfigService tokenConfigService = GXSSOHelperUtil.bean(GXTokenConfigService.class);
        GXBaseCacheService cacheService = GXSSOHelperUtil.bean(GXBaseCacheService.class);
        if (tokenConfigService == null || cacheService == null) {
            LOG.warn("SSO cache service is unavailable, the token was not cached.");
            return false;
        }

        Long id = resolveUserId(ssoToken);
        if (id == null) {
            GXSSOMetrics.getInstance().incrementInvalidToken();
            LOG.warn("SSO token does not contain a valid user id, the token was not cached.");
            return false;
        }

        String tokenCacheKey = tokenConfigService.getTokenCacheKey(id, ssoToken);
        String cacheBucketName = tokenConfigService.getCacheBucketName();

        try {
            if (expires > 0) {
                cacheService.setCache(cacheBucketName, tokenCacheKey, JSONUtil.toJsonStr(ssoToken), expires, TimeUnit.SECONDS);
            } else {
                cacheService.setCache(cacheBucketName, tokenCacheKey, JSONUtil.toJsonStr(ssoToken));
            }
            refreshActiveTokens(cacheService, cacheBucketName);
            return true;
        } catch (Exception e) {
            LOG.warn("Set SSO token cache failed: bucket={}, key={}, error={}", cacheBucketName, tokenCacheKey, e.getMessage());
            return false;
        }
    }

    default boolean delete(Dict ssoToken) {
        GXTokenConfigService tokenConfigService = GXSSOHelperUtil.bean(GXTokenConfigService.class);
        GXBaseCacheService cacheService = GXSSOHelperUtil.bean(GXBaseCacheService.class);
        if (tokenConfigService == null || cacheService == null) {
            LOG.debug("SSO cache service is unavailable, nothing to delete.");
            return false;
        }

        Dict tmpToken = ssoToken;
        if (CollUtil.isEmpty(tmpToken)) {
            String tokenSecret = tokenConfigService.getTokenSecret();
            tmpToken = GXCurrentRequestContextUtils.getLoginCredentials(GXSSOProperties.getInstance().getTokenName(), tokenSecret);
            if (CollUtil.isEmpty(tmpToken)) {
                return false;
            }
        }

        Long id = resolveUserId(tmpToken);
        if (id == null) {
            GXSSOMetrics.getInstance().incrementInvalidToken();
            LOG.debug("SSO token does not contain a valid user id, nothing to delete.");
            return false;
        }

        String tokenCacheKey = tokenConfigService.getTokenCacheKey(id, tmpToken);
        String cacheBucketName = tokenConfigService.getCacheBucketName();

        try {
            cacheService.deleteCache(cacheBucketName, tokenCacheKey);
            refreshActiveTokens(cacheService, cacheBucketName);
            return true;
        } catch (Exception e) {
            LOG.warn("Delete SSO token cache failed: bucket={}, key={}, error={}", cacheBucketName, tokenCacheKey, e.getMessage());
            return false;
        }
    }

    default boolean verifyTokenConsistency(Dict cacheSSOToken, Dict cookieSSOToken) {
        if (cacheSSOToken == null || cookieSSOToken == null) {
            return false;
        }

        Long cookieLoginAt = Optional.ofNullable(cookieSSOToken.getLong(GXTokenConstant.LOGIN_AT_FIELD_NAME)).orElse(0L);
        Long cacheLoginAt = Optional.ofNullable(cacheSSOToken.getLong(GXTokenConstant.LOGIN_AT_FIELD_NAME)).orElse(1L);

        return cookieLoginAt.equals(cacheLoginAt);
    }

    private static void refreshActiveTokens(GXBaseCacheService cacheService, String bucketName) {
        try {
            Integer size = cacheService.size(bucketName);
            if (size != null) {
                GXSSOMetrics.getInstance().updateActiveTokens(size);
            }
        } catch (Exception e) {
            LOG.debug("event=sso.metrics.active-tokens.failed error={}", e.getMessage());
        }
    }

    private static void markState(GXSSOTokenStateEnums state) {
        HttpServletRequest request = GXCurrentRequestContextUtils.getHttpServletRequest();
        if (request != null) {
            request.setAttribute(GXSSOConstant.SSO_TOKEN_STATE, state.code());
        }
    }

    private static Long resolveUserId(Dict token) {
        if (CollUtil.isEmpty(token)) {
            return null;
        }
        Long id = Optional.ofNullable(token.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME)).orElse(0L);
        return id > 0 ? id : null;
    }

    private static Dict toDict(Object cachedToken) {
        if (cachedToken instanceof Dict dict) {
            return dict;
        }
        if (cachedToken instanceof String tokenJson) {
            if (CharSequenceUtil.isBlank(tokenJson)) {
                return Dict.create();
            }
            return JSONUtil.toBean(tokenJson, Dict.class);
        }
        return JSONUtil.toBean(JSONUtil.toJsonStr(cachedToken), Dict.class);
    }
}
