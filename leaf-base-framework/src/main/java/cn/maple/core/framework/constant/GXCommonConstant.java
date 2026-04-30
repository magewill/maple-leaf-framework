package cn.maple.core.framework.constant;

public class GXCommonConstant {
    public static final int DEFAULT_CURRENT_PAGE = 1;

    public static final int DEFAULT_PAGE_SIZE = 20;

    public static final int DEFAULT_MAX_PAGE_SIZE = 10000;

    public static final String DIGITAL_REGULAR_EXPRESSION = "^(([1-9]+\\.[0-9]*[1-9][0-9]*)|([0-9]*[1-9][0-9]*\\.[0-9]+)|([0-9]*[1-9][0-9]*))$";

    public static final String COMMON_ENCRYPT_KEY = "B78D32BTR1CHEN15AC1F19C46A9B533986";

    public static final String SHORT_LOGGER_FORMAT = "线程 : {} --> 描述信息 : {}";

    public static final String RUN_ENV_LOCAL = "local";

    public static final String RUN_ENV_DEV = "dev";

    public static final String RUN_ENV_TEST = "test";

    public static final String RUN_ENV_PRE = "pre";

    public static final String RUN_ENV_PROD = "prod";

    public static final String DATA_SOURCE_SECRET_KEY = "maple.ds.secret";

    public static final String DATA_SOURCE_SECRET_KEY_ENV = "DS_SECRET_KEY";

    public static final String DEFAULT_CUSTOMER_PROCESS_METHOD_NAME = "customizeProcess";

    public static final String DEPLOY_ENV_HEADER_NAME = "DEPLOY_ENV";

    public static final String DEPLOY_REQUEST_ENV_HEADER_NAME = "DEPLOY_REQUEST_ENV";

    public static final String SSO_TOKEN_ATTR = "sso_token_attr";

    public static final int DB_RECORD_NOT_FOUND = -1;

    public static final String X_AUTH_TOKEN = "X-Auth-Token";

    public static final String X_HMAC_SIGNATURE = "X-HMAC-Signature";

    private GXCommonConstant() {
    }
}
