package cn.maple.sso.captcha;

public interface ICaptchaStore {
    String get(String ticket);

    boolean put(String ticket, String captcha);
}
