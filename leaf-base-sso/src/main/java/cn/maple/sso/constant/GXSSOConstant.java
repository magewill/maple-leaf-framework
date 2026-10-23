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

    /**
     * Request attribute carrying the {@link cn.maple.sso.enums.GXSSOTokenStateEnums} code.
     */
    public static final String SSO_TOKEN_STATE = "sso_token_state";

    public static final String SSO_COOKIE_MAX_AGE = "sso_cookie_max_age";

    public static final Charset CHARSET_ENCODING = Charset.forName(ENCODING);

    public static final String CUT_SYMBOL = "#";

    public static final Long TOKEN_TIMESTAMP_CUT = 1000L;

    /**
     * Order of the login interceptor in the SSO chain.
     *
     * <p>The login interceptor must run before the permission interceptor. Relying on bean
     * name dictionary order is fragile: adding any interceptor whose bean name sorts earlier
     * would silently change the security chain.</p>
     */
    public static final int AUTHORIZATION_ORDER = 100;

    /**
     * Order of the permission interceptor in the SSO chain.
     */
    public static final int PERMISSION_ORDER = 200;

    private GXSSOConstant() {
    }
}