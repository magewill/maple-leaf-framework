package cn.maple.sso.captcha;

public interface ICaptchaStore {
    int DEFAULT_EXPIRE_SECONDS = 60;

    String get(String ticket);

    boolean put(String ticket, String captcha);

    default boolean put(String ticket, String captcha, int expireSeconds) {
        return put(ticket, captcha);
    }

    default void remove(String ticket) {
    }

    default String consume(String ticket) {
        String captcha = get(ticket);
        if (captcha != null) {
            remove(ticket);
        }
        return captcha;
    }
}
