package cn.maple.sso.service;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.util.GXCommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public interface GXTokenConfigService {
    Logger LOG = LoggerFactory.getLogger(GXTokenConfigService.class);

    AtomicBoolean DEFAULT_TOKEN_SECRET_WARNED = new AtomicBoolean();

    default String getTokenSecret() {
        if (DEFAULT_TOKEN_SECRET_WARNED.compareAndSet(false, true)) {
            LOG.warn("Using default SSO token secret. Override GXTokenConfigService#getTokenSecret for production.");
        }
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
        return getTokenCachePrefix() + userId;
    }

    default Dict getEfficaciousToken(Dict requestToken) {
        return requestToken;
    }

    default boolean verifyTokenEffectiveness() {
        return true;
    }
}
