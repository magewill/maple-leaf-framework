package cn.maple.sso.properties;

import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.oauth.GXSSOAuthorization;
import cn.maple.sso.plugins.GXSSOPlugin;
import cn.maple.sso.utils.GXSSOHelperUtil;
import org.jspecify.annotations.Nullable;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Objects;

/**
 * <p>
 * SSO 配置文件解析
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
@Data
@Accessors(chain = true)
public class GXSSOProperties {
    /**
     * Default absolute token time-to-live in seconds (2 hours).
     *
     * <p>Before v4.3 this default was {@code -1}, which disabled both the signature
     * expiry and the cache TTL. Explicitly setting {@code -1} still reproduces the old
     * behaviour but is logged as an error.</p>
     */
    public static final int DEFAULT_CACHE_EXPIRES = 7200;

    /**
     * Maximum token TTL used when the cache is unavailable and the fallback is enabled.
     */
    public static final int MAX_FALLBACK_TTL_SECONDS = 900;

    /**
     * 编码格式，默认 UTF-8
     */
    private String encoding = GXSSOConstant.ENCODING;

    /**
     * 签名密钥（用于对此算法）
     */
    private @Nullable String signKey;

    /**
     * 签名算法
     */
    private String signAlgorithm = "HS512";

    /**
     * RSA 私钥 key.jks 存储路径
     */
    private String rsaJksStore = "key.jks";

    /**
     * RSA 公钥 public.cert 存储路径
     */
    private String rsaCertStore = "public.cert";

    /**
     * RSA 密钥 Alias
     */
    private String rsaAlias = "jwtkey";

    /**
     * RSA 密钥 keypass（保留字段，当前实现未使用；不得写入真实口令）
     */
    private @Nullable String rsaKeypass;

    /**
     * RSA 密钥 storepass（保留字段，当前实现未使用；不得写入真实口令）
     */
    private @Nullable String rsaStorepass;

    /**
     * 访问票据名
     */
    private String tokenName = "token";

    /**
     * cookie 名称
     */
    private String cookieName = "uid";

    /**
     * cookie 所在有效域名，不设置为当前访问域名
     */
    private @Nullable String cookieDomain;

    /**
     * cookie 路径
     */
    private String cookiePath = "/";

    /**
     * cookie 是否设置安全，设置 true 那么只能为 https 协议访问
     */
    private boolean cookieSecure = false;

    /**
     * cookie 是否为只读状态，设置 js 无法获取
     */
    private boolean cookieHttpOnly = true;

    /**
     * Cookie SameSite attribute.
     */
    private String cookieSameSite = "Lax";

    /**
     * cookie 有效期 -1 关闭浏览器失效
     */
    private int cookieMaxAge = -1;

    /**
     * 是否验证 cookie 设置时浏览器信息
     */
    private boolean cookieBrowser = false;

    /**
     * 是否验证 cookie 设置时 IP 信息
     */
    private boolean cookieCheckIp = false;

    /**
     * Whether to trust proxy forwarded IP headers.
     */
    private boolean trustForwardedIpHeaders = false;

    /**
     * 登录地址
     */
    private String loginUrl = "";

    /**
     * 退出地址
     */
    private String logoutUrl = "";

    /**
     * 登录成功回调地址
     */
    private String paramReturnUrl = "ReturnURL";

    /**
     * Token 绝对有效期（秒），同时作为服务端缓存 TTL。
     *
     * <p>{@code -1} 表示永不过期，仅用于兼容历史配置，启动校验会打 ERROR 日志。</p>
     */
    private int cacheExpires = DEFAULT_CACHE_EXPIRES;

    /**
     * Configuration source: {@code nacos} (default) or {@code local}.
     */
    private String source = "nacos";

    /**
     * Whether a local only token is allowed when no {@code GXSSOCache} bean is available.
     */
    private boolean cacheFallbackEnabled = false;

    /**
     * Maximum entries of the handler method annotation cache.
     */
    private int capacity = 4096;

    /**
     * 权限认证（默认 false）
     */
    private boolean permissionUri = false;

    /**
     * 无权限注解时是否放行（默认 true，与历史行为一致）。
     *
     * <p>生产环境建议显式配置为 {@code false}（白名单模式）；启用 {@code permission-uri} 后
     * URI 授权自身决断，不再回落到该开关。</p>
     */
    private boolean nothingAnnotationPass = true;

    /**
     * 无权限时的跳转地址；为空则返回 403。
     */
    private @Nullable String illegalUrl;

    /**
     * 插件列表
     */
    private @Nullable List<GXSSOPlugin> pluginList;

    /**
     * SSO 缓存
     */
    private @Nullable GXSSOCache cache;

    /**
     * SSO 权限授权
     */
    private @Nullable GXSSOAuthorization authorization;

    public GXSSOProperties() {
        /* 支持 setInstance 设置初始化 */
    }

    /**
     * new 当前对象
     */
    public static GXSSOProperties getInstance() {
        return GXSSOHelperUtil.getSSOConfig();
    }

    public static String getSsoEncoding() {
        return getInstance().getEncoding();
    }

    /**
     * <p>
     * 生成 Token 缓存主键
     * </p>
     *
     * @param userId 用户ID
     * @return String
     */
    public static String toCacheKey(Object userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return "ssoTokenKey_" + userId;
    }

    /**
     * 签名密钥（保留字段，当前实现未读取；缺失时返回 null，不再内联默认值）
     */
    public String getSignKey() {
        return this.signKey;
    }

    public GXSSOAuthorization getAuthorization() {
        return authorization;
    }

    public GXSSOProperties setAuthorization(GXSSOAuthorization authorization) {
        this.authorization = authorization;
        return this;
    }
}
