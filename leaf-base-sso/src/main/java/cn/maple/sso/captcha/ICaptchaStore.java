package cn.maple.sso.captcha;

/**
 * <p>
 * 图片验证码票据存储接口
 * </p>
 *
 * <p>
 * 该接口定义了验证码存储的基本操作，包括：
 * 1. 存储验证码内容
 * 2. 获取验证码内容
 * 3. 支持多种存储实现（Session、Redis、内存等）
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 创建验证码存储实例
 * ICaptchaStore store = new CaptchaStoreSession(request);
 *
 * // 存储验证码
 * String ticket = UUID.randomUUID().toString();
 * store.put(ticket, "A7B9C");
 *
 * // 获取验证码并验证
 * String captcha = store.get(ticket);
 * boolean isValid = userInput.equalsIgnoreCase(captcha);
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
public interface ICaptchaStore {
    /**
     * <p>
     * 获取验证码票据的验证内容
     * </p>
     *
     * @param ticket 验证码票据，作为存储的唯一标识
     * @return 验证码内容，如果票据不存在或已过期则返回null
     */
    String get(String ticket);

    /**
     * <p>
     * 设置验证码票据的验证内容
     * </p>
     *
     * @param ticket  验证码票据，作为存储的唯一标识
     * @param captcha 验证码内容
     * @return 存储是否成功，true表示成功，false表示失败
     */
    boolean put(String ticket, String captcha);
}
