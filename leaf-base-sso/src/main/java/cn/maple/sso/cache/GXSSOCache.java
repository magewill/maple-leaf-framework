package cn.maple.sso.cache;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public interface GXSSOCache {
    default Dict get(int expires, Dict requestToken) {
        if (GXCurrentRequestContextUtils.isRPC()) {
            return Dict.create();
        }
        if (CollUtil.isEmpty(requestToken)) {
            return Dict.create();
        }
        GXTokenConfigService tokenConfigService = GXSpringContextUtils.getBean(GXTokenConfigService.class);
        if (tokenConfigService == null) {
            throw new GXBusinessException("未找到TokenConfigService实现类");
        }
        boolean isValid = tokenConfigService.checkLoginStatus();
        if (!isValid) {
            throw new GXTokenInvalidException("token已经失效,请重新登录!", HttpStatus.HTTP_UNAUTHORIZED);
        }
        return tokenConfigService.getEfficaciousToken(requestToken);
    }

    @SuppressWarnings("all")
    default boolean set(Dict ssoToken, int expires) {
        GXBaseCacheService cacheService = GXSpringContextUtils.getBean(GXBaseCacheService.class);
        GXTokenConfigService tokenConfigService = GXSpringContextUtils.getBean(GXTokenConfigService.class);

        if (tokenConfigService == null || cacheService == null) {
            throw new GXBusinessException("缓存服务或Token配置服务不可用");
        }

        Long id = Optional.ofNullable(ssoToken.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME)).orElse(0L);
        if (Objects.isNull(id) || id <= 0) {
            throw new GXTokenInvalidException("token中未包含有效的id标识");
        }

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
            return false;
        }
    }

    @SuppressWarnings("all")
    default boolean delete(Dict ssoToken) {
        try {
            GXBaseCacheService cacheService = GXSpringContextUtils.getBean(GXBaseCacheService.class);
            GXTokenConfigService tokenConfigService = GXSpringContextUtils.getBean(GXTokenConfigService.class);

            if (tokenConfigService == null || cacheService == null) {
                throw new GXBusinessException("缓存服务或Token配置服务不可用");
            }

            Dict tmpToken = ssoToken;
            if (CollUtil.isEmpty(tmpToken)) {
                String tokenSecret = tokenConfigService.getTokenSecret();
                tmpToken = GXCurrentRequestContextUtils.getLoginCredentials(GXSSOProperties.getInstance().getTokenName(), tokenSecret);
                if (CollUtil.isEmpty(tmpToken)) {
                    return false;
                }
            }

            Long id = Optional.ofNullable(tmpToken.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME)).orElse(0L);
            if (Objects.isNull(id) || id <= 0) {
                throw new GXTokenInvalidException("token中未包含有效的id标识");
            }

            String tokenCacheKey = tokenConfigService.getTokenCacheKey(id, tmpToken);
            String cacheBucketName = tokenConfigService.getCacheBucketName();
            Object result = cacheService.deleteCache(cacheBucketName, tokenCacheKey);

            return Objects.nonNull(result);
        } catch (GXTokenInvalidException e) {
            throw e;
        } catch (Exception e) {
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
}
