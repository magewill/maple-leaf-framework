package cn.maple.sso.captcha;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public class CaptchaStoreSession implements ICaptchaStore {
    private static final int DEFAULT_EXPIRE_SECONDS = 60;

    private final HttpSession httpSession;

    private CaptchaStoreSession() {
        throw new UnsupportedOperationException("不允许无参构造，请使用带HttpServletRequest参数的构造方法");
    }

    public CaptchaStoreSession(HttpServletRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("HttpServletRequest不能为null");
        }
        this.httpSession = request.getSession();
    }

    @Override
    public String get(String ticket) {
        if (ticket == null || ticket.trim().isEmpty()) {
            return null;
        }
        Object captchaObj = httpSession.getAttribute(ticket);
        return captchaObj != null ? String.valueOf(captchaObj) : null;
    }

    @Override
    public boolean put(String ticket, String captcha) {
        if (ticket == null || ticket.trim().isEmpty() || captcha == null) {
            return false;
        }
        httpSession.setMaxInactiveInterval(DEFAULT_EXPIRE_SECONDS);
        httpSession.setAttribute(ticket, captcha);
        return true;
    }
}
