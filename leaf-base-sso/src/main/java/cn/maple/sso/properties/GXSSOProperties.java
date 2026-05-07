package cn.maple.sso.properties;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCookieHelperUtil;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.oauth.GXSSOAuthorization;
import cn.maple.sso.plugins.GXSSOPlugin;
import cn.maple.sso.utils.GXSSOHelperUtil;
import lombok.Data;
import lombok.experimental.Accessors;
import cn.hutool.core.text.CharSequenceUtil;

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
     * 编码格式，默认 UTF-8
     */
    private String encoding = GXSSOConstant.ENCODING;

    /**
     * 签名密钥（用于对此算法）
     */
    private String signKey;

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
     * RSA 密钥 keypass
     */
    private String rsaKeypass = "llTs1p68K";

    /**
     * RSA 密钥 storepass
     */
    private String rsaStorepass = "lLt66Y8L321";

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
    private String cookieDomain;

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
     * 缓存有效期设置
     */
    private int cacheExpires = GXCookieHelperUtil.CLEAR_BROWSER_IS_CLOSED;

    /**
     * 权限认证（默认 false）
     */
    private boolean permissionUri = false;

    /**
     * 插件列表
     */
    private List<GXSSOPlugin> pluginList;

    /**
     * SSO 缓存
     */
    private GXSSOCache cache;

    /**
     * SSO 权限授权
     */
    private GXSSOAuthorization authorization;

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

    public String getRsaCertStore() {
        if (CharSequenceUtil.isBlank(rsaCertStore)) {
            throw new GXBusinessException("public.cert not found");
        }
        return rsaCertStore;
    }

    /**
     * 签名密钥
     */
    public String getSignKey() {
        if (CharSequenceUtil.isBlank(this.signKey)) {
            return "Janfv5UgKhoDrH73EZT7m+81pgqLN3EjWKXZtqF9lQHH9WruxqX0+FkQys6XK0QXzSUckseOAZGeQyvfreA3tw==";
        }
        return this.signKey;
    }

    public String getRsaJksStore() {
        if (CharSequenceUtil.isBlank(rsaJksStore)) {
            throw new GXBusinessException("jwt.jks not found");
        }
        return rsaJksStore;
    }

    public GXSSOAuthorization getAuthorization() {
        return authorization;
    }

    public GXSSOProperties setAuthorization(GXSSOAuthorization authorization) {
        this.authorization = authorization;
        return this;
    }
}
