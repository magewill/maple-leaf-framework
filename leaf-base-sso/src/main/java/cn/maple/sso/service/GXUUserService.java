package cn.maple.sso.service;

import cn.hutool.core.lang.Dict;

public interface GXUUserService {
    default Dict verifyUserToken(String token) {
        throw new UnsupportedOperationException("verifyUserToken must be implemented by the application.");
    }

    default String login(Dict loginParam) {
        throw new UnsupportedOperationException("login must be implemented by the application.");
    }

    default Dict getUserByUserId(Long userId) {
        throw new UnsupportedOperationException("getUserByUserId must be implemented by the application.");
    }

    default void loginOut() {
        throw new UnsupportedOperationException("loginOut must be implemented by the application.");
    }
}
