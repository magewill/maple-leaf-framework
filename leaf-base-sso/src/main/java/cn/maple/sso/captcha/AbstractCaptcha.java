package cn.maple.sso.captcha;

import cn.maple.sso.enums.GXRandomType;
import cn.maple.sso.utils.GXRandomUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.experimental.Accessors;

import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;

/**
 * <p>
 * 验证码抽象类
 * </p>
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
    protected boolean gif;

    protected Font font;

    protected int[][] rgbArr;

    protected int interfere = 5;

    protected Color interfereColor;

    protected Color color;

    protected int length;

    protected int width;

    protected int height;

    protected String suffix;

    protected GXRandomType randomType;

    protected String chineseUnicode;

    protected ICaptchaStore captchaStore;

    protected boolean ignoreCase = true;

    @Override
    public void generate(HttpServletRequest request, OutputStream out, String ticket) throws IOException {
        String captcha = randomCaptcha();
        if (getCaptchaStore(request).put(ticket, captcha)) {
            writeImage(captcha, out);
        }
    }

    @Override
    public boolean verification(HttpServletRequest request, String ticket, String captcha) {
        String tc = getCaptchaStore(request).get(ticket);
        if (null == tc) {
            return false;
        }
        return ignoreCase ? tc.equalsIgnoreCase(captcha) : tc.equals(captcha);
    }

    private ICaptchaStore getCaptchaStore(HttpServletRequest request) {
        if (null == captchaStore) {
            return new CaptchaStoreSession(request);
        }
        return captchaStore;
    }

    protected abstract String writeImage(String captcha, OutputStream out) throws IOException;

    protected int num(int min, int max) {
        return min + GXRandomUtil.RANDOM.nextInt(max - min);
    }

    protected int num(int num) {
        return GXRandomUtil.RANDOM.nextInt(num);
    }

    protected String randomCaptcha() {
        if (null == randomType) {
            randomType = GXRandomType.MIX;
        }

        if (null == font) {
            if (GXRandomType.CHINESE == randomType) {
                font = new Font("楷体", Font.BOLD, 28);
            } else {
                font = new Font("Arial", Font.BOLD, 32);
            }
        }

        if (null == rgbArr) {
            rgbArr = ColorType.LIVELY;
        }

        if (null == suffix) {
            suffix = gif ? "gif" : "png";
        }

        if (width < 10) {
            width = 120;
        }
        if (height < 10) {
            height = 48;
        }

        if (length < 1) {
            length = 5;
        } else if (length > 10) {
            length = 10;
        }

        if (GXRandomType.CHINESE == randomType) {
            if (chineseUnicode == null || chineseUnicode.isEmpty()) {
                return GXRandomUtil.getChinese(null, length);
            }
            return GXRandomUtil.getChinese(chineseUnicode, length);
        }

        return GXRandomUtil.getText(randomType, length);
    }
}