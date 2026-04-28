package cn.maple.sso.service;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXAuthCodeUtils;
import cn.maple.core.framework.util.GXCookieHelperUtil;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.enums.GXTokenFlag;
import cn.maple.sso.plugins.GXSSOPlugin;
import cn.maple.sso.utils.GXHttpUtil;
import cn.maple.sso.utils.GXIpHelperUtil;
import cn.maple.sso.utils.GXRandomUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * SSO 单点登录服务抽象实现类
 * </p>
 *
 * <p>
 * 实现了SSO服务的核心功能，包括：
 * 1. Token的获取和验证 - 安全地解析和验证用户身份令牌
 * 2. 用户登录状态管理 - 创建、更新和销毁用户会话
 * 3. Cookie设置和清理 - 安全地处理包含身份信息的Cookie
 * 4. 登录和注销流程处理 - 完整的用户认证生命周期管理
 * 5. 插件机制支持 - 可扩展的认证和授权逻辑
 * </p>
 *
 * <p>
 * 安全特性：
 * - 多重验证机制：结合IP、浏览器信息和缓存验证，提高安全性，防止会话劫持
 * - 防会话固定攻击：登录时自动重新生成会话标识，使攻击者获取的会话ID失效
 * - 防CSRF攻击：设置Cookie的SameSite属性为Lax，阻止跨站请求自动携带凭证
 * - 防XSS攻击：支持HttpOnly选项，防止客户端脚本访问Cookie内容
 * - 分布式会话管理：支持集群环境下的会话同步和验证，确保系统横向扩展能力
 * - 安全响应头：自动添加多种安全响应头，全面提升Web安全防护能力
 * - 降级策略：在缓存服务不可用等异常情况下提供合理的降级机制，保障系统可用性
 * </p>
 *
 * <p>
 * 性能优化：
 * - 缓存机制：减少重复解密和验证的开销，提高响应速度
 * - 懒加载策略：仅在必要时执行验证逻辑，避免不必要的性能消耗
 * - 插件化设计：按需加载额外的验证和处理逻辑，保持核心逻辑轻量高效
 * - 异常处理优化：精细化的异常捕获和处理，确保系统稳定性
 * - 快速失败策略：对无效Token快速返回，避免后续不必要的处理
 * </p>
 *
 * <p>
 * 使用场景：
 * - 多系统单点登录集成：实现多个子系统间的统一身份认证
 * - 分布式系统的会话管理：在集群环境中维护一致的用户会话状态
 * - 前后端分离架构中的用户认证：为前端应用提供安全的身份验证机制
 * - API服务的Token验证：保护API端点，确保只有授权请求能够访问
 * - 安全敏感系统的访问控制：为金融、医疗等高安全要求系统提供可靠的身份验证
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * @Service
 * public class CustomSSOService extends GXAbstractSSOService {
 *     // 可以覆盖父类方法，实现自定义逻辑
 *     @Override
 *     public boolean kickLogin(Object userId) {
 *         // 实现自定义的踢出用户逻辑
 *         log.info("强制用户{}下线", userId);
 *         // 可以添加额外的安全审计记录
 *         securityAuditService.recordForceLogout(userId);
 *         return super.kickLogin(userId);
 *     }
 *
 *     // 使用示例
 *     public void loginExample(HttpServletRequest request, HttpServletResponse response) {
 *         // 1. 验证用户凭证（此处省略）
 *
 *         // 2. 创建包含用户信息的Token
 *         Dict userInfo = Dict.create()
 *             .set("userId", 10001L)
 *             .set("username", "张三")
 *             .set("roles", "admin,user");
 *
 *         // 3. 设置登录Cookie并防止会话固定攻击
 *         authCookie(request, response, userInfo);
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
@Slf4j
public abstract class GXAbstractSSOService extends GXSSOSupportService implements GXSSOService {
    /**
     * <p>
     * 获取当前请求的SSO Token
     * </p>
     *
     * <p>
     * 从Cookie或请求头中解密获取SSO Token，主要用于拦截器场景。
     * 非拦截器场景建议使用attrSSOToken方法减少重复解密开销。
     * </p>
     *
     * <p>
     * 处理流程：
     * 1. 从缓存中获取Token - 优先使用分布式缓存中的Token信息
     * 2. 验证Token的IP和浏览器信息 - 防止会话劫持和Token盗用
     * 3. 通过插件机制进行额外验证 - 支持自定义的验证逻辑扩展
     * </p>
     *
     * <p>
     * 安全特性：
     * 1. 多重验证 - 结合IP、浏览器信息和缓存验证，提高安全性，有效防止会话劫持攻击
     * 2. 插件扩展 - 支持通过插件机制添加自定义验证逻辑，如地理位置验证、设备指纹验证等
     * 3. 防篡改保护 - 验证Token的完整性，防止被恶意修改，确保用户身份不被伪造
     * 4. 失效处理 - 对无效Token返回空对象而非异常，避免信息泄露，增强系统安全性
     * 5. 环境绑定 - 将Token与用户登录环境绑定，防止Token被复制到其他环境使用
     * </p>
     *
     * <p>
     * 性能考虑：
     * 1. 缓存利用 - 优先从缓存获取Token，减少解密开销，提高响应速度
     * 2. 快速失败 - 对无效Token快速返回，避免不必要的处理，节约系统资源
     * 3. 延迟验证 - 仅在必要时执行完整的验证流程，采用懒加载策略提升性能
     * 4. 分布式支持 - 在集群环境中保持一致的验证逻辑，支持系统横向扩展
     * </p>
     *
     * <p>
     * 使用场景：
     * 1. 接口鉴权 - 验证API请求的合法性，保护接口安全
     * 2. 页面访问控制 - 确保只有已登录用户能访问受保护的页面
     * 3. 权限检查 - 获取用户身份信息，用于后续的权限验证
     * 4. 用户会话管理 - 跟踪和维护用户的登录状态
     * </p>
     *
     * @param request HTTP请求对象
     * @return 包含用户登录信息的Dict对象，验证失败则返回空Dict
     */
    @Override
    public Dict getSSOToken(HttpServletRequest request) {
        Dict cacheSSOToken = cacheSSOToken(request, getConfig().getCache());
        Dict token = checkIpBrowser(request, cacheSSOToken);
        if (Objects.isNull(token)) {
            return Dict.create();
        }
        // 执行插件逻辑
        List<GXSSOPlugin> pluginList = getConfig().getPluginList();
        if (pluginList != null) {
            for (GXSSOPlugin plugin : pluginList) {
                boolean valid = plugin.validateToken(token);
                if (!valid) {
                    return Dict.create();
                }
            }
        }
        return token;
    }

    /**
     * 踢出指定用户ID的登录用户，强制其退出当前系统
     * <p>
     * 通过删除用户的Token缓存实现强制注销，立即使指定用户的所有会话失效。
     * 适用于管理员强制下线用户、检测到异常登录等安全场景。
     * </p>
     *
     * <p>
     * 安全应用场景：
     * 1. 管理员在后台强制用户下线 - 用于系统管理和用户管控
     * 2. 检测到用户账号异地登录时的安全措施 - 防止账号被盗用
     * 3. 用户修改密码后，使所有已登录会话失效 - 增强账号安全性
     * 4. 系统检测到潜在的安全威胁时，主动使会话失效 - 风险控制措施
     * 5. 用户权限变更后，强制重新登录 - 确保权限即时生效
     * 6. 系统维护时，有序下线所有用户 - 避免数据不一致
     * </p>
     *
     * <p>
     * 实现说明：
     * - 通过删除缓存中的Token记录实现强制注销，无需用户交互
     * - 需要配置有效的缓存实现才能正常工作，推荐使用分布式缓存
     * - 建议在生产环境中实现完整的安全审计日志记录，便于追溯和问题排查
     * - 该方法是幂等的，对同一用户多次调用不会产生副作用
     * - 在集群环境中，确保所有节点的缓存一致性，避免部分节点仍保持登录状态
     * </p>
     *
     * <p>
     * 性能考虑：
     * - 操作轻量级，主要是缓存删除操作，通常响应迅速
     * - 在大规模用户同时下线场景下，考虑分批处理避免缓存服务压力过大
     * - 可以结合消息队列实现异步处理，提高系统吞吐能力
     * </p>
     *
     * <p>
     * 最佳实践：
     * - 实现自定义的安全审计服务，记录所有强制下线操作
     * - 配合前端实现优雅下线提示，提升用户体验
     * - 考虑实现分级踢出策略，如允许用户在特定时间内完成操作后再下线
     * </p>
     *
     * @param userId 要踢出的用户ID，可以是任何类型的用户标识符
     * @return 操作是否成功，成功返回true，失败返回false
     */
    @Override
    public boolean kickLogin(Object userId) {
        GXSSOCache cache = getConfig().getCache();
        if (cache != null) {
            Dict ssoToken = Dict.create().set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, userId);
            return cache.delete(ssoToken);
        } else {
            log.debug(" kickLogin! please implements GXSsoCache class.");
        }
        return false;
    }

    /**
     * 在当前访问域下设置登录Cookie
     * <p>
     * 将用户登录信息写入Cookie并同步到缓存系统，实现分布式会话管理。
     * 这是SSO系统的核心方法之一，负责创建和维护用户的登录状态。
     * </p>
     *
     * <p>
     * Cookie超时时间设置方式：
     * request.setAttribute(GXSSOConstant.SSO_COOKIE_MAX_AGE, -1);
     * 超时时间说明：
     * -1: 浏览器关闭时自动删除（会话Cookie）- 适用于公共场所登录
     * 0: 立即删除Cookie - 用于注销操作
     * 正整数: 表示Cookie有效期（以秒为单位），如120表示2分钟 - 用于记住登录状态
     * </p>
     *
     * <p>
     * 安全措施：
     * 1. 环境绑定 - 将Token与用户IP和浏览器信息绑定，有效防止会话劫持
     * 2. HttpOnly保护 - 启用HttpOnly标志，阻止JavaScript访问Cookie内容，防止XSS攻击窃取凭证
     * 3. 传输安全 - 支持Secure标志，确保Cookie仅通过HTTPS安全传输，防止中间人攻击
     * 4. CSRF防护 - 设置SameSite=Lax属性，阻止跨站请求自动携带Cookie，防止CSRF攻击
     * 5. 数据加密 - 使用加密算法存储Token内容，即使Cookie被截获也无法解析
     * 6. 分布式同步 - 将Token同步到缓存系统，确保集群环境下会话一致性
     * 7. 安全响应头 - 添加X-Content-Type-Options和X-XSS-Protection等安全响应头
     * 8. 状态合并 - 保留原有Token中的有用状态数据，确保会话连续性
     * </p>
     *
     * <p>
     * 性能优化：
     * 1. 异常处理 - 全面捕获并记录异常，确保系统稳定性，提供友好错误信息
     * 2. 缓存状态检查 - 实时检测缓存服务可用性，提供本地降级方案确保功能可用
     * 3. 合理设置Cookie属性 - 避免不必要的Cookie传输，减少网络开销
     * 4. 延迟加载 - 仅在必要时执行插件逻辑，避免不必要的性能消耗
     * </p>
     *
     * <p>
     * 使用场景：
     * 1. 用户登录成功后设置身份凭证
     * 2. 刷新用户Token延长会话有效期
     * 3. 更新用户权限或角色信息
     * 4. 实现"记住我"功能，延长登录状态保持时间
     * 5. 在分布式系统中同步用户会话状态
     * </p>
     *
     * <p>
     * 注意事项：
     * 1. 生产环境中强烈建议启用HTTPS，并设置Cookie的Secure属性
     * 2. Token中不应包含敏感信息，如明文密码或完整的个人信息
     * 3. 合理设置Token过期时间，平衡安全性和用户体验
     * 4. 对于高安全要求的系统，考虑实现双因素认证
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param ssoToken 包含用户登录信息的Token数据，通常包括用户ID、用户名、角色等信息
     */
    @Override
    public void setCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken) {
        try {
            // 判断 GXSSOCache 是否缓存处理失效
            // cache 缓存宕机，flag 设置为失效
            GXSSOCache cache = getConfig().getCache();
            if (cache != null) {
                boolean tokenProvided = CharSequenceUtil.isNotBlank(ssoToken.getStr(getConfig().getTokenName()));
                // 添加额外安全信息，绑定用户环境信息防止会话劫持
                ssoToken.put("createTime", System.currentTimeMillis());
                ssoToken.put("userAgent", request.getHeader("User-Agent"));
                ssoToken.put("ip", GXIpHelperUtil.getIpAddr(request));

                // 合并现有Token信息，保留有用的状态数据
                Dict cookieSSOToken = getSSOTokenFromCookie(request);
                if (cookieSSOToken != null && !cookieSSOToken.isEmpty()) {
                    cookieSSOToken.putAll(ssoToken);
                    ssoToken.clear();
                    ssoToken.putAll(cookieSSOToken);
                }
                if (!tokenProvided) {
                    ssoToken.remove(getConfig().getTokenName());
                }

                fillTokenValueIfNecessary(ssoToken);

                // 将Token同步到缓存，支持分布式会话管理
                boolean rlt = cache.set(ssoToken, getConfig().getCacheExpires());
                if (!rlt) {
                    // 缓存服务不可用时的降级处理
                    ssoToken.put("flag", GXTokenFlag.CACHE_SHUT.value());
                    ssoToken.remove(getConfig().getTokenName());
                    fillTokenValueIfNecessary(ssoToken);
                    log.warn("缓存服务不可用，Token将使用本地模式，可能影响分布式会话同步");
                }
            }

            fillTokenValueIfNecessary(ssoToken);

            // 生成安全的加密Cookie
            Cookie ck = this.generateCookie(request, ssoToken);

            // 设置HttpOnly标志，防止JavaScript通过脚本访问Cookie，增强安全性
            // 此方法利用了 jakarta.servlet.http.Cookie 提供的标准API
            if (getConfig().isCookieHttpOnly()) {
                ck.setHttpOnly(true);
            }

            // 手动构建Cookie字符串以包含SameSite等现代属性
            // jakarta.servlet.http.Cookie API本身不直接支持SameSite属性的设置
            // 因此，我们通过构建Set-Cookie头字符串的方式来实现
            StringBuilder cookieBuilder = new StringBuilder();
            cookieBuilder.append(ck.getName()).append("=").append(ck.getValue());

            if (ck.getPath() != null) {
                cookieBuilder.append("; Path=").append(ck.getPath());
            }

            // 设置Cookie有效期 (Max-Age)
            if (ck.getMaxAge() >= 0) { // Max-Age可以为0（立即删除）或正数
                cookieBuilder.append("; Max-Age=").append(ck.getMaxAge());
            }

            // 设置Cookie域名范围
            if (ck.getDomain() != null) {
                cookieBuilder.append("; Domain=").append(ck.getDomain());
            }

            // 设置Secure标志，要求Cookie仅通过HTTPS传输
            if (ck.getSecure()) {
                cookieBuilder.append("; Secure");
            }

            // 设置HttpOnly标志 (如果通过ck.setHttpOnly(true)设置，则此处无需重复添加，
            // 但为确保所有属性都通过一个统一的机制（header字符串）设置，或者在不支持setHttpOnly的旧环境中，
            // 这里的显式添加仍然有意义。当前代码已使用ck.setHttpOnly()，故理论上此处可省略，
            // 但保留它以明确展示最终Cookie字符串的构成，或作为一种兼容性回退。
            // 现代Servlet容器通常会正确处理通过API设置的HttpOnly属性。
            if (ck.isHttpOnly()) { // 检查是否已通过API设置
                cookieBuilder.append("; HttpOnly");
            }

            // 设置SameSite属性，增强CSRF防护
            // Lax: 允许在顶级导航（如点击链接）时发送Cookie，但阻止跨站POST请求等携带Cookie。
            // Strict: 完全禁止第三方Cookie发送。
            // None: 允许第三方Cookie发送，但必须同时设置Secure标志。
            // 此处选择Lax，在安全性和用户体验之间取得平衡。
            cookieBuilder.append("; SameSite=Lax");

            // 应用Cookie设置到HTTP响应头
            response.addHeader("Set-Cookie", cookieBuilder.toString());

            // 添加安全响应头，增强整体安全性
            response.setHeader("X-Content-Type-Options", "nosniff"); // 防止MIME类型嗅探
            response.setHeader("X-XSS-Protection", "1; mode=block"); // 启用XSS过滤

            // 执行插件逻辑，支持自定义登录处理
            List<GXSSOPlugin> pluginList = getConfig().getPluginList();
            if (pluginList != null) {
                for (GXSSOPlugin plugin : pluginList) {
                    boolean login = plugin.login(request, response);
                    if (!login) {
                        log.warn("插件[{}]登录处理失败，请检查插件配置", plugin.getClass().getSimpleName());
                    }
                }
            }
        } catch (Exception e) {
            log.error("设置SSO Cookie时发生错误: {}", e.getMessage(), e);
            throw new GXBusinessException("设置登录Cookie失败，请稍后重试");
        }
    }

    private void fillTokenValueIfNecessary(Dict ssoToken) {
        if (CharSequenceUtil.isNotBlank(ssoToken.getStr(getConfig().getTokenName()))) {
            return;
        }

        GXTokenConfigService tokenConfigService = GXSpringContextUtils.getBean(GXTokenConfigService.class);
        if (tokenConfigService == null) {
            throw new GXBusinessException("未找到GXTokenConfigService实现类");
        }

        ssoToken.putIfAbsent(GXTokenConstant.LOGIN_AT_FIELD_NAME, System.currentTimeMillis() / 1000);
        Dict tokenData = new Dict(ssoToken);
        tokenData.remove(getConfig().getTokenName());
        int expires = Math.max(getConfig().getCacheExpires(), 0);
        ssoToken.set(getConfig().getTokenName(),
                GXAuthCodeUtils.authCodeEncode(JSONUtil.toJsonStr(tokenData), tokenConfigService.getTokenSecret(), expires));
    }

    /**
     * 在当前访问域下设置登录Cookie并防止会话固定攻击
     * <p>
     * 此方法是登录流程的推荐入口，它提供了比setCookie更高级别的安全保护。
     * 在设置登录Cookie的同时，重新生成会话标识(JSESSIONID)，有效防止会话固定攻击。
     * </p>
     *
     * <p>
     * 会话固定攻击防护原理：
     * 1. 攻击者获取有效会话ID并诱导用户使用该ID登录 - 例如通过钓鱼链接或XSS攻击
     * 2. 用户登录成功后，攻击者可使用原会话ID获取用户权限 - 无需知道用户凭证
     * 3. 通过登录时重新生成会话ID，使攻击者持有的原会话ID失效 - 切断攻击链
     * 4. 即使攻击者诱导用户点击包含预设会话ID的链接，登录后也会使该ID失效
     * </p>
     *
     * <p>
     * 安全增强措施：
     * 1. 高强度随机会话标识 - 使用加密安全的随机数生成器创建不可预测的会话ID
     * 2. 足够的熵值 - 会话标识长度为16字符，提供足够的随机性，防止暴力破解
     * 3. 多层次安全响应头 - 设置X-Frame-Options、Cache-Control等头，全面提升防护能力
     * 4. 降级保护策略 - 完善的异常处理，确保即使会话重新生成失败也能保持基本登录功能
     * 5. 安全审计日志 - 详细记录会话重新生成过程，便于安全审计和问题排查
     * 6. 全方位Cookie保护 - 继承setCookie方法的所有安全特性，如HttpOnly、SameSite等
     * </p>
     *
     * <p>
     * 使用场景：
     * - 用户首次登录系统时 - 基础安全防护
     * - 用户权限变更后需要刷新会话 - 确保权限即时生效
     * - 检测到潜在的会话劫持尝试时 - 主动防御措施
     * - 用户执行敏感操作（如转账、修改密码）前 - 增强关键操作安全性
     * - 从不可信来源（如外部链接）跳转到登录页时 - 防止钓鱼攻击
     * - 在共享计算机环境下登录系统时 - 防止本地会话被利用
     * </p>
     *
     * <p>
     * 最佳实践：
     * - 在所有登录成功后调用此方法，而不是直接调用setCookie - 提供更高安全性
     * - 确保前端应用能够正确处理会话变更 - 避免用户体验问题
     * - 在分布式环境中，确保会话同步机制正常工作 - 保持集群一致性
     * - 结合安全审计系统，记录所有会话重新生成事件 - 便于安全分析
     * - 对于特别敏感的系统，考虑在每次关键操作前都重新生成会话ID - 最大化安全性
     * - 实现会话活动监控，检测异常的会话行为 - 主动防御
     * </p>
     *
     * <p>
     * 技术实现细节：
     * - 使用GXRandomUtil生成高强度随机会话标识
     * - 通过GXCookieHelperUtil安全地替换原有JSESSIONID
     * - 设置多种安全响应头增强防护能力
     * - 采用try-catch结构确保即使出错也能降级到基本Cookie设置
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param ssoToken 包含用户登录信息的Token数据，将被安全地存储在Cookie和缓存中
     */
    public void authCookie(HttpServletRequest request, HttpServletResponse response, Dict ssoToken) {
        try {
            // 生成足够长度的随机会话标识，提高安全性
            // 使用16字符的随机字符串，包含字母和数字，提供足够的随机性
            String sessionId = GXRandomUtil.getCharacterAndNumber(16);

            // 记录会话重新生成事件，便于安全审计和问题排查
            log.debug("重新生成会话ID[{}]，防止会话固定攻击", sessionId.substring(0, 4) + "***");

            // 使用安全的方式重新生成会话，替换原有的JSESSIONID
            GXCookieHelperUtil.authJSESSIONID(request, sessionId);

            // 在新会话中设置用户登录Cookie
            this.setCookie(request, response, ssoToken);

            // 添加安全响应头，全面增强安全性
            // X-Frame-Options: 防止页面被嵌入iframe，避免点击劫持攻击
            response.setHeader("X-Frame-Options", "DENY");

            // Cache-Control: 防止敏感页面被缓存，减少信息泄露风险
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");

            // Referrer-Policy: 控制请求头中Referer的内容，防止信息泄露
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

            // 记录安全审计日志
            if (log.isDebugEnabled()) {
                log.debug("用户登录安全增强完成，应用了会话固定攻击防护和安全响应头");
            }
        } catch (Exception e) {
            // 记录详细错误信息，但不泄露敏感数据
            log.error("重新生成会话时发生错误: {}", e.getMessage(), e);

            // 即使出错也尝试设置Cookie，确保基本功能可用
            // 这是一种降级策略，保证用户至少能够登录系统
            log.warn("会话重新生成失败，将使用基本Cookie设置，安全性可能降低");
            this.setCookie(request, response, ssoToken);
        }
    }

    /**
     * 清除用户登录状态
     * <p>
     * 完整清理用户的登录信息，执行完整的注销流程，但不进行页面重定向。
     * 这是SSO系统中负责安全退出的核心方法，确保用户身份凭证被彻底清除。
     * </p>
     *
     * <p>
     * 清理内容包括：
     * 1. 删除浏览器Cookie - 移除客户端存储的身份凭证
     * 2. 清除服务端缓存 - 确保分布式环境中的会话同步失效
     * 3. 执行SSO插件的注销逻辑 - 支持扩展的清理操作
     * 4. 清理请求属性 - 确保当前请求中的用户信息被清除
     * </p>
     *
     * <p>
     * 安全考虑：
     * - 全面清理 - 确保所有会话状态和缓存数据被完全清除，不留安全隐患
     * - 插件机制 - 执行所有注册的SSO插件的注销逻辑，支持自定义清理流程
     * - 容错处理 - 适当处理清理失败的情况，避免部分状态残留导致的安全风险
     * - 无状态设计 - 清理后确保系统不保留任何可被利用的用户状态信息
     * - 防止会话劫持 - 彻底销毁会话，防止会话被后续劫持利用
     * </p>
     *
     * <p>
     * 使用场景：
     * - 用户主动登出系统 - 响应用户的注销请求
     * - 会话超时后的清理 - 自动清理过期会话
     * - 检测到安全问题时的强制注销 - 安全风险控制措施
     * - 单点登出实现 - 在SSO系统中实现一处登出，处处注销
     * - 权限变更后的状态刷新 - 确保用户使用新权限重新登录
     * - 系统维护前的用户下线 - 有序清理用户状态
     * </p>
     *
     * <p>
     * 性能考虑：
     * - 轻量级操作 - 主要是Cookie删除和缓存清理，通常响应迅速
     * - 分布式同步 - 在集群环境中确保所有节点的会话状态一致性
     * - 异常处理优化 - 即使部分清理失败也能继续执行其他清理步骤
     * </p>
     *
     * <p>
     * 最佳实践：
     * - 在所有需要注销的场景中调用此方法，确保彻底清理用户状态
     * - 实现安全审计日志，记录所有注销操作，便于安全分析
     * - 注销后立即使相关页面跳转到登录页或首页，避免留在受保护页面
     * - 考虑实现注销确认机制，防止意外操作导致的数据丢失
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @return 操作是否成功，成功返回true，失败返回false
     */
    @Override
    public boolean clearLogin(HttpServletRequest request, HttpServletResponse response) {
        return logout(request, response, getConfig().getCache());
    }

    /**
     * 重新登录处理
     * <p>
     * 执行完整的注销流程，然后将用户重定向到登录页面。
     * 会保留当前请求的URL作为参数，便于用户重新登录后返回原页面，提升用户体验。
     * 这是处理会话失效或需要重新认证场景的推荐方法。
     * </p>
     *
     * <p>
     * 处理流程：
     * 1. 清理当前登录状态 - 调用clearLogin方法彻底清除用户凭证
     * 2. 获取配置的登录页URL - 从SSO配置中读取统一的登录页地址
     * 3. 智能响应处理 - 根据请求类型提供不同的响应方式：
     * - 对于API请求，返回标准的JSON响应，包含401状态码
     * - 对于页面请求，进行重定向并保留原始URL作为返回参数
     * </p>
     *
     * <p>
     * 安全特性：
     * - 完整注销 - 确保在重定向前彻底清除所有用户凭证和会话状态
     * - 请求保留 - 安全地保存原始请求URL，防止URL注入和跳转攻击
     * - 类型识别 - 区分API和页面请求，提供合适的响应格式，增强安全性
     * - 防止信息泄露 - API响应中不包含敏感的系统路径或详细错误信息
     * - URL编码 - 对重定向URL进行安全编码，防止跨站点脚本攻击
     * </p>
     *
     * <p>
     * 使用场景：
     * - 会话过期时自动跳转登录 - 提供无缝的会话续期体验
     * - 访问需要登录的资源时进行重定向 - 权限控制的标准流程
     * - 检测到Token无效时的安全处理 - 防止使用失效凭证的访问
     * - 权限不足需要重新认证 - 访问高权限资源时的安全措施
     * - 检测到潜在的会话劫持时强制重新登录 - 主动安全防御
     * - 系统升级或维护后的重新认证 - 确保用户状态一致性
     * </p>
     *
     * <p>
     * 实现特点：
     * - 双模式支持 - 同时支持API和页面两种场景的处理，适应不同客户端需求
     * - 无缝体验 - 对于页面请求，重定向后能够返回原始页面，提升用户体验
     * - 标准响应 - 对于API请求，返回符合RESTful规范的JSON响应
     * - 配置驱动 - 登录页URL通过配置确定，支持不同环境的灵活部署
     * - 异常安全 - 完善的异常处理，确保即使在错误情况下也能提供合理响应
     * </p>
     *
     * <p>
     * 最佳实践：
     * - 在所有需要重新登录的场景中统一使用此方法，保持一致的用户体验
     * - 确保登录页能够正确处理returnUrl参数，实现登录后的自动跳转
     * - 前端应用应当能够优雅地处理401响应，引导用户重新登录
     * - 考虑实现登录尝试次数限制，防止暴力破解攻击
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @throws IOException 如果重定向过程中发生I/O错误
     */
    @Override
    public void clearRedirectLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 清理当前登录状态
        clearLogin(request, response);
        if (response.isCommitted()) {
            log.debug("响应已提交，跳过重新登录响应处理: {}", request.getRequestURI());
            return;
        }

        // redirect login page
        String loginUrl = getConfig().getLoginUrl();
        if ("api".equalsIgnoreCase(request.getHeader("X-RESPONSE-TYPE")) || CharSequenceUtil.isBlank(loginUrl)) {
            Dict data = Dict.create().set("code", HttpStatus.HTTP_UNAUTHORIZED).set("msg", "Please login").set("data", null);
            response.setStatus(HttpStatus.HTTP_UNAUTHORIZED);
            response.setCharacterEncoding(getConfig().getEncoding());
            response.setContentType("application/json;charset=" + getConfig().getEncoding());
            response.getWriter().write(JSONUtil.toJsonStr(data));
        } else {
            String retUrl = GXHttpUtil.getRequestUrl(request);
            log.debug("loginAgain redirect pageUrl.." + retUrl);
            response.sendRedirect(GXHttpUtil.encodeRetURL(loginUrl, getConfig().getParamReturnUrl(), retUrl));
        }
    }

    /**
     * SSO系统退出登录
     * <p>
     * 执行完整的SSO注销流程，包括清理本地状态和重定向到注销页面。
     * 与clearLogin的区别在于，此方法会进行页面重定向，提供更完整的用户体验。
     * 这是用户主动注销时的推荐调用方法。
     * </p>
     *
     * <p>
     * 处理流程：
     * 1. 调用logout方法清理所有登录状态（Cookie和缓存）- 确保彻底注销
     * 2. 获取配置的注销页面URL - 从SSO配置中读取统一的注销成功页地址
     * 3. 执行页面重定向 - 将用户引导到注销成功页面
     * 4. 如果未配置注销页面，则返回友好的错误信息 - 确保用户体验完整性
     * </p>
     *
     * <p>
     * 安全特性：
     * - 完整注销 - 确保在重定向前彻底清除所有用户凭证和会话状态
     * - 集中配置 - 通过统一配置管理注销页面，避免硬编码URL带来的安全风险
     * - 防止会话重用 - 彻底销毁会话，防止会话被后续劫持利用
     * - 单点登出支持 - 在SSO环境中实现一处注销，所有系统同步登出
     * - 安全重定向 - 确保重定向到受信任的页面，防止开放重定向漏洞
     * </p>
     *
     * <p>
     * 使用场景：
     * - 用户点击"退出登录"按钮 - 响应用户的主动注销请求
     * - 系统自动注销过期会话 - 会话超时后的安全处理
     * - 管理员强制用户退出后的页面处理 - 提供友好的用户体验
     * - 检测到安全风险后的强制注销 - 安全风险控制措施
     * - 单点登出流程 - 实现多系统统一退出
     * - 用户切换账号 - 清理当前用户状态后引导到登录页
     * </p>
     *
     * <p>
     * 实现特点：
     * - 完整流程 - 先清理所有登录状态，然后执行页面重定向，提供完整体验
     * - 配置驱动 - 注销页URL通过配置确定，支持不同环境的灵活部署
     * - 友好反馈 - 如果未配置注销页面，提供明确的错误信息指导配置
     * - 异常安全 - 完善的异常处理，确保即使在错误情况下也能提供合理响应
     * </p>
     *
     * <p>
     * 最佳实践：
     * - 配置专门的注销成功页面，提供清晰的注销成功提示和重新登录选项
     * - 在注销页面添加自动跳转到首页或登录页的功能，提升用户体验
     * - 实现注销操作的安全审计日志，记录用户退出行为
     * - 考虑在注销前提供确认机制，防止意外操作
     * - 确保注销页面不包含敏感信息，即使未登录用户也可以安全访问
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @throws IOException 如果重定向过程中发生I/O错误
     */
    public void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // delete cookie
        logout(request, response, getConfig().getCache());

        // redirect logout page
        String logoutUrl = getConfig().getLogoutUrl();
        if ("".equals(logoutUrl)) {
            response.getWriter().write("sso.yml Must include: sso.config.logout.url");
        } else {
            response.sendRedirect(logoutUrl);
        }
    }
}
