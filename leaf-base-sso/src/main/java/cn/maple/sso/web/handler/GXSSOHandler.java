package cn.maple.sso.web.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface GXSSOHandler {
    boolean preTokenIsNullAjax(HttpServletRequest request, HttpServletResponse response);

    boolean preTokenIsNull(HttpServletRequest request, HttpServletResponse response);
}
