package cn.maple.core.framework.constant;

/**
 * 框架通用常量类
 * <p>
 * 该类定义了框架中使用的各种常量，包括分页参数、环境标识、加密密钥等。
 * 这些常量在整个应用程序中被广泛使用，集中定义有助于统一管理和维护。
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 使用分页常量
 * int currentPage = GXCommonConstant.DEFAULT_CURRENT_PAGE;
 * int pageSize = GXCommonConstant.DEFAULT_PAGE_SIZE;
 *
 * // 2. 判断运行环境
 * String env = getEnvironment();
 * boolean isProd = GXCommonConstant.RUN_ENV_PROD.equals(env);
 *
 * // 3. 获取敏感信息加密密钥
 * String secretKey = System.getProperty(GXCommonConstant.DATA_SOURCE_SECRET_KEY);
 * </pre>
 * </p>
 *
 * <p>
 * 常量分类：
 * 1. 分页相关常量 - 默认分页参数和最大分页限制
 * 2. 环境标识常量 - 区分不同的运行环境（开发、测试、生产等）
 * 3. 安全相关常量 - 加密密钥和敏感信息处理
 * 4. 系统配置常量 - 部署环境标识和请求头
 * </p>
 *
 * @author maple
 */
public class GXCommonConstant {
    /**
     * 默认当前分页
     */
    public static final int DEFAULT_CURRENT_PAGE = 1;

    /**
     * 默认每页的大小
     */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 默认分页的最大值
     */
    public static final int DEFAULT_MAX_PAGE_SIZE = 10000;

    /**
     * 验证数字的正则表达式
     */
    public static final String DIGITAL_REGULAR_EXPRESSION = "^(([1-9]+\\.[0-9]*[1-9][0-9]*)|([0-9]*[1-9][0-9]*\\.[0-9]+)|([0-9]*[1-9][0-9]*))$";

    /**
     * 手机号码加密的KEY
     */
    public static final String COMMON_ENCRYPT_KEY = "B78D32BTR1CHEN15AC1F19C46A9B533986";

    /**
     * 日志格式
     */
    public static final String SHORT_LOGGER_FORMAT = "线程 : {} --> 描述信息 : {}";

    /**
     * 服务运行的环境
     * 个人开发环境
     */
    public static final String RUN_ENV_LOCAL = "local";

    /**
     * 服务运行的环境
     * 开发模式
     */
    public static final String RUN_ENV_DEV = "dev";

    /**
     * 服务运行的环境
     * 测试模式
     */
    public static final String RUN_ENV_TEST = "test";

    /**
     * 服务运行的环境
     * 预发布模式
     */
    public static final String RUN_ENV_PRE = "pre";

    /**
     * 服务运行的环境
     * 生产模式
     */
    public static final String RUN_ENV_PROD = "prod";

    /**
     * 服务敏感信息的加解密KEY的名字,需要通过
     * {@code -Dmaple.ds.secret=britton}
     * 虚拟机参数指定
     * 1、数据库
     * 2、redis
     * 3、MongoDB
     */
    public static final String DATA_SOURCE_SECRET_KEY = "maple.ds.secret";

    /**
     * 系统(环境)变量里面配置的服务敏感信息的加解密KEY的名字
     * {@code
     * Linux : export JAVA_BIZ_SECRET_KEY = "aaa-bbb";
     * Windows : 在环境变量里面设置
     * }
     */
    public static final String DATA_SOURCE_SECRET_KEY_ENV = "DS_SECRET_KEY";

    /**
     * 默认的自定义处理函数名字
     */
    public static final String DEFAULT_CUSTOMER_PROCESS_METHOD_NAME = "customizeProcess";

    /**
     * 部署环境的环境变量标识头名字
     * 该字段头主要是为了限制部署环境的一致性
     */
    public static final String DEPLOY_ENV_HEADER_NAME = "DEPLOY_ENV";

    /**
     * 部署环境的代理标识头名字 ，eg : NGINX  proxy_set_header  DEPLOY_REQUEST_ENV  "prod";
     * 该字段头主要是为了限制部署环境的一致性
     */
    public static final String DEPLOY_REQUEST_ENV_HEADER_NAME = "DEPLOY_REQUEST_ENV";

    /**
     * 拦截器判断后设置 Token至当前请求<br>
     * 减少Token解密次数： request.setAttribute("sso_token_attr", token)
     * <p>
     * 使用获取方式： GXSsoHelper.attrToken(request)
     * </p>
     */
    public static final String SSO_TOKEN_ATTR = "sso_token_attr";

    /**
     * 根据条件在数据库查询出来的记录不存在
     */
    public static final int DB_RECORD_NOT_FOUND = -1;

    /**
     * 使用WebClient传递的认证Token
     */
    public static final String WEB_CLIENT_AUTH_TOKEN = "Web-Client-Auth-Token";

    private GXCommonConstant() {
    }
}
