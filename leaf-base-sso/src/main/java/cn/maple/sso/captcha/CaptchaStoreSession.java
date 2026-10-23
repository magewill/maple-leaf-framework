package cn.maple.sso.captcha;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.Serial;
import java.io.Serializable;

/**
 * Session backed captcha store.
 *
 * <p>The HTTP session is resolved lazily: constructing the store never creates a session,
 * so a captcha endpoint that is called without a session does not mint one. Consumption is
 * serialized on the session so that a ticket can be used exactly once even when two requests
 * race.</p>
 */
public class CaptchaStoreSession implements ICaptchaStore {
    private final HttpServletRequest request;

    private HttpSession httpSession;

    private CaptchaStoreSession() {
        throw new UnsupportedOperationException("Use CaptchaStoreSession(HttpServletRequest).");
    }

    public CaptchaStoreSession(HttpServletRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("HttpServletRequest must not be null");
        }
        this.request = request;
    }

    @Override
    public String get(String ticket) {
        if (isBlank(ticket)) {
            return null;
        }

        HttpSession session = session(false);
        if (session == null) {
            return null;
        }

        Object captchaObj;
        try {
            captchaObj = session.getAttribute(ticket);
        } catch (IllegalStateException e) {
            return null;
        }
        if (!(captchaObj instanceof CaptchaEntry entry)) {
            return captchaObj != null ? String.valueOf(captchaObj) : null;
        }

        if (entry.isExpired()) {
            remove(ticket);
            return null;
        }
        return entry.value();
    }

    @Override
    public boolean put(String ticket, String captcha) {
        return put(ticket, captcha, DEFAULT_EXPIRE_SECONDS);
    }

    @Override
    public boolean put(String ticket, String captcha, int expireSeconds) {
        if (isBlank(ticket) || captcha == null) {
            return false;
        }

        int safeExpireSeconds = expireSeconds > 0 ? expireSeconds : DEFAULT_EXPIRE_SECONDS;
        HttpSession session = session(true);
        if (session == null) {
            return false;
        }
        session.setAttribute(ticket, new CaptchaEntry(captcha, System.currentTimeMillis() + safeExpireSeconds * 1000L));
        return true;
    }

    @Override
    public void remove(String ticket) {
        if (isBlank(ticket)) {
            return;
        }
        HttpSession session = session(false);
        if (session == null) {
            return;
        }
        try {
            session.removeAttribute(ticket);
        } catch (IllegalStateException e) {
            // session invalidated concurrently, nothing left to remove
        }
    }

    @Override
    public String consume(String ticket) {
        HttpSession session = session(false);
        if (session == null) {
            return null;
        }
        synchronized (session) {
            return ICaptchaStore.super.consume(ticket);
        }
    }

    private HttpSession session(boolean create) {
        if (httpSession != null) {
            return httpSession;
        }
        try {
            httpSession = create ? request.getSession(true) : request.getSession(false);
        } catch (IllegalStateException e) {
            return null;
        }
        return httpSession;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record CaptchaEntry(String value, long expiresAtMillis) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }
}
