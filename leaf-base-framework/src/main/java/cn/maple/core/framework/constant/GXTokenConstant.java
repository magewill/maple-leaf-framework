package cn.maple.core.framework.constant;

/**
 * Token相关常量定义类
 * <p>
 * 本类定义了系统中所有与Token处理相关的常量，包括Token名称、过期时间、刷新阈值、密钥等。
 * 这些常量用于实现安全的用户认证和授权机制，支持前端用户和管理端用户的不同处理策略。
 * </p>
 *
 * <p>
 * 安全说明：
 * 1. 不同类型的用户（前端用户、管理员）使用不同的密钥，增强安全隔离
 * 2. 实现了Token自动续期机制，当Token接近过期时自动刷新，提升用户体验
 * 3. 使用缓存桶存储Token信息，支持分布式环境下的会话管理
 * 4. 所有密钥都应妥善保管，避免泄露
 * 5. Token中可存储用户ID、用户名等关键信息，便于身份识别和权限控制
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 创建用户Token
 * String userId = "10001";
 * String userName = "张三";
 *
 * // 构建Token数据
 * Map<String, Object> tokenData = new HashMap<>();
 * tokenData.put(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, userId);
 * tokenData.put(GXTokenConstant.TOKEN_USER_NAME_FIELD_NAME, userName);
 * tokenData.put(GXTokenConstant.LOGIN_AT_FIELD_NAME, System.currentTimeMillis());
 *
 * // 生成Token
 * String token = JWTUtils.generateToken(tokenData, GXTokenConstant.USER_TOKEN_SECRET_KEY, GXTokenConstant.USER_EXPIRE);
 *
 * // 验证Token并获取用户ID
 * Claims claims = JWTUtils.parseToken(token, GXTokenConstant.USER_TOKEN_SECRET_KEY);
 * String verifiedUserId = claims.get(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, String.class);
 * </pre>
 * </p>
 */
public class GXTokenConstant {
    /**
     * token的名字
     * <p>
     * 在HTTP请求头中用于传递Token的字段名
     * </p>
     */
    public static final String TOKEN_NAME = "token";

    /**
     * Token永不过期标识
     * <p>
     * 当Token的过期时间设置为此值时，表示Token永不过期
     * 注意：永不过期的Token存在安全风险，应谨慎使用
     * </p>
     */
    public static final Integer TOKEN_NEVER_EXPIRE = 0;

    /**
     * 用户端TOKEN过期时间（秒）
     * <p>
     * 默认为15天，即用户登录后15天内无需重新登录
     * </p>
     */
    public static final int USER_EXPIRE = 24 * 60 * 60 * 15;

    /**
     * 用户端TOKEN即将过期刷新阈值（秒）
     * <p>
     * 当用户的token过期时间小于该值时（默认3天），系统会自动刷新token的过期时间
     * 这种机制可以在不打断用户体验的情况下延长会话有效期
     * </p>
     */
    public static final int USER_EXPIRE_REFRESH_THRESHOLD = 24 * 60 * 60 * 3;

    /**
     * 用户端的token续期时间（秒）
     * <p>
     * 当用户的token小于USER_EXPIRE_REFRESH_THRESHOLD时，系统会将token过期时间延长至当前时间加上此值（默认7天）
     * </p>
     */
    public static final int USER_TOKEN_RENEWAL = 24 * 60 * 60 * 7;

    /**
     * web客户端token过期时间（秒）
     * <p>
     * 默认为150秒（5 * 30），用于WebClient调用时的认证Token有效期。
     * 此值应根据系统安全需求和性能考虑进行设置：
     * - 值过小会导致频繁生成Token，增加系统负担
     * - 值过大会增加Token被盗用的风险
     * </p>
     * <p>
     * 在实现GXWebClientService时，建议提前60秒左右刷新Token，
     * 以确保系统运行过程中Token的连续有效性。
     * </p>
     */
    public static final int WEB_CLIENT_TOKEN_EXPIRE = 5 * 30;

    /**
     * 用户端缓存桶的名字(redisson)
     * <p>
     * 用于在分布式环境中存储用户Token信息的缓存桶名称
     * </p>
     */
    public static final String USER_CACHE_BUCKET_NAME = "user-cache-bucket";

    /**
     * 用户token的名字
     * <p>
     * 在HTTP请求头中用于传递用户Token的字段名
     * </p>
     */
    public static final String USER_TOKEN_NAME = "token";

    /**
     * 用户ID字段名
     * <p>
     * 在Token中存储用户ID的字段名
     * </p>
     */
    public static final String TOKEN_USER_ID_FIELD_NAME = "userId";

    /**
     * 用户名字段名
     * <p>
     * 在Token中存储用户名的字段名
     * </p>
     */
    public static final String TOKEN_USER_NAME_FIELD_NAME = "userName";

    /**
     * 管理端缓存桶的名字(redisson)
     * <p>
     * 用于在分布式环境中存储管理员Token信息的缓存桶名称
     * </p>
     */
    public static final String MANAGER_CACHE_BUCKET_NAME = "admin-cache-bucket";

    /**
     * C端(用户端)客户TOKEN加密的KEY
     * <p>
     * 用于加密和验证用户端Token的密钥
     * 注意：此密钥应妥善保管，避免泄露
     * </p>
     */
    public static final String USER_TOKEN_SECRET_KEY = "6A3EDD4768E3669B5";

    /**
     * 管理端TOKEN加密的KEY
     * <p>
     * 用于加密和验证管理端Token的密钥
     * 注意：此密钥应妥善保管，避免泄露
     * </p>
     */
    public static final String ADMIN_TOKEN_SECRET_KEY = "C0180A90D3931D";

    /**
     * 通用的TOKEN加密的KEY
     * <p>
     * 用于加密和验证通用Token的密钥
     * 注意：此密钥应妥善保管，避免泄露
     * </p>
     */
    public static final String TOKEN_SECRET_KEY = "31D0D393D3EAF5";

    /**
     * 管理端TOKEN即将过期刷新阈值（秒）
     * <p>
     * 当管理员的token过期时间小于该值时（默认24小时），系统会自动刷新token的过期时间
     * 管理端的刷新阈值通常小于用户端，以增强安全性
     * </p>
     */
    public static final int ADMIN_EXPIRE_REFRESH_THRESHOLD = 24 * 60;

    /**
     * 管理端的token续期时间（秒）
     * <p>
     * 当管理员的token小于ADMIN_EXPIRE_REFRESH_THRESHOLD时，系统会将token过期时间延长至当前时间加上此值（默认12小时）
     * 管理端的续期时间通常小于用户端，以增强安全性
     * </p>
     */
    public static final int ADMIN_TOKEN_RENEWAL = 12 * 60 * 60;

    /**
     * 登录时间字段名
     * <p>
     * 在Token中存储用户登录时间的字段名
     * 可用于分析用户行为和安全审计
     * </p>
     */
    public static final String LOGIN_AT_FIELD_NAME = "loginAt";

    /**
     * 平台标识字段名
     * <p>
     * 在Token中存储用户登录平台的字段名
     * 例如：web、android、ios等
     * </p>
     */
    public static final String PLATFORM = "platform";

    /**
     * 来源平台字段名
     * <p>
     * 在跨服务调用时，用于标识Token的来源平台
     * 例如：从订单服务到商品服务的调用
     * 主要用于token在不同平台生成时，解密token的密钥不统一的场景
     * </p>
     */
    public static final String FROM_PLATFORM = "from_platform";

    /**
     * 私有构造函数，防止实例化
     */
    private GXTokenConstant() {
    }
}
