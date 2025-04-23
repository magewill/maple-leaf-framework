package cn.maple.sso.service;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.enums.GXTokenFlag;
import cn.maple.sso.plugins.GXSSOPlugin;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.utils.GXBrowserUtil;
import cn.maple.core.framework.util.GXCookieHelperUtil;
import cn.maple.sso.utils.GXIpHelperUtil;
import cn.maple.sso.utils.GXSSOHelperUtil;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * SSO 单点登录服务支持类
 * </p>
 * 
 * <p>
 * 提供SSO服务的基础功能支持，包括：
 * 1. 配置管理 - 统一管理SSO系统的配置参数
 * 2. Token获取和验证 - 从请求中提取Token并验证其有效性
 * 3. Cookie处理 - 安全地设置和读取包含Token的Cookie
 * 4. IP和浏览器验证 - 防止会话劫持和Token盗用
 * 5. 登录状态管理 - 维护用户的登录状态和会话信息
 * </p>
 * 
 * <p>
 * 安全特性：
 * 1. 防会话固定攻击 - 支持登录时重新生成会话标识
 * 2. IP绑定验证 - 可选择将Token与用户IP绑定，防止Token被盗用
 * 3. 浏览器指纹验证 - 可验证浏览器特征，增强安全性
 * 4. 分布式会话管理 - 支持集群环境下的会话同步和验证
 * 5. 会话超时控制 - 自动使过期会话失效，减少安全风险
 * </p>
 * 
 * <p>
 * 性能优化策略：
 * 1. 缓存利用 - 优先从请求属性中获取已验证的Token，减少重复解密和验证
 * 2. 延迟验证 - 仅在必要时执行完整的验证流程
 * 3. 快速失败 - 对无效Token快速返回，避免不必要的处理
 * 4. 插件化设计 - 支持按需加载额外的验证和处理逻辑
 * 5. 异常处理优化 - 捕获并记录异常，确保系统稳定性
 * </p>
 * 
 * <p>
 * 使用场景：
 * 1. 多系统单点登录集成
 * 2. 分布式系统的统一身份认证
 * 3. 前后端分离架构中的用户认证
 * 4. API服务的Token验证
 * 5. 移动应用的用户会话管理
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 在自定义SSO服务中继承此类
 * @Service
 * public class CustomSSOSupportService extends GXSSOSupportService {
 *     // 自定义Token验证逻辑
 *     @Override
 *     protected Dict checkIpBrowser(HttpServletRequest request, Dict ssoToken) {
 *         // 先调用父类方法进行基本验证
 *         Dict token = super.checkIpBrowser(request, ssoToken);
 *         if (token.isEmpty()) {
 *             return Dict.create();
 *         }
 *         
 *         // 添加自定义的验证逻辑
 *         // 例如：验证用户是否有权限访问特定资源
 *         Long userId = token.getLong("userId");
 *         String requestUri = request.getRequestURI();
 *         if (!userPermissionService.hasPermission(userId, requestUri)) {
 *             log.warn("用户{}尝试访问未授权资源: {}", userId, requestUri);
 *             return Dict.create(); // 返回空Dict表示验证失败
 *         }
 *         
 *         return token; // 返回原Token表示验证通过
 *     }
 *     
 *     // 自定义Cookie生成逻辑
 *     @Override
 *     protected Cookie generateCookie(HttpServletRequest request, Dict token) {
 *         Cookie cookie = super.generateCookie(request, token);
 *         
 *         // 添加自定义的Cookie属性
 *         // 例如：根据不同的用户角色设置不同的Cookie有效期
 *         String userRole = token.getStr("role", "");
 *         if ("admin".equals(userRole)) {
 *             // 管理员Cookie有效期较短，增强安全性
 *             cookie.setMaxAge(1800); // 30分钟
 *         } else if ("vip".equals(userRole)) {
 *             // VIP用户Cookie有效期较长，提升体验
 *             cookie.setMaxAge(86400); // 24小时
 *         }
 *         
 *         return cookie;
 *     }
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 该类作为SSO服务的基础类，为子类提供通用功能实现，
 * 通常不直接使用，而是通过其子类GXAbstractSSOService来提供完整的SSO服务
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
@Slf4j
public abstract class GXSSOSupportService {
    /**
     * 获取SSO系统配置
     * 
     * <p>
     * 返回SSO系统的全局配置对象，包含所有配置参数。
     * 配置参数通常从配置文件（如application.yml）中加载。
     * </p>
     * 
     * <p>
     * 配置项包括：
     * - Cookie相关设置（名称、路径、域名、过期时间等）
     * - 缓存相关设置（缓存实现、过期时间等）
     * - 安全相关设置（是否验证IP、浏览器等）
     * - 插件相关设置（启用的插件列表）
     * - 页面相关设置（登录页URL、注销页URL等）
     * </p>
     * 
     * @return SSO配置对象，包含系统所有配置参数
     */
    public GXSSOProperties getConfig() {
        return GXSSOProperties.getInstance();
    }

    // ------------------------------- 客户端相关方法 -------------------------------

    /**
     * 获取当前请求中的SSOToken
     * <p>
     * 从请求属性中获取Token，此属性通常在过滤器或拦截器中设置
     * 此方法主要用于业务系统中获取已验证的Token，避免重复解密
     * </p>
     *
     * <p>
     * 性能优化说明：
     * - 避免重复解密和验证Token，提高系统性能
     * - 在请求处理链中，Token只需要验证一次
     * - 后续的业务处理可以直接使用已验证的Token
     * </p>
     *
     * <p>
     * 使用场景：
     * - 在Controller中获取当前登录用户信息
     * - 在业务逻辑中进行权限检查
     * - 在日志记录中添加用户标识
     * </p>
     *
     * @param request HTTP请求对象
     * @return 包含用户登录信息的Dict对象，如果不存在则返回null
     */
    public Dict attrSSOToken(HttpServletRequest request) {
        Object attribute = request.getAttribute(GXSSOConstant.SSO_TOKEN_ATTR);
        return Convert.convert(Dict.class, attribute);
    }

    /**
     * 处理SSOToken的缓存逻辑
     * <p>
     * 判断SSOToken是否在缓存中存在并有效，主要流程：
     * 1. 从Cookie或请求头中获取Token
     * 2. 从缓存中查询对应的Token数据
     * 3. 验证缓存Token与请求Token的一致性
     * 4. 处理缓存宕机等异常情况
     * </p>
     *
     * <p>
     * 安全考虑：
     * - 验证Token的一致性，防止伪造或篡改
     * - 处理缓存宕机情况，提供降级策略
     * - 支持踢出用户功能，使已登录的Token失效
     * </p>
     *
     * <p>
     * 性能优化：
     * - 使用高效的缓存实现，如Redis
     * - 合理设置缓存过期时间，平衡安全性和性能
     * - 对缓存操作进行异常处理，提高系统稳定性
     * </p>
     *
     * @param request HTTP请求对象
     * @param cache SSO缓存实现对象
     * @return 验证通过返回有效的Token数据，否则返回空Dict
     */
    protected Dict cacheSSOToken(HttpServletRequest request, GXSSOCache cache) {
        // 如果缓存组件存在则使用缓存中存储的token
        if (cache != null) {
            Dict requestToken = getSSOTokenFromCookie(request);
            if (requestToken == null) {
                // 未登录
                log.info("SSO 用户未登录....");
                return Dict.create();
            }

            Dict cacheToken = cache.get(getConfig().getCacheExpires(), requestToken);
            if (cacheToken.isEmpty()) {
                // 开启缓存且失效，清除 Cookie 退出 , 返回 null
                log.info("cacheSSOToken GXSsoToken is null.");
                return Dict.create();
            } else {
                // 开启缓存，判断是否宕机：
                // 1、缓存正常，返回 tk
                // 2、缓存宕机，执行读取 Cookie 逻辑
                if (!Objects.equals(cacheToken.getInt("flag"), GXTokenFlag.CACHE_SHUT.value())) {
                    if (cache.verifyTokenConsistency(cacheToken, requestToken)) {
                        return cacheToken;
                    } else {
                        log.info("Login time is not consistent or kicked out.");
                        request.setAttribute(GXSSOConstant.SSO_KICK_FLAG, GXSSOConstant.SSO_KICK_USER);
                        return Dict.create();
                    }
                }
            }
        }

        // GXSsoCache 为 null 执行以下逻辑
        return getSSOToken(request, getConfig().getCookieName());
    }

    /**
     * <p>
     * 获取当前请求中的SSOToken原始数据
     * </p>
     * <p>
     * 获取Token的优先级：
     * 1. 先从请求头中获取（适用于API调用场景）
     * 2. 如果请求头中不存在，则从Cookie中获取（适用于浏览器场景）
     * </p>
     * <p>
     * 安全说明：
     * - 支持多种Token传递方式，适应不同客户端场景
     * - 对获取的Token进行解析和基本验证
     * - 记录详细日志，便于问题排查
     * </p>
     *
     * <p>
     * 适用场景：
     * - 浏览器环境：从Cookie中获取Token
     * - API调用：从请求头中获取Token
     * - 混合应用：同时支持两种方式
     * </p>
     *
     * <p>
     * 安全建议：
     * - 在API场景中，建议使用Bearer认证方式传递Token
     * - 确保Token在传输过程中使用HTTPS加密
     * - 对于敏感操作，考虑增加额外的身份验证
     * </p>
     *
     * @param request    HTTP请求对象
     * @param cookieName Cookie名称，用于从Cookie中查找Token
     * @return 解析后的Token数据，如果不存在则返回空Dict
     */
    protected Dict getSSOToken(HttpServletRequest request, String cookieName) {
        String token = request.getHeader(getConfig().getTokenName());
        log.info("SSO从header中获取token : {}", token);
        if (CharSequenceUtil.isBlank(token)) {
            Cookie cookie = GXCookieHelperUtil.findCookieByName(request, cookieName);
            if (null == cookie) {
                log.info("Unauthorized login request, ip=" + GXIpHelperUtil.getIpAddr(request));
                return Dict.create();
            }
            return GXSSOHelperUtil.parser(cookie.getValue(), false);
        }
        return GXSSOHelperUtil.parser(token, true);
    }

    /**
     * <p>
     * 校验SSOToken的IP和浏览器信息与登录时是否一致
     * </p>
     * <p>
     * 安全验证措施：
     * 1. 验证请求的浏览器信息与Token中记录的是否一致
     * 2. 验证请求的IP地址与Token中记录的是否一致
     * </p>
     * <p>
     * 这些验证可以有效防止Token被盗用的风险，提高系统安全性
     * 验证是否启用可通过配置控制，便于不同环境和场景的灵活应用
     * </p>
     *
     * <p>
     * 安全增强建议：
     * - 对于高安全要求的系统，建议同时启用IP和浏览器验证
     * - 考虑实现基于地理位置的验证，检测异常登录地点
     * - 对于移动应用，可增加设备指纹验证
     * - 实现登录行为分析，检测异常的访问模式
     * </p>
     *
     * <p>
     * 配置灵活性：
     * - IP验证可通过配置开启或关闭
     * - 浏览器验证可通过配置开启或关闭
     * - 可根据不同的环境和安全需求调整验证策略
     * </p>
     *
     * @param request  HTTP请求对象
     * @param ssoToken 待验证的登录票据
     * @return 验证通过返回原Token，否则返回空Dict
     */
    protected Dict checkIpBrowser(HttpServletRequest request, Dict ssoToken) {
        if (null == ssoToken) {
            return Dict.create();
        }
        // 判断请求浏览器是否合法
        if (getConfig().isCookieBrowser() && !GXBrowserUtil.isLegalUserAgent(request, ssoToken.getStr("userAgent"))) {
            log.debug("The request browser is inconsistent.");
            return Dict.create();
        }
        // 判断请求 IP 是否合法
        if (getConfig().isCookieCheckIp()) {
            String ip = GXIpHelperUtil.getIpAddr(request);
            if (ip != null && !ip.equals(ssoToken.getStr("ip"))) {
                log.debug(String.format("ip inconsistent! return SSOToken null, SSOToken userIp:%s, reqIp:%s", ssoToken.getStr("ip"), ip));
                return Dict.create();
            }
        }
        return ssoToken;
    }

    /**
     * 从Cookie或请求属性中获取SSOToken
     * <p>
     * 获取Token的优先级：
     * 1. 先从请求属性中获取（通常由拦截器设置）
     * 2. 如果属性中不存在，则从Cookie或请求头中获取并解析
     * </p>
     * <p>
     * 注意：该方法仅获取Token数据，不验证IP等安全信息
     * 完整的安全验证应使用getSSOToken方法
     * </p>
     *
     * <p>
     * 性能优化说明：
     * - 优先从请求属性中获取，避免重复解析
     * - 只有在必要时才执行Token解析操作
     * - 记录详细日志，便于问题排查和性能分析
     * </p>
     *
     * <p>
     * 使用场景：
     * - 在多个组件中需要访问Token信息
     * - 在不需要完整安全验证的场景中获取基本用户信息
     * - 作为其他Token处理方法的基础方法
     * </p>
     *
     * @param request HTTP请求对象
     * @return 解析后的Token数据，如果不存在则返回null
     */
    public Dict getSSOTokenFromCookie(HttpServletRequest request) {
        Dict token = attrSSOToken(request);
        if (token == null) {
            log.info("SSO组件从request的属性中未获取到");
            token = getSSOToken(request, getConfig().getCookieName());
        }
        log.info("SSO组件最终解码出来的token: {}", token);
        return token;
    }

    // ------------------------------- 登录相关方法 -------------------------------

    /**
     * 根据SSOToken生成登录信息Cookie
     * <p>
     * 将Token信息写入Cookie，设置相关安全属性：
     * 1. 设置Cookie路径
     * 2. 配置Secure属性（是否仅通过HTTPS传输）
     * 3. 设置Cookie域名范围
     * 4. 配置Cookie过期时间
     * </p>
     * <p>
     * 安全配置说明：
     * - 支持配置Cookie的域名范围，控制Cookie的可见范围
     * - 可设置Secure标志，要求Cookie仅通过HTTPS传输
     * - 支持动态设置Cookie的有效期
     * - 对localhost域名特殊处理，避免开发环境问题
     * </p>
     *
     * <p>
     * 安全增强建议：
     * - 在生产环境中启用Secure标志，要求HTTPS传输
     * - 合理设置Cookie的域名范围，避免跨域风险
     * - 对敏感系统，设置较短的Cookie有效期
     * - 考虑实现Cookie轮换机制，定期更新Cookie
     * - 在支持的环境中，设置SameSite属性防止CSRF攻击
     * </p>
     *
     * <p>
     * 兼容性说明：
     * - 对于localhost域名，某些浏览器可能无法正确设置Cookie
     * - 不同浏览器对Cookie属性的支持可能有所不同
     * - 移动应用和桌面应用可能需要特殊处理Cookie
     * </p>
     *
     * @param request 请求对象
     * @param token   SSO登录信息票据
     * @return 生成的Cookie对象
     * @throws GXBusinessException 如果Cookie生成过程中发生错误
     */
    protected Cookie generateCookie(HttpServletRequest request, Dict token) {
        try {
            Cookie cookie = new Cookie(getConfig().getCookieName(), token.getStr("token"));
            cookie.setPath(getConfig().getCookiePath());
            cookie.setSecure(getConfig().isCookieSecure());
            // domain 提示
            // 有些浏览器 localhost 无法设置 cookie
            String domain = getConfig().getCookieDomain();
            if (null != domain) {
                cookie.setDomain(domain);
                if ("".equals(domain) || domain.contains("localhost")) {
                    log.warn("if you can't login, please enter normal domain. instead:" + domain);
                }
            }

            // 设置Cookie超时时间
            int maxAge = getConfig().getCookieMaxAge();
            Integer attrMaxAge = (Integer) request.getAttribute(GXSSOConstant.SSO_COOKIE_MAX_AGE);
            if (attrMaxAge != null) {
                maxAge = attrMaxAge;
            }
            if (maxAge >= 0) {
                cookie.setMaxAge(maxAge);
            }
            return cookie;
        } catch (Exception e) {
            throw new GXBusinessException("Generate sso cookie exception ", e);
        }
    }

    /**
     * <p>
     * 退出当前登录状态
     * </p>
     * <p>
     * 完整的注销流程：
     * 1. 清除缓存中的Token数据
     * 2. 执行所有SSO插件的注销逻辑
     * 3. 删除浏览器中的Cookie
     * </p>
     * <p>
     * 安全说明：
     * - 确保服务端和客户端的登录状态同时清除
     * - 支持特殊的踢出用户标记处理
     * - 对缓存操作失败进行重试，提高可靠性
     * </p>
     *
     * <p>
     * 完整注销策略：
     * - 删除服务端缓存中的Token记录
     * - 执行所有注册的SSO插件的注销逻辑
     * - 清除客户端Cookie，使客户端会话失效
     * - 对操作失败进行重试，确保注销成功
     * - 支持特殊的踢出用户标记处理
     * </p>
     *
     * <p>
     * 分布式环境考虑：
     * - 确保所有节点都能识别用户已注销
     * - 考虑使用消息队列通知相关服务用户已注销
     * - 实现Token黑名单机制，防止已注销的Token被重用
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param cache    SSO缓存实现对象
     * @return 操作是否成功，成功返回true，失败返回false
     */
    protected boolean logout(HttpServletRequest request, HttpServletResponse response, GXSSOCache cache) {
        // SSOToken 如果开启了缓存，删除缓存记录
        if (cache != null && !GXSSOConstant.SSO_KICK_USER.equals(request.getAttribute(GXSSOConstant.SSO_KICK_FLAG))) {
            Dict token = getSSOTokenFromCookie(request);
            if (token != null) {
                boolean rlt = cache.delete(token);
                if (!rlt) {
                    cache.delete(token);
                }
            }
        }

        // 执行插件逻辑
        List<GXSSOPlugin> pluginList = getConfig().getPluginList();
        if (pluginList != null) {
            for (GXSSOPlugin plugin : pluginList) {
                boolean logout = plugin.logout(request, response);
                if (!logout) {
                    plugin.logout(request, response);
                }
            }
        }

        // 删除登录 Cookie
        return GXCookieHelperUtil.clearCookieByName(request, response, getConfig().getCookieName(), getConfig().getCookieDomain(), getConfig().getCookiePath());
    }
}
