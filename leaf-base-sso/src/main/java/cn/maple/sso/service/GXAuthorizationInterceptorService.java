package cn.maple.sso.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface GXAuthorizationInterceptorService {
    boolean interceptor(HttpServletRequest request, HttpServletResponse response);
}
