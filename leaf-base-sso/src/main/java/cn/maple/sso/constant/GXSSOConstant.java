package cn.maple.sso.constant;

import cn.maple.core.framework.constant.GXCommonConstant;

import java.nio.charset.Charset;

/**
 * <p>
 * SSO 定义常量
 * </p>
 * 
 * <p>
 * 本类定义了单点登录(SSO)系统中使用的所有常量，包括：
 * 1. 编码常量 - 确保系统中字符编码的一致性
 * 2. Token字段常量 - 定义Token中各个字段的名称
 * 3. 会话属性常量 - 用于在请求和会话中存储SSO相关信息
 * 4. Cookie参数常量 - 控制SSO Cookie的行为
 * 5. 时间戳处理常量 - 用于Token时间戳的处理
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 获取Token中的用户IP
 * String userIp = tokenDict.getStr(GXSSOConstant.TOKEN_USER_IP);
 * 
 * // 2. 从请求中获取SSO Token属性
 * Object tokenAttr = request.getAttribute(GXSSOConstant.SSO_TOKEN_ATTR);
 * 
 * // 3. 设置Cookie的最大生存时间
 * request.setAttribute(GXSSOConstant.SSO_COOKIE_MAX_AGE, 3600); // 1小时
 * 
 * // 4. 处理时间戳
 * long currentTimeSeconds = System.currentTimeMillis() / GXSSOConstant.TOKEN_TIMESTAMP_CUT;
 * </pre>
 * </p>
 * 
 * <p>
 * 安全说明：
 * - 本类中的常量用于确保SSO系统的安全性和一致性
 * - 修改这些常量可能会影响系统的安全性和兼容性
 * - 建议在修改前充分了解其影响范围
 * </p>
 *
 * @author britton birtton@126.com
 * @since 2021-09-16
 */
public class GXSSOConstant {
    /**
     * 系统默认字符编码
     * <p>
     * 用于确保系统中所有字符串编码的一致性，特别是在处理Token加密解密、
     * HTTP请求响应和Cookie处理时使用。
     * </p>
     */
    public static final String ENCODING = "UTF-8";

    /**
     * Token中的IP字段名字
     * <p>
     * 用于在Token中存储用户的IP地址，可用于防止Token被盗用。
     * 系统可以比对当前请求的IP与Token中存储的IP是否一致，提高安全性。
     * </p>
     */
    public static final String TOKEN_USER_IP = "ip";

    /**
     * Token中的USER_AGENT字段名字
     * <p>
     * 用于在Token中存储用户的浏览器标识信息，结合IP地址可以更精确地
     * 识别用户环境，防止会话劫持和Token盗用。
     * </p>
     */
    public static final String TOKEN_USER_AGENT = "userAgent";

    /**
     * Token中的flag字段名字
     * <p>
     * 用于标识Token的类型或状态，例如是否为记住我功能创建的Token、
     * 是否为临时Token等。系统可以根据此标志采取不同的处理策略。
     * </p>
     */
    public static final String TOKEN_FLAG = "fg";

    /**
     * Token中的ORIGIN字段名字
     * <p>
     * 用于记录Token的来源信息，例如是从哪个系统或应用创建的，
     * 在多系统集成的场景下特别有用，可用于追踪用户的登录来源。
     * </p>
     */
    public static final String TOKEN_ORIGIN = "og";

    /**
     * Token中的租户ID名字
     * <p>
     * 用于在多租户系统中标识用户所属的租户，确保用户只能访问其所属
     * 租户的资源，是实现数据隔离的关键字段。
     * </p>
     */
    public static final String TOKEN_TENANT_ID = "tenantId";

    /**
     * Token的名字
     * <p>
     * 定义了存储在Cookie或请求头中的Token的键名，系统通过此名称
     * 获取Token值。修改此常量需同步修改前端和其他系统的配置。
     * </p>
     */
    public static final String ACCESS_SECRET = "accessSecret";

    /**
     * 拦截器判断后设置 Token至当前请求<br>
     * 减少Token解密次数： request.setAttribute("sso_token_attr", token)
     * <p>
     * 使用获取方式： GXSsoHelper.attrToken(request)
     * </p>
     */
    public static final String SSO_TOKEN_ATTR = GXCommonConstant.SSO_TOKEN_ATTR;

    /**
     * 踢出用户逻辑标记
     * <p>
     * 用于在请求属性中标记当前操作是踢出用户的操作，系统可以根据此标记
     * 执行特定的逻辑，如清除用户缓存、记录安全日志等。
     * </p>
     */
    public static final String SSO_KICK_FLAG = "sso_kick_flag";

    /**
     * 被踢出用户的标识
     * <p>
     * 存储被踢出用户的唯一标识，通常是用户ID，用于在日志中记录
     * 哪个用户被踢出，以及在缓存中清除该用户的会话信息。
     * </p>
     */
    public static final String SSO_KICK_USER = "sso_kick_user";

    /**
     * SSO 动态设置 Cookie 参数
     * <p>
     * -1 浏览器关闭时自动删除 0 立即删除 120 表示Cookie有效期2分钟(以秒为单位)
     * </p>
     */
    public static final String SSO_COOKIE_MAX_AGE = "sso_cookie_max_age";

    /**
     * Charset 类型编码格式
     * <p>
     * 基于ENCODING字符串创建的Charset对象，用于需要Charset类型参数的场景，
     * 如字符串编码转换、IO操作等，避免重复创建Charset对象提高性能。
     * </p>
     */
    public static final Charset CHARSET_ENCODING = Charset.forName(ENCODING);

    /**
     * 分隔符
     * <p>
     * 用于在复合字符串中分隔不同的部分，例如在缓存键名中分隔不同的标识部分，
     * 或在Token的某些字段中分隔多个值。选择#作为分隔符是因为它在URL和常见
     * 数据中出现频率较低。
     * </p>
     */
    public static final String CUT_SYMBOL = "#";

    /**
     * 时间戳的截断位所用除数
     * <p>
     * 用于Token中时间戳(例如iat,exp)和系统内System.currentTimeMillis()时间戳值的比对。
     * Token中的时间戳通常精确到秒级别，而System.currentTimeMillis()返回的是毫秒级时间戳，
     * 两者相差3位，因此需要将毫秒级时间戳除以1000转换为秒级。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 获取当前的秒级时间戳
     * long currentTimeSeconds = System.currentTimeMillis() / GXSSOConstant.TOKEN_TIMESTAMP_CUT;
     * 
     * // 检查Token是否过期
     * long expireTime = tokenDict.getLong("exp");
     * if (currentTimeSeconds > expireTime) {
     *     // Token已过期，执行相应处理
     * }
     * </pre>
     * </p>
     */
    public static final Long TOKEN_TIMESTAMP_CUT = 1000L;

    private GXSSOConstant() {
    }
}