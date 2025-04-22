package cn.maple.sso.captcha;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * <p>
 * 图片验证码接口
 * </p>
 * 
 * <p>
 * 该接口定义了验证码生成和验证的基本操作，包括：
 * 1. 生成图片验证码并输出到响应流
 * 2. 验证用户输入的验证码是否正确
 * 3. 支持多种验证码实现（静态图片、GIF动态图片等）
 * </p>
 * 
 * <p>
 * 安全说明：
 * - 实现类应确保验证码生成的随机性和不可预测性
 * - 应使用安全的存储机制保存验证码内容
 * - 验证码应有合理的过期时间，防止暴力破解
 * - 应提供足够的干扰元素，防止OCR自动识别
 * - 验证过程应防止重放攻击和暴力尝试
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 创建验证码实例
 * ICaptcha captcha = ImageCaptcha.getInstance()
 *     .setLength(5)         // 设置验证码长度
 *     .setWidth(150)        // 设置图片宽度
 *     .setHeight(50)        // 设置图片高度
 *     .setGif(true);        // 设置为GIF动态验证码
 * 
 * // 生成验证码并输出到响应流
 * String ticket = UUID.randomUUID().toString();
 * captcha.generate(request, response.getOutputStream(), ticket);
 * 
 * // 验证用户输入
 * boolean valid = captcha.verification(request, ticket, userInputCaptcha);
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
public interface ICaptcha extends Serializable {
    /**
     * <p>
     * 生成图片验证码
     * </p>
     * <p>
     * 该方法负责生成验证码图片并写入输出流，同时将验证码内容存储起来以便后续验证。
     * 实现类应确保：
     * 1. 生成足够随机的验证码内容
     * 2. 安全地存储验证码内容
     * 3. 生成包含适当干扰元素的验证码图片
     * 4. 正确处理IO异常
     * </p>
     *
     * @param request 当前HTTP请求对象，用于获取存储上下文
     * @param out     输出流，用于写入生成的验证码图片
     * @param ticket  验证码票据，作为存储验证码的唯一标识
     * @throws IOException 如果图片生成或写入过程中发生I/O错误
     */
    void generate(HttpServletRequest request, OutputStream out, String ticket) throws IOException;

    /**
     * <p>
     * 判断验证码是否正确
     * </p>
     * <p>
     * 该方法负责验证用户输入的验证码是否与之前生成的验证码匹配。
     * 实现类应确保：
     * 1. 安全地获取存储的验证码内容
     * 2. 正确比较用户输入与存储的验证码
     * 3. 考虑是否忽略大小写等验证策略
     * 4. 防止重放攻击（可选：验证成功后立即失效）
     * </p>
     *
     * @param request 当前HTTP请求对象，用于获取存储上下文
     * @param ticket  验证码票据，用于获取存储的验证码
     * @param captcha 用户输入的验证码
     * @return 验证结果，true表示验证通过，false表示验证失败
     */
    boolean verification(HttpServletRequest request, String ticket, String captcha);
}
