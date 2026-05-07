package cn.maple.sso.service;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;

public interface GXTokenConfigService {
    default String getTokenSecret() {
        return GXTokenConstant.TOKEN_SECRET_KEY;
    }

    default String getCacheBucketName() {
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "");
        if (CharSequenceUtil.isNotBlank(appName)) {
            return CharSequenceUtil.format("token_bucket:{}", appName);
        }
        return "token_bucket";
    }

    default boolean checkLoginStatus() {
        return true;
    }

    default String getTokenCachePrefix() {
        return "ssoTokenKey_";
    }

    default String getTokenCacheKey(Long userId, Dict extraData) {
        throw new GXBusinessException("请实现缓存的cache键方法!");
    }

    default Dict getEfficaciousToken(Dict requestToken) {
        return requestToken;
    }
    
    default boolean verifyTokenEffectiveness() {
        return Boolean.TRUE;
    }
}
