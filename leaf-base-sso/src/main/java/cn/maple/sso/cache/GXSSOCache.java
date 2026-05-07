package cn.maple.sso.cache;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXTokenInvalidException;
import cn.maple.core.framework.service.GXBaseCacheService;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.service.GXTokenConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public interface GXSSOCache {
    Logger LOG = LoggerFactory.getLogger(GXSSOCache.class);

    default Dict get(int expires, Dict requestToken) {
        if (GXCurrentRequestContextUtils.isRPC() || CollUtil.isEmpty(requestToken)) {
            return Dict.create();
        }

        GXTokenConfigService tokenConfigService = GXSpringContextUtils.getBean(GXTokenConfigService.class);
        GXBaseCacheService cacheService = GXSpringContextUtils.getBean(GXBaseCacheService.class);
        ensureRequiredServices(tokenConfigService, cacheService);

        if (!tokenConfigService.checkLoginStatus() || !tokenConfigService.verifyTokenEffectiveness()) {
            throw new GXTokenInvalidException("Token is invalid, please login again.", HttpStatus.HTTP_UNAUTHORIZED);
        }

        Long id = getValidUserId(requestToken);
        String tokenCacheKey = tokenConfigService.getTokenCacheKey(id, requestToken);
        String cacheBucketName = tokenConfigService.getCacheBucketName();
        Object cachedToken = cacheService.getCache(cacheBucketName, tokenCacheKey);
        if (Objects.isNull(cachedToken)) {
            return Dict.create();
        }

        return tokenConfigService.getEfficaciousToken(toDict(cachedToken));
    }

    default boolean set(Dict ssoToken, int expires) {
        GXBaseCacheService cacheService = GXSpringContextUtils.getBean(GXBaseCacheService.class);
        GXTokenConfigService tokenConfigService = GXSpringContextUtils.getBean(GXTokenConfigService.class);
        ensureRequiredServices(tokenConfigService, cacheService);

        Long id = getValidUserId(ssoToken);
        String tokenCacheKey = tokenConfigService.getTokenCacheKey(id, ssoToken);
        String cacheBucketName = tokenConfigService.getCacheBucketName();

        try {
            if (expires > 0) {
                cacheService.setCache(cacheBucketName, tokenCacheKey, JSONUtil.toJsonStr(ssoToken), expires, TimeUnit.SECONDS);
            } else {
                cacheService.setCache(cacheBucketName, tokenCacheKey, JSONUtil.toJsonStr(ssoToken));
            }
            return true;
        } catch (Exception e) {
            LOG.warn("Set SSO token cache failed: bucket={}, key={}, error={}", cacheBucketName, tokenCacheKey, e.getMessage());
            return false;
        }
    }

    default boolean delete(Dict ssoToken) {
        GXBaseCacheService cacheService = GXSpringContextUtils.getBean(GXBaseCacheService.class);
        GXTokenConfigService tokenConfigService = GXSpringContextUtils.getBean(GXTokenConfigService.class);
        ensureRequiredServices(tokenConfigService, cacheService);

        Dict tmpToken = ssoToken;
        if (CollUtil.isEmpty(tmpToken)) {
            String tokenSecret = tokenConfigService.getTokenSecret();
            tmpToken = GXCurrentRequestContextUtils.getLoginCredentials(GXSSOProperties.getInstance().getTokenName(), tokenSecret);
            if (CollUtil.isEmpty(tmpToken)) {
                return false;
            }
        }

        Long id = getValidUserId(tmpToken);
        String tokenCacheKey = tokenConfigService.getTokenCacheKey(id, tmpToken);
        String cacheBucketName = tokenConfigService.getCacheBucketName();

        try {
            Object result = cacheService.deleteCache(cacheBucketName, tokenCacheKey);
            return Objects.nonNull(result);
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

    private static void ensureRequiredServices(GXTokenConfigService tokenConfigService, GXBaseCacheService cacheService) {
        if (tokenConfigService == null || cacheService == null) {
            throw new GXBusinessException("SSO cache service or token config service is unavailable.");
        }
    }

    private static Long getValidUserId(Dict token) {
        Long id = Optional.ofNullable(token.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME)).orElse(0L);
        if (id <= 0) {
            throw new GXTokenInvalidException("Token does not contain a valid user id.");
        }
        return id;
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
