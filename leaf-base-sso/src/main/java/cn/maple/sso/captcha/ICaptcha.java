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
     *
     * @param request 当前HTTP请求对象，用于获取存储上下文
     * @param ticket  验证码票据，用于获取存储的验证码
     * @param captcha 用户输入的验证码
     * @return 验证结果，true表示验证通过，false表示验证失败
     */
    boolean verification(HttpServletRequest request, String ticket, String captcha);
}
