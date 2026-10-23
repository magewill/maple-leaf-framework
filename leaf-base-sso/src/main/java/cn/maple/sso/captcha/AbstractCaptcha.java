package cn.maple.sso.captcha;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.sso.enums.GXRandomType;
import cn.maple.sso.utils.GXRandomUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;

import java.awt.Color;
import java.awt.Font;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Base captcha generator.
 *
 * <p>Instances are mutable during setup and frozen once the first image has been produced:
 * rendering on a shared instance can no longer be perturbed by a concurrent caller, and the
 * freeze makes the "configure then render" contract enforceable.</p>
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

    private transient boolean frozen;

    @Override
    public synchronized void generate(HttpServletRequest request, OutputStream out, String ticket) throws IOException {
        String captcha = randomCaptcha();
        frozen = true;
        ICaptchaStore store = getCaptchaStore(request);
        try (ByteArrayOutputStream imageBuffer = new ByteArrayOutputStream()) {
            writeImage(captcha, imageBuffer);
            if (!store.put(ticket, captcha, expireSeconds)) {
                throw new GXBusinessException("Captcha store rejected the ticket, image was not written.");
            }
            imageBuffer.writeTo(out);
            out.flush();
        } catch (IOException | RuntimeException e) {
            store.remove(ticket);
            throw e;
        }
    }

    @Override
    public boolean verification(HttpServletRequest request, String ticket, @Nullable String captcha) {
        if (captcha == null) {
            return false;
        }
        String expected = getCaptchaStore(request).consume(ticket);
        if (expected == null) {
            return false;
        }
        return ignoreCase ? expected.equalsIgnoreCase(captcha) : expected.equals(captcha);
    }

    public AbstractCaptcha setLength(int length) {
        checkNotFrozen();
        this.length = length;
        return this;
    }

    public AbstractCaptcha setWidth(int width) {
        checkNotFrozen();
        this.width = width;
        return this;
    }

    public AbstractCaptcha setHeight(int height) {
        checkNotFrozen();
        this.height = height;
        return this;
    }

    public AbstractCaptcha setGif(boolean gif) {
        checkNotFrozen();
        this.gif = gif;
        return this;
    }

    /**
     * Whether the instance already produced an image and therefore rejects reconfiguration.
     *
     * @return {@code true} when frozen
     */
    public boolean isFrozen() {
        return frozen;
    }

    private void checkNotFrozen() {
        if (frozen) {
            throw new GXBusinessException("Captcha instance is already in use, create a new instance instead.");
        }
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
