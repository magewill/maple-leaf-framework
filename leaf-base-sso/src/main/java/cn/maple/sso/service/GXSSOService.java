package cn.maple.sso.service;

import cn.hutool.core.lang.Dict;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

public interface GXSSOService {
    Dict getSSOToken(HttpServletRequest request);

    boolean kickLogin(@Nullable Object userId);

    void setCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken);

    boolean clearLogin(HttpServletRequest request, HttpServletResponse response);

    void clearRedirectLogin(HttpServletRequest request, HttpServletResponse response) throws IOException;

    /**
     * Read the SSO token already stored on the request.
     *
     * @param request current request
     * @return the token, never {@code null} (empty when absent)
     */
    default Dict attrSSOToken(HttpServletRequest request) {
        return Dict.create();
    }

    /**
     * Clear the login state and redirect to the configured logout URL.
     *
     * @param request current request
     * @param response current response
     * @throws IOException when the redirect or the response writer fails
     */
    default void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        throw new UnsupportedOperationException("logout must be implemented by the SSO service.");
    }
}
