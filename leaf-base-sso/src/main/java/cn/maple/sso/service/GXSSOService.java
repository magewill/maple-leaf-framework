package cn.maple.sso.service;

import cn.hutool.core.lang.Dict;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public interface GXSSOService {
    Dict getSSOToken(HttpServletRequest request);

    boolean kickLogin(Object userId);

    void setCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken);

    boolean clearLogin(HttpServletRequest request, HttpServletResponse response);

    void clearRedirectLogin(HttpServletRequest request, HttpServletResponse response) throws IOException;
}