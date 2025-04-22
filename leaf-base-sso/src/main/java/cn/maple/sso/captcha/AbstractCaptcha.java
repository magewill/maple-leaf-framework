package cn.maple.sso.captcha;

import cn.maple.sso.enums.GXRandomType;
import cn.maple.sso.utils.GXRandomUtil;
import lombok.Data;
import lombok.experimental.Accessors;

import jakarta.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;

/**
 * <p>
 * 验证码抽象类
 * </p>
 * 
 * <p>
 * 该抽象类提供验证码生成和验证的基础功能，包括：
 * 1. 支持多种验证码类型（数字、字母、汉字、混合）
 * 2. 支持静态图片和GIF动态图片
 * 3. 提供验证码存储和校验机制
 * 4. 支持自定义字体、颜色和干扰元素
 * </p>
 * 
 * <p>
 * 安全说明：
 * - 默认使用HttpSession存储验证码，支持自定义存储实现
 * - 验证码默认1分钟过期，防止暴力破解
 * - 支持忽略大小写验证，提高用户体验
 * - 提供干扰元素配置，增强验证码安全性
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 创建验证码实例
 * ImageCaptcha captcha = ImageCaptcha.getInstance()
 *     .setLength(5)         // 设置验证码长度
 *     .setWidth(150)        // 设置图片宽度
 *     .setHeight(50)        // 设置图片高度
 *     .setRandomType(GXRandomType.MIX)  // 设置验证码类型
 *     .setGif(true);        // 设置为GIF动态验证码
 * 
 * // 生成验证码
 * String ticket = UUID.randomUUID().toString();
 * captcha.generate(request, response.getOutputStream(), ticket);
 * 
 * // 验证用户输入
 * boolean valid = captcha.verification(request, ticket, userInput);
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
@Data
@Accessors(chain = true)
public abstract class AbstractCaptcha implements ICaptcha {
    /**
     * 是否为 GIF 验证码
     */
    protected boolean gif;

    /**
     * 字体Verdana
     */
    protected Font font;

    /**
     * RGB 颜色数组
     */
    protected int[][] rgbArr;

    /**
     * 干扰量
     */
    protected int interfere = 5;

    /**
     * 干扰色默认随机
     */
    protected Color interfereColor;

    /**
     * 验证码颜色默认随机
     */
    protected Color color;

    /**
     * 验证码随机字符长度
     */
    protected int length;

    /**
     * 验证码显示宽度
     */
    protected int width;

    /**
     * 验证码显示高度
     */
    protected int height;

    /**
     * 图片后缀
     */
    protected String suffix;

    /**
     * 验证码类型
     */
    protected GXRandomType randomType;

    /**
     * 常用汉字
     */
    protected String chineseUnicode;

    /**
     * 图片验证码票据存储接口
     */
    protected ICaptchaStore captchaStore;

    /**
     * 是否忽略验证内容大小写，默认 true
     */
    protected boolean ignoreCase = true;

    /**
     * <p>
     * 生成验证码并写入输出流
     * </p>
     * <p>
     * 该方法执行以下操作：
     * 1. 生成随机验证码字符串
     * 2. 将验证码存储到验证码存储器中（默认为Session）
     * 3. 将验证码图片写入输出流
     * </p>
     * <p>
     * 安全说明：
     * - 验证码生成后立即存储，确保验证的一致性
     * - 使用ticket作为唯一标识，避免会话混淆
     * - 仅当存储成功时才生成图片，确保验证码可验证
     * </p>
     *
     * @param request 当前HTTP请求对象，用于获取Session
     * @param out 输出流，用于写入生成的验证码图片
     * @param ticket 验证码票据，作为存储验证码的唯一标识
     * @throws IOException 如果图片生成或写入过程中发生I/O错误
     */
    @Override
    public void generate(HttpServletRequest request, OutputStream out, String ticket) throws IOException {
        String captcha = randomCaptcha();
        if (getCaptchaStore(request).put(ticket, captcha)) {
            writeImage(captcha, out);
        }
    }

    /**
     * <p>
     * 验证用户输入的验证码是否正确
     * </p>
     * <p>
     * 该方法执行以下操作：
     * 1. 从验证码存储器中获取之前生成的验证码
     * 2. 比较用户输入的验证码与存储的验证码是否匹配
     * 3. 根据ignoreCase配置决定是否忽略大小写
     * </p>
     * <p>
     * 安全说明：
     * - 验证码不存在时直接返回false，防止空指针异常
     * - 支持大小写不敏感比较，提高用户体验
     * - 使用ticket作为唯一标识，确保验证的准确性
     * </p>
     *
     * @param request 当前HTTP请求对象，用于获取Session
     * @param ticket 验证码票据，用于获取存储的验证码
     * @param captcha 用户输入的验证码
     * @return 验证结果，true表示验证通过，false表示验证失败
     */
    @Override
    public boolean verification(HttpServletRequest request, String ticket, String captcha) {
        String tc = getCaptchaStore(request).get(ticket);
        if (null == tc) {
            return false;
        }
        return ignoreCase ? tc.equalsIgnoreCase(captcha) : tc.equals(captcha);
    }

    /**
     * <p>
     * 获取验证码存储器实例
     * </p>
     * <p>
     * 该方法用于获取验证码存储器，如果未设置自定义存储器，则使用默认的Session存储实现。
     * 默认的Session存储实现会将验证码存储在HttpSession中，并设置1分钟的过期时间。
     * </p>
     * <p>
     * 安全说明：
     * - 默认使用HttpSession存储，确保验证码的安全性
     * - 支持自定义存储实现，可以根据需求使用Redis等分布式存储
     * - 验证码默认1分钟过期，防止长时间有效导致的安全风险
     * </p>
     *
     * @param request 当前HTTP请求对象，用于创建Session存储器
     * @return ICaptchaStore 验证码存储器实例
     */
    private ICaptchaStore getCaptchaStore(HttpServletRequest request) {
        if (null == captchaStore) {
            return new CaptchaStoreSession(request);
        }
        return captchaStore;
    }

    /**
     * <p>
     * 输出图片验证码
     * </p>
     * <p>
     * 该抽象方法由子类实现，负责将验证码文本渲染为图片并写入输出流。
     * 不同的验证码实现类可以生成不同风格的验证码图片，如静态图片或GIF动态图片。
     * </p>
     * <p>
     * 安全说明：
     * - 实现类应确保验证码图片具有足够的干扰元素，防止OCR识别
     * - 应正确关闭和刷新输出流，防止资源泄露
     * - 应处理可能的异常，确保系统稳定性
     * </p>
     *
     * @param captcha 验证码文本内容
     * @param out     输出流，用于写入生成的图片数据
     * @return 字符串验证码，通常返回传入的captcha参数
     * @throws IOException 如果图片生成或写入过程中发生I/O错误
     */
    protected abstract String writeImage(String captcha, OutputStream out) throws IOException;

    /**
     * 产生两个数之间的随机数
     * <p>
     * 生成一个位于[min, max)区间内的随机整数，包含最小值，不包含最大值。
     * 该方法主要用于生成验证码图片中的随机位置、大小等参数。
     * </p>
     *
     * @param min 随机数的最小值（包含）
     * @param max 随机数的最大值（不包含）
     * @return 生成的随机数
     */
    protected int num(int min, int max) {
        return min + GXRandomUtil.RANDOM.nextInt(max - min);
    }

    /**
     * 产生0-num的随机数，不包括num
     * <p>
     * 生成一个位于[0, num)区间内的随机整数，包含0，不包含最大值。
     * 该方法是{@link #num(int, int)}的简化版本，常用于数组索引等场景。
     * </p>
     *
     * @param num 随机数的最大值（不包含）
     * @return 生成的随机数
     */
    protected int num(int num) {
        return GXRandomUtil.RANDOM.nextInt(num);
    }

    /**
     * 生成随机验证码文本
     * <p>
     * 该方法根据配置的验证码类型生成随机验证码文本，并设置相关的默认参数。
     * 支持生成数字、字母、混合和中文验证码，可通过randomType属性进行配置。
     * </p>
     * <p>
     * 安全说明：
     * - 默认使用混合字母数字模式，提高验证码复杂度
     * - 中文验证码模式可进一步提高安全性，防止机器自动识别
     * - 默认验证码长度为5，可根据安全需求调整
     * - 使用安全的随机数生成器，增强不可预测性
     * - 避免使用易混淆的字符（如0和O、1和l），提高用户体验
     * - 支持自定义字体和颜色，增加识别难度
     * </p>
     * <p>
     * 默认参数说明：
     * - 验证码类型：默认为混合字母数字(MIX)
     * - 字体：中文验证码使用楷体28号，其他使用Arial 32号
     * - 颜色：默认使用活泼色彩组合
     * - 图片格式：GIF动态验证码使用gif格式，静态验证码使用png格式
     * - 尺寸：默认宽度120像素，高度48像素
     * - 长度：默认5个字符
     * </p>
     *
     * @return 生成的随机验证码字符串
     */
    protected String randomCaptcha() {
        // 设置默认验证码类型
        if (null == randomType) {
            randomType = GXRandomType.MIX;
        }
        
        // 设置默认字体
        if (null == font) {
            if (GXRandomType.CHINESE == randomType) {
                // 中文验证码使用楷体，更易于显示
                font = new Font("楷体", Font.BOLD, 28);
            } else {
                // 非中文验证码使用Arial字体，清晰度高
                font = new Font("Arial", Font.BOLD, 32);
            }
        }
        
        // 设置默认颜色数组
        if (null == rgbArr) {
            rgbArr = ColorType.LIVELY;
        }
        
        // 设置默认图片格式
        if (null == suffix) {
            suffix = gif ? "gif" : "png";
        }
        
        // 设置默认尺寸
        if (width < 10) {
            width = 120;
        }
        if (height < 10) {
            height = 48;
        }
        
        // 设置默认长度，并确保长度在合理范围内
        if (length < 1) {
            length = 5;
        } else if (length > 10) {
            // 限制最大长度，防止生成过长的验证码导致显示问题
            length = 10;
        }
        
        // 根据验证码类型生成随机码
        if (GXRandomType.CHINESE == randomType) {
            // 生成中文验证码
            if (chineseUnicode == null || chineseUnicode.isEmpty()) {
                // 如果未设置中文字符集，使用默认的常用汉字
                return GXRandomUtil.getChinese(null, length);
            }
            return GXRandomUtil.getChinese(chineseUnicode, length);
        }
        
        // 生成数字、字母或混合验证码
        return GXRandomUtil.getText(randomType, length);
    }
}