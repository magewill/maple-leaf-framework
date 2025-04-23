package cn.maple.sso.captcha;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * <p>
 * 图片验证码内容存储 Session 实现类
 * </p>
 * 
 * <p>
 * 该类负责将验证码存储在HttpSession中，并提供以下功能：
 * 1. 自动设置验证码过期时间（默认1分钟）
 * 2. 安全地获取和存储验证码内容
 * 3. 防止空指针和类型转换异常
 * </p>
 * 
 * <p>
 * 安全说明：
 * - 验证码默认1分钟过期，防止暴力破解
 * - 使用ticket作为唯一标识，避免会话混淆
 * - 安全处理null值，防止空指针异常
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 创建验证码存储实例
 * ICaptchaStore store = new CaptchaStoreSession(request);
 * 
 * // 存储验证码
 * store.put("ticket123", "A7B9C");
 * 
 * // 获取验证码
 * String captcha = store.get("ticket123");
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
public class CaptchaStoreSession implements ICaptchaStore {
  /**
   * 验证码存储的会话对象
   */
  private final HttpSession httpSession;
  
  /**
   * 验证码默认过期时间（秒）
   */
  private static final int DEFAULT_EXPIRE_SECONDS = 60;

  /**
   * 私有构造方法，防止直接实例化
   */
  private CaptchaStoreSession() {
    throw new UnsupportedOperationException("不允许无参构造，请使用带HttpServletRequest参数的构造方法");
  }

  /**
   * 使用HttpServletRequest创建验证码存储实例
   * 
   * @param request HTTP请求对象，用于获取会话
   * @throws IllegalArgumentException 如果request为null
   */
  public CaptchaStoreSession(HttpServletRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("HttpServletRequest不能为null");
    }
    this.httpSession = request.getSession();
  }

  /**
   * <p>
   * 获取验证码票据对应的验证码内容
   * </p>
   * <p>
   * 安全地从会话中获取验证码，并处理null值情况
   * </p>
   *
   * @param ticket 验证码票据，作为存储的唯一标识
   * @return 验证码内容，如果不存在则返回null
   */
  @Override
  public String get(String ticket) {
    if (ticket == null || ticket.trim().isEmpty()) {
      return null;
    }
    Object captchaObj = httpSession.getAttribute(ticket);
    return captchaObj != null ? String.valueOf(captchaObj) : null;
  }

  /**
   * <p>
   * 将验证码内容存储到会话中
   * </p>
   * <p>
   * 存储验证码并设置过期时间，防止长时间有效导致的安全风险
   * </p>
   *
   * @param ticket 验证码票据，作为存储的唯一标识
   * @param captcha 验证码内容
   * @return 存储是否成功
   */
  @Override
  public boolean put(String ticket, String captcha) {
    if (ticket == null || ticket.trim().isEmpty() || captcha == null) {
      return false;
    }
    httpSession.setMaxInactiveInterval(DEFAULT_EXPIRE_SECONDS);
    httpSession.setAttribute(ticket, captcha);
    return true;
  }
}
