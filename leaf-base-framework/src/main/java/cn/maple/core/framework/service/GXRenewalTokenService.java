package cn.maple.core.framework.service;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXBusinessException;

import java.util.Date;

public interface GXRenewalTokenService {
    default boolean renewalToken() {
        throw new GXBusinessException("请自定义实现Token无感刷新的逻辑");
    }

    default String refreshToken(Dict extraData) {
        throw new GXBusinessException("请自定义实现Token无感刷新的逻辑");
    }

    default Date getTokenExpireTime(String token) {
        throw new GXBusinessException("请自定义实现获取Token过期时间的逻辑");
    }

    default boolean validateToken(String token) {
        throw new GXBusinessException("请自定义实现Token验证的逻辑");
    }

    default boolean invalidateToken(String token) {
        throw new GXBusinessException("请自定义实现使Token失效的逻辑");
    }
}
