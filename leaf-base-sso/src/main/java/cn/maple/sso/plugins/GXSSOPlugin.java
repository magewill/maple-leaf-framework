package cn.maple.sso.plugins;

import cn.hutool.core.lang.Dict;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface GXSSOPlugin {
    default boolean login(HttpServletRequest request, HttpServletResponse response) {
        return true;
    }

    default boolean validateToken(Dict ssoToken) {
        return true;
    }

    default boolean logout(HttpServletRequest request, HttpServletResponse response) {
        return true;
    }
}
