package cn.maple.sso.service;

import cn.hutool.core.lang.Dict;

public interface GXUUserService {
    default Dict verifyUserToken(String token) {
        return Dict.create();
    }

    default String login(Dict loginParam) {
        return "";
    }

    default Dict getUserByUserId(Long userId) {
        return Dict.create();
    }

    default void loginOut() {
    }
}
