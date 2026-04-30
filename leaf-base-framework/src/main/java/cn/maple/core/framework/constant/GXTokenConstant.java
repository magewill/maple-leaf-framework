package cn.maple.core.framework.constant;

public class GXTokenConstant {
    public static final String TOKEN_NAME = "token";

    public static final Integer TOKEN_NEVER_EXPIRE = 0;

    public static final int USER_EXPIRE = 24 * 60 * 60 * 15;

    public static final int USER_EXPIRE_REFRESH_THRESHOLD = 24 * 60 * 60 * 3;

    public static final int USER_TOKEN_RENEWAL = 24 * 60 * 60 * 7;

    public static final int WEB_CLIENT_TOKEN_EXPIRE = 5 * 30;

    public static final String USER_CACHE_BUCKET_NAME = "user-cache-bucket";

    public static final String USER_TOKEN_NAME = "token";

    public static final String TOKEN_USER_ID_FIELD_NAME = "userId";

    public static final String TOKEN_USER_NAME_FIELD_NAME = "userName";

    public static final String MANAGER_CACHE_BUCKET_NAME = "admin-cache-bucket";

    public static final String USER_TOKEN_SECRET_KEY = "6A3EDD4768E3669B5";

    public static final String ADMIN_TOKEN_SECRET_KEY = "C0180A90D3931D";

    public static final String TOKEN_SECRET_KEY = "31D0D393D3EAF5";

    public static final int ADMIN_EXPIRE_REFRESH_THRESHOLD = 24 * 60;

    public static final int ADMIN_TOKEN_RENEWAL = 12 * 60 * 60;

    public static final String LOGIN_AT_FIELD_NAME = "loginAt";

    public static final String PLATFORM = "platform";

    public static final String FROM_PLATFORM = "from_platform";

    private GXTokenConstant() {
    }
}
