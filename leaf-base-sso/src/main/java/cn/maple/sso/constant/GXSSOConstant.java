package cn.maple.sso.constant;

import cn.maple.core.framework.constant.GXCommonConstant;

import java.nio.charset.Charset;

public class GXSSOConstant {
    public static final String ENCODING = "UTF-8";

    public static final String TOKEN_USER_IP = "ip";

    public static final String TOKEN_USER_AGENT = "userAgent";

    public static final String TOKEN_FLAG = "fg";

    public static final String TOKEN_ORIGIN = "og";

    public static final String TOKEN_TENANT_ID = "tenantId";

    public static final String ACCESS_SECRET = "accessSecret";

    public static final String SSO_TOKEN_ATTR = GXCommonConstant.SSO_TOKEN_ATTR;

    public static final String SSO_KICK_FLAG = "sso_kick_flag";

    public static final String SSO_KICK_USER = "sso_kick_user";

    public static final String SSO_COOKIE_MAX_AGE = "sso_cookie_max_age";

    public static final Charset CHARSET_ENCODING = Charset.forName(ENCODING);

    public static final String CUT_SYMBOL = "#";

    public static final Long TOKEN_TIMESTAMP_CUT = 1000L;

    private GXSSOConstant() {
    }
}