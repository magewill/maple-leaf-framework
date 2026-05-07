package cn.maple.sso.captcha;

import cn.maple.sso.utils.GXRandomUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

/**
 * <p>
 * 图片验证码实现类
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 创建验证码实例并配置参数
 * ImageCaptcha captcha = ImageCaptcha.getInstance()
 *     .setLength(5)         // 设置验证码长度
 *     .setWidth(150)        // 设置图片宽度
 *     .setHeight(50)        // 设置图片高度
 *     .setInterfere(8)      // 设置干扰元素数量
 *     .setGif(true);        // 设置为GIF动态验证码
 *
 * // 生成验证码并输出到响应流
 * String ticket = UUID.randomUUID().toString();
 * String captchaCode = captcha.generate(request, response.getOutputStream(), ticket);
 *
 * // 前端提交验证
 * boolean valid = captcha.verification(request, ticket, userInputCaptcha);
 *
 * // 在Spring Boot控制器中的完整示例
 * @GetMapping("/captcha")
 * public void getCaptcha(HttpServletRequest request, HttpServletResponse response) throws IOException {
 *     response.setContentType("image/gif");
 *     response.setHeader("Pragma", "No-cache");
 *     response.setHeader("Cache-Control", "no-cache");
 *     response.setDateHeader("Expires", 0);
 *
 *     // 创建验证码实例
 *     ImageCaptcha captcha = ImageCaptcha.getInstance()
 *         .setLength(4)
 *         .setGif(true);
 *
 *     // 生成验证码并写入响应
 *     String ticket = UUID.randomUUID().toString();
 *     captcha.generate(request, response.getOutputStream(), ticket);
 * }
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
public class ImageCaptcha extends AbstractCaptcha {
    /**
     * 随机数生成器，用于增加验证码的随机性
     * 使用安全的随机数生成器，提高验证码的安全性
     */
    private static final Random RANDOM = new Random();
    /**
     * 图片验证码单例实例
     * 使用volatile关键字确保多线程环境下的可见性
     */
    private static volatile ImageCaptcha IMAGE_CAPTCHA;

    /**
     * 私有构造方法，防止直接实例化
     * 使用单例模式确保全局唯一实例，提高资源利用率和线程安全性
     */
    private ImageCaptcha() {
        // 私有构造方法，防止直接实例化
        // 请使用 ImageCaptcha.getInstance() 获取实例
    }

    /**
     * <p>
     * 获取验证码实例（单例模式）
     * </p>
     *
     * @return ImageCaptcha 验证码实例
     */
    public static ImageCaptcha getInstance() {
        if (IMAGE_CAPTCHA == null) {
            synchronized (ImageCaptcha.class) {
                if (IMAGE_CAPTCHA == null) {
                    IMAGE_CAPTCHA = new ImageCaptcha();
                }
            }
        }
        return IMAGE_CAPTCHA;
    }

    /**
     * <p>
     * 生成图片验证码并写入输出流
     * </p>
     *
     * @param captcha 验证码文本内容
     * @param out     输出流，用于写入生成的图片数据
     * @return 验证码文本内容
     * @throws IOException 如果图片生成或写入过程中发生I/O错误
     */
    @Override
    protected String writeImage(String captcha, OutputStream out) throws IOException {
        try {
            if (gif) {
                // 生成GIF动态验证码
                GifEncoder gifEncoder = new GifEncoder();
                gifEncoder.start(out);
                gifEncoder.setQuality(180); // 设置图片质量
                gifEncoder.setDelay(100);   // 设置帧延迟(毫秒)
                gifEncoder.setRepeat(0);    // 设置循环次数，0表示无限循环

                // 添加多帧图像，每帧使用不同的透明度和随机干扰元素
                for (int i = 0; i < length; i++) {
                    gifEncoder.addFrame(graphicsImage(captcha, i));
                }
                gifEncoder.finish();
            } else {
                // 生成静态图片验证码
                ImageIO.write(graphicsImage(captcha, 1), suffix, out);
            }
            out.flush();
            return captcha;
        } catch (IOException e) {
            throw new IOException("生成验证码图片失败", e);
        }
    }


    /**
     * <p>
     * 绘制图片验证码
     * </p>
     *
     * @param code 验证码文本内容
     * @param flag 透明度标识，用于GIF动画帧
     * @return 生成的验证码图片
     */
    private BufferedImage graphicsImage(String code, int flag) {
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = (Graphics2D) bi.getGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setFont(font);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 随机画干扰线
        if (interfere > 0) {
            for (int i = 0; i < interfere; i++) {
                g.setColor(null == interfereColor ? GXRandomUtil.getColor(rgbArr) : interfereColor);
                g.setStroke(new BasicStroke(1.1f + GXRandomUtil.RANDOM.nextFloat() / 2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
                int x1 = num(-10, width - 10);
                int y1 = num(5, height - 5);
                int x2 = num(10, width + 10);
                int y2 = num(2, height - 2);
                g.drawLine(x1, y1, x2, y2);
                // 画干扰圆圈
                g.setColor(null == interfereColor ? GXRandomUtil.getColor(rgbArr) : interfereColor);
                g.drawOval(num(width), num(height), 3 + num(15), 3 + num(15));
            }
        }
        // 画字符串
        int h = height - ((height - font.getSize()) >> 1);
        int w = width / length;
        for (int i = 0; i < length; i++) {
            g.setColor(null == color ? GXRandomUtil.getColor(rgbArr) : color);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, gif ? getAlpha(i, flag) : 0.75f));
            // 计算坐标
            g.drawString(String.valueOf(code.charAt(i)), (width - (length - i) * w) + (w - font.getSize()) + 1, h - 3);
        }
        return bi;
    }


    /**
     * <p>
     * 获取透明度,从0到1,自动计算步长
     * </p>
     *
     * @param i 字符索引
     * @param j 帧索引
     * @return 计算得到的透明度值(0.0-1.0)
     */
    private float getAlpha(int i, int j) {
        int num = i + j;
        float r = (float) 1 / (length - 1);
        float s = length * r;
        return num >= length ? (num * r - s) : num * r;
    }
}
