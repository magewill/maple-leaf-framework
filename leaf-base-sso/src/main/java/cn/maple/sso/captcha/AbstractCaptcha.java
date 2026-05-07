package cn.maple.sso.captcha;

import cn.maple.sso.enums.GXRandomType;
import cn.maple.sso.utils.GXRandomUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.experimental.Accessors;

import java.awt.Color;
import java.awt.Font;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Base captcha generator.
 *
 * <p>Instances are configurable and mutable. Do not share one configured
 * instance across endpoints with different settings unless external
 * synchronization is applied.</p>
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

    protected int expireSeconds = ICaptchaStore.DEFAULT_EXPIRE_SECONDS;

    @Override
    public void generate(HttpServletRequest request, OutputStream out, String ticket) throws IOException {
        String captcha = randomCaptcha();
        ICaptchaStore store = getCaptchaStore(request);
        try (ByteArrayOutputStream imageBuffer = new ByteArrayOutputStream()) {
            writeImage(captcha, imageBuffer);
            if (store.put(ticket, captcha, expireSeconds)) {
                imageBuffer.writeTo(out);
                out.flush();
            }
        } catch (IOException | RuntimeException e) {
            store.remove(ticket);
            throw e;
        }
    }

    @Override
    public boolean verification(HttpServletRequest request, String ticket, String captcha) {
        if (captcha == null) {
            getCaptchaStore(request).remove(ticket);
            return false;
        }
        String expected = getCaptchaStore(request).consume(ticket);
        if (expected == null) {
            return false;
        }
        return ignoreCase ? expected.equalsIgnoreCase(captcha) : expected.equals(captcha);
    }

    private ICaptchaStore getCaptchaStore(HttpServletRequest request) {
        if (captchaStore == null) {
            return new CaptchaStoreSession(request);
        }
        return captchaStore;
    }

    protected abstract String writeImage(String captcha, OutputStream out) throws IOException;

    protected int num(int min, int max) {
        return min + GXRandomUtil.nextInt(max - min);
    }

    protected int num(int num) {
        return GXRandomUtil.nextInt(num);
    }

    protected String randomCaptcha() {
        GXRandomType captchaRandomType = randomType == null ? GXRandomType.MIX : randomType;
        if (font == null) {
            font = new Font(captchaRandomType == GXRandomType.CHINESE ? "Serif" : "Arial", Font.BOLD,
                    captchaRandomType == GXRandomType.CHINESE ? 28 : 32);
        }
        if (rgbArr == null) {
            rgbArr = ColorType.LIVELY;
        }
        if (suffix == null) {
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

        if (captchaRandomType == GXRandomType.CHINESE) {
            if (chineseUnicode == null || chineseUnicode.isEmpty()) {
                return GXRandomUtil.getChinese(null, length);
            }
            return GXRandomUtil.getChinese(chineseUnicode, length);
        }

        return GXRandomUtil.getText(captchaRandomType, length);
    }
}
