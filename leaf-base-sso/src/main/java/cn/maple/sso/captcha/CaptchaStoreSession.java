package cn.maple.sso.captcha;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.Serial;
import java.io.Serializable;

public class CaptchaStoreSession implements ICaptchaStore {
    private final HttpSession httpSession;

    private CaptchaStoreSession() {
        throw new UnsupportedOperationException("Use CaptchaStoreSession(HttpServletRequest).");
    }

    public CaptchaStoreSession(HttpServletRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("HttpServletRequest must not be null");
        }
        this.httpSession = request.getSession();
    }

    @Override
    public String get(String ticket) {
        if (isBlank(ticket)) {
            return null;
        }

        Object captchaObj = httpSession.getAttribute(ticket);
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
        httpSession.setAttribute(ticket, new CaptchaEntry(captcha, System.currentTimeMillis() + safeExpireSeconds * 1000L));
        return true;
    }

    @Override
    public void remove(String ticket) {
        if (!isBlank(ticket)) {
            httpSession.removeAttribute(ticket);
        }
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
