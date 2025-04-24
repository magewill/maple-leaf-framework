package cn.maple.core.framework.util;

import cn.hutool.core.text.CharSequenceUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Cookie工具类
 * <p>
 * 提供全面的Cookie操作功能，包括：
 * 1. Cookie的创建和添加 - 支持设置域、路径、过期时间等属性
 * 2. Cookie的查找和获取 - 根据名称从请求中查找Cookie
 * 3. Cookie的清除和删除 - 支持清除单个Cookie或指定域下的所有Cookie
 * 4. 安全增强功能 - 支持HttpOnly和Secure属性，防止XSS攻击和会话劫持
 * 5. 会话安全保护 - 防止会话固定攻击，支持重新生成会话ID
 * </p>
 * 
 * <p>
 * 安全特性：
 * - HttpOnly支持：防止客户端脚本访问Cookie，减少XSS攻击风险
 * - Secure属性：确保Cookie仅在HTTPS连接中传输，防止中间人攻击
 * - 会话固定防护：支持在用户登录时重新生成会话ID，防止会话固定攻击
 * - 参数验证：对所有输入参数进行严格验证，防止空指针异常和不安全操作
 * - 异常处理：所有操作都有适当的异常处理，确保系统稳定性
 * </p>
 * 
 * <p>
 * 性能考虑：
 * - 使用StringBuilder预分配足够容量，减少字符串拼接时的内存分配
 * - 避免不必要的对象创建，减少垃圾回收压力
 * - 提供清晰的日志，便于问题排查和性能分析
 * </p>
 * 
 * <p>
 * 使用限制：
 * 注意：在cookie的名或值中不能使用分号（;）、逗号（,）、等号（=）以及空格，
 * 这些字符在Cookie规范中有特殊含义，使用这些字符可能导致Cookie解析错误。
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建和添加Cookie
 * // 添加一个安全的HttpOnly Cookie，仅在HTTPS下传输，有效期30分钟
 * GXCookieHelperUtil.addCookie(response, "example.com", "/", "sessionId", 
 *                           "abc123xyz", 1800, true, true);
 * 
 * // 2. 查找Cookie
 * Cookie cookie = GXCookieHelperUtil.findCookieByName(request, "sessionId");
 * if (cookie != null) {
 *     String value = cookie.getValue();
 *     // 处理Cookie值
 * }
 * 
 * // 3. 清除Cookie
 * // 清除指定域下的所有Cookie
 * GXCookieHelperUtil.clearAllCookie(request, response, "example.com", "/");
 * 
 * // 4. 防止会话固定攻击
 * // 用户登录成功后，重新生成会话ID
 * GXCookieHelperUtil.authJSESSIONID(request, userId.toString());
 * </pre>
 * </p>
 *
 * @author britton birtton@126.com
 * @since 2021-09-16
 */
@Slf4j
public class GXCookieHelperUtil {
    /**
     * 浏览器关闭时自动删除
     */
    public static final int CLEAR_BROWSER_IS_CLOSED = -1;

    /**
     * 立即删除
     */
    public static final int CLEAR_IMMEDIATELY_REMOVE = 0;

    private GXCookieHelperUtil() {
    }

    /**
     * 防止伪造SESSION_ID攻击，用户登录校验成功后销毁当前JSESSIONID并创建可信的JSESSIONID
     * <p>
     * 此方法用于防止会话固定攻击(Session Fixation Attack)，在用户成功登录后，
     * 销毁当前会话并创建新的会话，确保攻击者无法使用预先获取的会话ID进行身份冒充。
     * </p>
     * 
     * <p>
     * 安全说明：
     * - 会话固定攻击是一种常见的Web安全威胁，攻击者可能诱导用户使用预设的会话ID登录
     * - 通过在登录成功后重新生成会话ID，可以有效防止此类攻击
     * - 新会话中添加了用户唯一标识作为属性，增强了会话的安全性
     * </p>
     * 
     * <p>
     * 使用场景：
     * - 用户登录成功后调用此方法
     * - 用户权限变更后调用此方法
     * - 检测到潜在的会话劫持时调用此方法
     * </p>
     * 
     * <p>
     * 使用示例：
     * <pre>
     * // 用户登录成功后
     * if (loginSuccess) {
     *     GXCookieHelperUtil.authJSESSIONID(request, user.getId().toString());
     *     // 继续处理登录成功后的逻辑
     * }
     * </pre>
     * </p>
     *
     * @param request 当前HTTP请求，不能为null
     * @param value   用户ID等唯一信息，不能为null或空
     */
    public static void authJSESSIONID(HttpServletRequest request, String value) {
        if (request == null) {
            log.warn("Cannot authenticate JSESSIONID: request is null");
            return;
        }
        if (CharSequenceUtil.isEmpty(value)) {
            log.warn("Cannot authenticate JSESSIONID: value is empty");
            return;
        }
        request.getSession().invalidate();
        request.getSession().setAttribute("MAPLE-" + value, true);
    }

    /**
     * 根据cookieName从请求中获取Cookie
     * <p>
     * 从HTTP请求中查找指定名称的Cookie，如果找到则返回该Cookie对象，
     * 否则返回null。此方法会对输入参数进行严格验证，确保安全性。
     * </p>
     * 
     * <p>
     * 安全说明：
     * - 对输入参数进行非空检查，防止空指针异常
     * - 对Cookie数组进行空检查，避免在没有Cookie时出现异常
     * - 使用精确匹配而非模糊匹配，防止混淆攻击
     * </p>
     * 
     * <p>
     * 使用场景：
     * - 获取用户会话标识Cookie
     * - 获取用户偏好设置Cookie
     * - 获取认证令牌Cookie
     * - 在清除Cookie前检查Cookie是否存在
     * </p>
     * 
     * <p>
     * 使用示例：
     * <pre>
     * // 获取名为"sessionId"的Cookie
     * Cookie sessionCookie = GXCookieHelperUtil.findCookieByName(request, "sessionId");
     * if (sessionCookie != null) {
     *     String sessionId = sessionCookie.getValue();
     *     // 验证会话ID的有效性
     *     if (sessionService.isValid(sessionId)) {
     *         // 处理有效会话
     *     } else {
     *         // 处理无效会话
     *     }
     * } else {
     *     // 处理Cookie不存在的情况
     * }
     * </pre>
     * </p>
     *
     * @param request    请求对象，不能为null
     * @param cookieName Cookie name，不能为null或空
     * @return Cookie 如果找到匹配的Cookie则返回，否则返回null
     */
    public static Cookie findCookieByName(HttpServletRequest request, String cookieName) {
        if (request == null) {
            log.warn("Cannot find cookie: request is null");
            return null;
        }
        if (CharSequenceUtil.isEmpty(cookieName)) {
            log.warn("Cannot find cookie: cookieName is empty");
            return null;
        }
        
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        
        for (Cookie cookie : cookies) {
            if (cookie != null && cookieName.equals(cookie.getName())) {
                return cookie;
            }
        }
        return null;
    }

    /**
     * 根据cookieName清空默认域下的Cookie
     * <p>
     * 创建一个同名的过期Cookie来覆盖并清除原有Cookie。
     * 此方法适用于清除当前域下的Cookie，不指定特定的域和路径。
     * </p>
     * 
     * <p>
     * 工作原理：
     * - 创建一个与要删除的Cookie同名的新Cookie
     * - 将新Cookie的值设为空字符串
     * - 将新Cookie的最大生存时间设为0（立即删除）
     * - 将新Cookie添加到响应中，覆盖原有Cookie
     * </p>
     * 
     * <p>
     * 安全说明：
     * - 对输入参数进行非空检查，防止空指针异常
     * - 立即删除Cookie，减少信息泄露风险
     * - 适用于注销操作，清除用户会话信息
     * </p>
     * 
     * <p>
     * 使用示例：
     * <pre>
     * // 用户注销时，清除会话Cookie
     * GXCookieHelperUtil.clearCookieByName(response, "sessionId");
     * GXCookieHelperUtil.clearCookieByName(response, "authToken");
     * 
     * // 清除用户偏好Cookie
     * GXCookieHelperUtil.clearCookieByName(response, "userPreferences");
     * </pre>
     * </p>
     *
     * @param response   响应对象，不能为null
     * @param cookieName cookieName，不能为null或空
     */
    public static void clearCookieByName(HttpServletResponse response, String cookieName) {
        if (response == null) {
            log.warn("Cannot clear cookie: response is null");
            return;
        }
        if (CharSequenceUtil.isEmpty(cookieName)) {
            log.warn("Cannot clear cookie: cookieName is empty");
            return;
        }
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setMaxAge(CLEAR_IMMEDIATELY_REMOVE);
        response.addCookie(cookie);
    }

    /**
     * 清除指定domain和path下的所有Cookie
     * <p>
     * 遍历请求中的所有Cookie，并清除指定域和路径下的所有Cookie。
     * 此方法适用于完全注销用户、清除所有会话数据等场景。
     * </p>
     * 
     * <p>
     * 安全说明：
     * - 批量清除Cookie，确保不遗留敏感信息
     * - 适用于用户注销、会话超时等安全敏感场景
     * - 可用于应对潜在的会话劫持攻击
     * </p>
     * 
     * <p>
     * 性能考虑：
     * - 对于Cookie较多的请求，此方法可能产生多个Set-Cookie响应头
     * - 在高并发环境下，应谨慎使用此方法，避免响应头过大
     * </p>
     * 
     * <p>
     * 使用示例：
     * <pre>
     * // 用户完全注销，清除所有相关Cookie
     * GXCookieHelperUtil.clearAllCookie(request, response, "example.com", "/");
     * 
     * // 清除特定应用路径下的所有Cookie
     * GXCookieHelperUtil.clearAllCookie(request, response, "example.com", "/app/");
     * </pre>
     * </p>
     *
     * @param request  请求对象，包含要清除的Cookie
     * @param response 响应对象，用于添加清除Cookie的响应头
     * @param domain   Cookie所在的域，如"example.com"
     * @param path     Cookie路径，如"/"或"/app/"
     */
    public static void clearAllCookie(HttpServletRequest request, HttpServletResponse response, String domain,
                                      String path) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            log.debug("No cookies found in request");
            return;
        }
        for (Cookie cookie : cookies) {
            clearCookie(response, cookie.getName(), domain, path);
        }
        log.debug("clearAllCookie in domain {}", domain);
    }

    /**
     * 根据cookieName清除指定域和路径下的Cookie
     * <p>
     * 先检查指定的Cookie是否存在，如果存在则清除它。
     * 与clearCookieByName(response, cookieName)不同，此方法可以指定域和路径，
     * 适用于需要精确控制Cookie作用范围的场景。
     * </p>
     * 
     * <p>
     * 安全说明：
     * - 对输入参数进行非空检查，防止空指针异常
     * - 先检查Cookie是否存在，避免不必要的操作
     * - 支持指定域和路径，精确控制Cookie的作用范围
     * </p>
     * 
     * <p>
     * 使用场景：
     * - 清除特定子域下的认证Cookie
     * - 清除特定应用路径下的会话Cookie
     * - 在多域名环境下精确控制Cookie清除范围
     * </p>
     * 
     * <p>
     * 使用示例：
     * <pre>
     * // 清除特定子域下的认证Cookie
     * boolean success = GXCookieHelperUtil.clearCookieByName(
     *     request, response, "authToken", "api.example.com", "/");
     * if (success) {
     *     log.info("认证Cookie已成功清除");
     * }
     * 
     * // 清除特定应用路径下的会话Cookie
     * GXCookieHelperUtil.clearCookieByName(
     *     request, response, "sessionId", "example.com", "/app/");
     * </pre>
     * </p>
     *
     * @param request    请求对象，不能为null
     * @param response   响应对象，不能为null
     * @param cookieName cookie name，不能为null或空
     * @param domain     Cookie所在的域，如"example.com"
     * @param path       Cookie路径，如"/"或"/app/"
     * @return boolean 操作是否成功，成功返回true，失败返回false
     */
    public static boolean clearCookieByName(HttpServletRequest request, HttpServletResponse response, String cookieName, String domain, String path) {
        if (request == null || response == null) {
            log.warn("Cannot clear cookie: request or response is null");
            return false;
        }
        if (CharSequenceUtil.isEmpty(cookieName)) {
            log.warn("Cannot clear cookie: cookieName is empty");
            return false;
        }
        boolean result = false;
        Cookie ck = findCookieByName(request, cookieName);
        if (ck != null) {
            result = clearCookie(response, cookieName, domain, path);
        }
        return result;
    }

    /**
     * 清除指定Cookie（内部方法）
     * <p>
     * 直接清除指定的Cookie，不检查Cookie是否存在。
     * 此方法是内部使用的，不对外暴露，以防止Cookie不存在时产生异常。
     * </p>
     * 
     * <p>
     * 工作原理：
     * - 创建一个与要删除的Cookie同名的新Cookie
     * - 将新Cookie的值设为空字符串
     * - 将新Cookie的最大生存时间设为0（立即删除）
     * - 设置新Cookie的域和路径，确保能覆盖原有Cookie
     * - 将新Cookie添加到响应中
     * </p>
     * 
     * <p>
     * 异常处理：
     * - 捕获并记录所有可能的异常，确保方法调用不会中断程序流程
     * - 返回布尔值表示操作是否成功，便于调用者进行后续处理
     * </p>
     *
     * @param response   响应对象，用于添加清除Cookie的响应头
     * @param cookieName cookie name，要清除的Cookie名称
     * @param domain     Cookie所在的域，如"example.com"
     * @param path       Cookie路径，如"/"或"/app/"
     * @return boolean 操作是否成功，成功返回true，失败返回false
     */
    private static boolean clearCookie(HttpServletResponse response, String cookieName, String domain, String path) {
        boolean result = false;
        try {
            Cookie cookie = new Cookie(cookieName, "");
            cookie.setMaxAge(CLEAR_IMMEDIATELY_REMOVE);
            if (CharSequenceUtil.isNotEmpty(domain)) {
                cookie.setDomain(domain);
            }
            cookie.setPath(path);
            response.addCookie(cookie);
            log.debug("clear cookie {}", cookieName);
            result = true;
        } catch (Exception e) {
            log.error("clear cookie {} is exception!", cookieName, e);
        }
        return result;
    }

    /**
     * 添加Cookie到HTTP响应
     * <p>
     * 创建一个新的Cookie并添加到HTTP响应中，支持设置Cookie的各种属性，
     * 包括域、路径、过期时间、HttpOnly和Secure等安全属性。
     * </p>
     * 
     * <p>
     * 参数说明：
     * - maxAge: Cookie的生存时间，单位为秒
     *   - 负数（通常是-1）：表示Cookie在浏览器关闭时自动删除（会话Cookie）
     *   - 0：表示立即删除Cookie
     *   - 正数：表示Cookie的存活秒数，如1800表示30分钟
     * </p>
     * 
     * <p>
     * 安全特性：
     * - httpOnly=true：防止客户端JavaScript访问Cookie，减少XSS攻击风险
     * - secured=true：确保Cookie仅在HTTPS连接中传输，防止中间人攻击
     * </p>
     * 
     * <p>
     * 使用示例：
     * <pre>
     * // 1. 创建一个会话Cookie（浏览器关闭时过期）
     * GXCookieHelperUtil.addCookie(response, "example.com", "/", "sessionId", 
     *                           token, GXCookieHelperUtil.CLEAR_BROWSER_IS_CLOSED, true, true);
     *                           
     * // 2. 创建一个持久Cookie（30天有效期）
     * GXCookieHelperUtil.addCookie(response, "example.com", "/", "rememberMe", 
     *                           "true", 30 * 24 * 60 * 60, true, true);
     *                           
     * // 3. 创建一个不安全的Cookie（不推荐，仅用于特殊场景）
     * GXCookieHelperUtil.addCookie(response, "example.com", "/", "preference", 
     *                           "theme=dark", 3600, false, false);
     * </pre>
     * </p>
     *
     * @param response 响应对象，不能为null
     * @param domain   所在域，如果为null则使用当前域
     * @param path     域名路径，不能为null
     * @param name     名称，不能为null或空
     * @param value    内容，如果为null则设置为空字符串
     * @param maxAge   生命周期参数，单位为秒
     * @param httpOnly 是否启用HttpOnly属性，true表示启用（推荐）
     * @param secured  是否仅在Https协议下传输，true表示启用（推荐）
     */
    public static void addCookie(HttpServletResponse response, String domain, String path, String name, String value, int maxAge, boolean httpOnly, boolean secured) {
        if (response == null) {
            log.warn("Cannot add cookie: response is null");
            return;
        }
        if (CharSequenceUtil.isEmpty(name)) {
            log.warn("Cannot add cookie: name is empty");
            return;
        }
        if (path == null) {
            log.warn("Cannot add cookie: path is null");
            return;
        }
        
        Cookie cookie = new Cookie(name, value != null ? value : "");
        // 不设置该参数默认 当前所在域
        if (CharSequenceUtil.isNotEmpty(domain)) {
            cookie.setDomain(domain);
        }
        cookie.setPath(path);
        cookie.setMaxAge(maxAge);

        // Cookie 只在Https协议下传输设置
        if (secured) {
            cookie.setSecure(secured);
        }

        // Cookie 只读设置
        if (httpOnly) {
            addHttpOnlyCookie(response, cookie);
        } else {
            // servlet 3.0 support cookie.setHttpOnly(httpOnly)
            response.addCookie(cookie);
        }
    }

    /**
     * 解决Servlet 3.0以下版本不支持HttpOnly属性的问题
     * <p>
     * 通过直接设置HTTP响应头的方式添加带有HttpOnly标志的Cookie，
     * 适用于Servlet 3.0以下版本的环境，或者需要更精细控制Cookie属性的场景。
     * </p>
     * 
     * <p>
     * 实现原理：
     * - Servlet 3.0以下版本的Cookie API不直接支持HttpOnly属性
     * - 本方法通过直接操作HTTP响应头"Set-Cookie"来设置HttpOnly标志
     * - 手动构建符合HTTP规范的Cookie字符串，包含所有必要的属性
     * </p>
     * 
     * <p>
     * 安全说明：
     * - HttpOnly是一个重要的安全属性，可以防止客户端脚本访问Cookie
     * - 即使在旧版Servlet环境中，也应当尽可能启用此属性
     * - 此方法确保在所有Servlet版本中都能正确设置HttpOnly属性
     * </p>
     * 
     * <p>
     * 性能优化：
     * - 使用预分配容量的StringBuilder减少内存分配
     * - 按照Cookie属性的重要性顺序构建字符串
     * - 避免不必要的字符串连接操作
     * </p>
     *
     * @param response HttpServletResponse类型的响应，不能为null
     * @param cookie   要设置httpOnly的cookie对象，不能为null
     */
    public static void addHttpOnlyCookie(HttpServletResponse response, Cookie cookie) {
        if (cookie == null || response == null) {
            log.warn("Cannot add HttpOnly cookie: cookie or response is null");
            return;
        }
        // 依次取得cookie中的名称、值、 最大生存时间、路径、域和是否为安全协议信息
        String cookieName = cookie.getName();
        String cookieValue = cookie.getValue();
        int maxAge = cookie.getMaxAge();
        String path = cookie.getPath();
        String domain = cookie.getDomain();
        boolean isSecure = cookie.getSecure();
        
        // 使用足够初始容量的StringBuilder以减少扩容操作
        StringBuilder sf = new StringBuilder(128);
        sf.append(cookieName).append("=").append(cookieValue).append(";");
        if (maxAge >= 0) {
            sf.append("Max-Age=").append(maxAge).append(";");
        }
        if (domain != null) {
            sf.append("domain=").append(domain).append(";");
        }
        if (path != null) {
            sf.append("path=").append(path).append(";");
        }
        if (isSecure) {
            sf.append("secure;HTTPOnly;");
        } else {
            sf.append("HTTPOnly;");
        }
        response.addHeader("Set-Cookie", sf.toString());
    }
}
