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

    /**
     * Whether the current secret is still the framework development default.
     *
     * <p>Production deployments must override {@link #getTokenSecret()}; the startup
     * validation of {@code GXAbstractSSOService} refuses to run in a production profile
     * when this method returns {@code true}.</p>
     *
     * @return {@code true} when the default secret is in use
     */
    default boolean isDefaultTokenSecret() {
        return CharSequenceUtil.equals(GXTokenConstant.TOKEN_SECRET_KEY, getTokenSecret());
    }

    default String getTokenCachePrefix() {
        return "ssoTokenKey_";
    }

    default String getTokenCacheKey(Long userId, Dict extraData) {
        if (extraData == null) {
            return getTokenCachePrefix() + userId;
        }
        String platform = extraData.getStr(GXTokenConstant.PLATFORM);
        if (CharSequenceUtil.isBlank(platform)) {
            return getTokenCachePrefix() + userId;
        }
        return getTokenCachePrefix() + userId + ":" + platform;
    }

    default Dict getEfficaciousToken(Dict requestToken) {
        return requestToken;
    }

    default boolean verifyTokenEffectiveness() {
        return true;
    }
}
