package cn.maple.sso.utils;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXAuthCodeUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.plugins.GXSSOPlugin;
import cn.maple.sso.properties.GXSSOConfigProperties;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.service.GXAbstractSSOService;
import cn.maple.sso.service.GXTokenConfigService;
import cn.maple.sso.service.impl.GXConfigurableAbstractSSOServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * <p>
 * SSO 帮助类
 * </p>
 * 
 * 提供SSO（单点登录）系统的核心工具方法，包括：
 * 1. SSO配置管理
 * 2. SSO服务初始化
 * 3. Cookie操作
 * 4. Token获取和解析
 * 5. 登录状态管理
 * 
 * 该类是SSO系统的门面(Facade)，为外部系统提供统一的接口
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
public class GXSSOHelperUtil {
    /**
     * 日志对象
     * 用于记录SSO操作日志，便于问题排查和安全审计
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(GXSSOHelperUtil.class);
    /**
     * SSO配置对象
     * 存储SSO系统的全局配置信息，采用单例模式
     */
    protected static GXSSOProperties ssoConfig;
    /**
     * SSO服务处理对象
     * 负责执行具体的SSO业务逻辑，采用单例模式
     */
    protected static GXAbstractSSOService ssoService;

    /**
     * 私有构造函数
     * 防止实例化工具类，确保所有方法都是静态调用
     */
    private GXSSOHelperUtil() {

    }

    /**
     * <p>
     * 获取SSO配置对象
     * </p>
     * 
     * <p>
     * 线程安全的懒加载方式初始化SSO配置，包括：
     * 1. 加载基本配置信息
     * 2. 注册SSO插件
     * 3. 配置缓存实现
     * </p>
     * 
     * <p>
     * 配置来源优先级：
     * 1. Spring容器中的GXSSOConfigProperties bean
     * 2. 默认配置
     * </p>
     * 
     * <p>
     * 线程安全说明：
     * - 使用双重检查锁定模式确保线程安全
     * - 防止多线程环境下的配置覆盖问题
     * - 避免不必要的同步开销
     * </p>
     *
     * @return SSO配置对象
     * @author britton
     * @since 2021-09-17
     */
    public static GXSSOProperties getSSOConfig() {
        // 双重检查锁定，确保线程安全
        if (Objects.isNull(ssoConfig)) {
            synchronized (GXSSOHelperUtil.class) {
                if (Objects.isNull(ssoConfig)) {
                    try {
                        // 为每个应用设置自己的配置信息
                        if (Objects.nonNull(GXSpringContextUtils.getBean(GXSSOConfigProperties.class))) {
                            ssoConfig = Objects.requireNonNull(GXSpringContextUtils.getBean(GXSSOConfigProperties.class)).getConfig();
                        } else {
                            ssoConfig = new GXSSOProperties();
                        }
                        
                        // 为每个应用配置自己的插件
                        Map<String, GXSSOPlugin> ssoPluginMap = GXSpringContextUtils.getBeans(GXSSOPlugin.class);
                        if (!ssoPluginMap.isEmpty()) {
                            List<GXSSOPlugin> plugins = new ArrayList<>();
                            ssoPluginMap.forEach((key, val) -> plugins.add(val));
                            ssoConfig.setPluginList(plugins);
                        }
                        
                        // 为每个应用配置自己的SsoCache实例
                        if (Objects.nonNull(GXSpringContextUtils.getBean(GXSSOCache.class))) {
                            ssoConfig.setCache(GXSpringContextUtils.getBean(GXSSOCache.class));
                        }
                    } catch (Exception e) {
                        LOGGER.error("初始化SSO配置时发生错误", e);
                        // 确保即使出错也有基本配置可用
                        if (Objects.isNull(ssoConfig)) {
                            ssoConfig = new GXSSOProperties();
                        }
                    }
                }
            }
        }
        return ssoConfig;
    }

    /**
     * 设置SSO配置对象
     * <p>
     * 手动设置SSO配置，用于特殊场景下的配置覆盖
     * 通常情况下应使用getSSOConfig方法获取自动配置
     * </p>
     *
     * @param ssoConfig 要设置的SSO配置对象
     * @return 设置后的SSO配置对象
     * @author britton
     * @since 2021-09-17
     */
    public static GXSSOProperties setSsoConfig(GXSSOProperties ssoConfig) {
        GXSSOHelperUtil.ssoConfig = ssoConfig;
        return GXSSOHelperUtil.ssoConfig;
    }

    /**
     * SSO服务初始化
     * <p>
     * 懒加载方式初始化SSO服务实现，优先级：
     * 1. Spring容器中的GXAbstractSSOService实现
     * 2. 默认的GXConfigurableAbstractSSOServiceImpl实现
     * </p>
     * 
     * @return SSO服务对象
     */
    public static GXAbstractSSOService getSSOService() {
        if (Objects.isNull(ssoService)) {
            if (Objects.nonNull(GXSpringContextUtils.getBean(GXAbstractSSOService.class))) {
                ssoService = GXSpringContextUtils.getBean(GXAbstractSSOService.class);
            } else {
                ssoService = new GXConfigurableAbstractSSOServiceImpl();
            }
        }
        return ssoService;
    }

    // ------------------------------- 登录相关方法 -------------------------------

    /**
     * 设置加密Cookie（登录验证成功）
     * <p>
     * 将用户登录信息写入加密Cookie，并根据需要处理JSESSIONID
     * </p>
     * <p>
     * 参数说明：
     * - invalidate为true时：销毁当前JSESSIONID并创建新的JSESSIONID，防止会话固定攻击
     * - invalidate为false时：仅设置Cookie，不修改JSESSIONID
     * </p>
     * <p>
     * Cookie超时设置：
     * 可通过request.setAttribute(GXSsoConfig.SSO_COOKIE_MAX_AGE, maxAge)动态设置
     * maxAge定义：
     * - -1: 浏览器关闭时自动删除（会话Cookie）
     * - 0: 立即删除Cookie
     * - 正整数: 表示Cookie有效期（以秒为单位），如120表示2分钟
     * </p>
     * <p>
     * 安全说明：
     * - 支持防会话固定攻击
     * - Cookie内容经过加密处理
     * - 可配置HttpOnly和Secure选项
     * </p>
     *
     * @param request    HTTP请求对象
     * @param response   HTTP响应对象
     * @param ssoToken   SSO票据，包含用户登录信息
     * @param invalidate 是否销毁当前JSESSIONID
     */
    public static void setCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken, boolean invalidate) {
        if (invalidate) {
            getSSOService().authCookie(request, response, ssoToken);
        } else {
            getSSOService().setCookie(request, response, ssoToken);
        }
    }

    public static void setCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken) {
        setCookie(request, response, ssoToken, false);
    }

    // ------------------------------- 客户端相关方法 -------------------------------

    /**
     * 获取当前请求的Token
     * <p>
     * 该方法直接从Cookie或请求头中解密获取Token
     * 常用于登录系统及拦截器中，执行完整的Token验证流程
     * </p>
     * <p>
     * 注意：如果请求已经过登录拦截器处理，建议使用attrToken(request)方法
     * 避免重复解密，提高性能
     * </p>
     * <p>
     * 安全说明：
     * - 执行完整的Token验证，包括IP、浏览器信息验证
     * - 支持插件机制进行扩展验证
     * - 验证失败时返回空对象，而非异常，避免信息泄露
     * </p>
     *
     * @param request HTTP请求对象
     * @return 包含用户登录信息的Dict对象，验证失败则返回空Dict
     */
    public static Dict getSSOToken(HttpServletRequest request) {
        return getSSOService().getSSOToken(request);
    }

    /**
     * 从请求属性中获取Token
     * <p>
     * 从请求属性中获取已验证的Token数据
     * 该数据通常由登录拦截器放入request中，避免重复解密和验证
     * </p>
     * <p>
     * 性能说明：
     * - 相比getSSOToken方法，此方法避免了重复解密和验证，性能更好
     * - 适用于已通过登录拦截器的请求
     * </p>
     *
     * @param request HTTP请求对象
     * @return 包含用户登录信息的Dict对象，不存在则返回null
     */
    public static Dict attrToken(HttpServletRequest request) {
        return getSSOService().attrSSOToken(request);
    }

    /**
     * 退出登录并重定向到注销页面
     * <p>
     * 执行完整的注销流程，包括：
     * 1. 清除客户端Cookie
     * 2. 清除服务端缓存
     * 3. 重定向到配置的注销页面
     * </p>
     * <p>
     * 重定向目标由sso.properties中的sso.logout.url属性指定
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @throws IOException 如果重定向过程中发生I/O错误
     */
    public static void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        getSSOService().logout(request, response);
    }

    /**
     * 清理当前登录状态
     * <p>
     * 清理用户的登录信息，但不进行页面重定向，包括：
     * 1. 清除客户端Cookie
     * 2. 清除服务端缓存
     * 3. 执行SSO插件的注销逻辑
     * </p>
     * <p>
     * 与logout方法的区别：此方法仅清理状态，不进行页面重定向
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @return 操作是否成功，成功返回true，失败返回false
     */
    public static boolean clearLogin(HttpServletRequest request, HttpServletResponse response) {
        return getSSOService().clearLogin(request, response);
    }

    /**
     * 退出并重定向到登录页
     * <p>
     * 清理当前登录状态，然后将用户重定向到登录页面
     * 重定向目标由sso.properties中的sso.login.url属性指定
     * </p>
     * <p>
     * 适用场景：
     * - 会话过期时自动跳转登录
     * - 用户主动退出后跳转登录
     * - 检测到安全问题强制重新登录
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @throws IOException 如果重定向过程中发生I/O错误
     */
    public static void clearRedirectLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        getSSOService().clearRedirectLogin(request, response);
    }

    /**
     * 获取Token的缓存主键
     * <p>
     * 根据当前请求信息生成Token的缓存键
     * 缓存键通常包含用户ID和平台信息，用于在缓存系统中唯一标识Token
     * </p>
     * <p>
     * 实现说明：
     * 1. 从请求头中获取平台信息
     * 2. 从当前请求上下文中获取用户ID
     * 3. 调用TokenConfigService生成缓存键
     * </p>
     *
     * @param request 当前HTTP请求对象
     * @return 生成的缓存键字符串
     */
    public static String getTokenCacheKey(HttpServletRequest request) {
        GXTokenConfigService tokenConfigService = GXSpringContextUtils.getBean(GXTokenConfigService.class);
        assert tokenConfigService != null;
        String platform = Optional.ofNullable(request.getHeader(GXTokenConstant.PLATFORM)).orElse("");
        Dict data = Dict.create().set(GXTokenConstant.PLATFORM, platform);
        Dict loginCredentials = GXCurrentRequestContextUtils.getLoginCredentials(GXTokenConstant.TOKEN_NAME, tokenConfigService.getTokenSecret());
        Long userId = Optional.ofNullable(loginCredentials.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME)).orElse(0L);
        return tokenConfigService.getTokenCacheKey(userId, data);
    }

    /**
     * 获取Token的缓存主键
     * <p>
     * 根据用户ID生成Token的缓存键
     * 这是一个简化版的缓存键生成方法，仅使用用户ID
     * </p>
     * <p>
     * 与带请求参数的方法区别：
     * - 此方法仅使用用户ID生成缓存键，不包含平台等信息
     * - 适用于不关心多平台登录的简单场景
     * </p>
     *
     * @param userId 用户ID对象
     * @return 生成的缓存键字符串
     */
    public static String getTokenCacheKey(Object userId) {
        return GXSSOProperties.toCacheKey(userId);
    }

    /**
     * 踢出指定用户ID的登录用户
     * <p>
     * 强制特定用户退出系统，清除其登录状态
     * 通常用于管理员操作或检测到异常登录时的安全措施
     * </p>
     * <p>
     * 实现原理：
     * - 删除用户Token的服务端缓存
     * - 用户下次请求时，由于缓存验证失败而被要求重新登录
     * </p>
     *
     * @param userId 要踢出的用户ID
     * @return 操作是否成功，成功返回true，失败返回false
     */
    public static boolean kickLogin(Object userId) {
        return getSSOService().kickLogin(userId);
    }


    /**
     * 解析浏览器端的Token
     * <p>
     * 解析并验证客户端传递的Token字符串
     * 默认不标记Token来源，等同于parser(token, false)
     * </p>
     *
     * @param token Token字符串
     * @return 解码后的Token数据对象
     */
    public static Dict parser(String token) {
        return parser(token, false);
    }

    /**
     * <p>
     * 解析浏览器端的Token
     * </p>
     * 
     * <p>
     * 解析并验证客户端传递的Token字符串，
     * 支持标记Token的来源（Cookie或Header）。
     * </p>
     * 
     * <p>
     * 安全特性：
     * 1. 使用配置的密钥进行解密，确保Token的机密性
     * 2. 自动添加客户端IP信息，用于后续验证，防止Token盗用
     * 3. 对RPC调用做了特殊处理，适应不同调用场景
     * 4. 记录详细日志，便于安全审计和问题排查
     * 5. 完善的异常处理，防止解析错误导致系统不稳定
     * 6. 对空值和非法值进行严格检查，提高系统健壮性
     * </p>
     *
     * @param token  Token字符串
     * @param header 标记Token是否来自请求头，true表示来自Header，false表示来自Cookie
     * @return 解码后的Token数据对象
     * @throws GXBusinessException 当TokenConfigService未正确配置或Token解析失败时抛出异常
     */
    public static Dict parser(String token, boolean header) {
        // 如果是RPC 直接返回
        if (GXCurrentRequestContextUtils.isRPC()) {
            return Dict.create();
        }
        
        // 检查Token是否为空
        if (CharSequenceUtil.isBlank(token)) {
            LOGGER.warn("接收到空的Token字符串");
            return Dict.create();
        }
        
        if (header) {
            LOGGER.info("token字符串来自于header");
        } else {
            LOGGER.info("token字符串来自于cookie");
        }
        
        try {
            // 获取Token配置服务
            GXTokenConfigService tokenSecretService = GXSpringContextUtils.getBean(GXTokenConfigService.class);
            if (Objects.isNull(tokenSecretService)) {
                throw new GXBusinessException("请实现GXTokenConfigService类,并将其加入到spring容器中");
            }
            
            // 获取密钥并解密Token
            String tokenSecret = tokenSecretService.getTokenSecret();
            if (CharSequenceUtil.isBlank(tokenSecret)) {
                LOGGER.error("Token密钥为空，无法解析Token");
                throw new GXBusinessException("Token密钥配置错误");
            }
            
            // 解密Token
            String decodedToken;
            try {
                decodedToken = GXAuthCodeUtils.authCodeDecode(token, tokenSecret);
                if (CharSequenceUtil.isBlank(decodedToken)) {
                    LOGGER.warn("Token解密结果为空");
                    return Dict.create();
                }
            } catch (Exception e) {
                LOGGER.error("Token解密失败: {}", e.getMessage());
                return Dict.create();
            }
            
            // 解析JSON
            Dict requestToken;
            try {
                requestToken = JSONUtil.toBean(decodedToken, Dict.class);
            } catch (Exception e) {
                LOGGER.error("Token JSON解析失败: {}", e.getMessage());
                return Dict.create();
            }
            
            // 添加IP信息用于安全验证
            String clientIP = GXCurrentRequestContextUtils.getClientIP();
            requestToken.put("ip", clientIP);
            
            // 记录脱敏后的Token信息
            Dict logToken = new Dict(requestToken);
            if (logToken.containsKey("password")) {
                logToken.put("password", "******");
            }
            LOGGER.info("SSO组件解析出来的token信息 : {}", logToken);
            
            return requestToken;
        } catch (GXBusinessException e) {
            throw e; // 业务异常直接抛出
        } catch (Exception e) {
            LOGGER.error("解析Token时发生未预期的错误", e);
            return Dict.create(); // 其他异常返回空对象，避免系统崩溃
        }
    }
}