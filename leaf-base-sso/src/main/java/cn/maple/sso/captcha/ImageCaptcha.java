package cn.maple.sso.captcha;

import cn.maple.sso.utils.GXRandomUtil;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Image captcha implementation.
 *
 * <p>Each call to {@link #getInstance()} returns a new mutable instance. Configure
 * the returned object per endpoint or bean instead of sharing it across unrelated
 * captcha flows.</p>
 *
 * <pre>
 * ImageCaptcha captcha = ImageCaptcha.getInstance()
 *     .setLength(4)
 *     .setWidth(150)
 *     .setHeight(50)
 *     .setGif(true);
 *
 * String ticket = UUID.randomUUID().toString();
 * captcha.generate(request, response.getOutputStream(), ticket);
 * boolean valid = captcha.verification(request, ticket, userInputCaptcha);
 * </pre>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
public class ImageCaptcha extends AbstractCaptcha {
    public ImageCaptcha() {
    }

    /**
     * Create a new configurable captcha instance.
     *
     * @return a new captcha instance
     */
    public static ImageCaptcha getInstance() {
        return new ImageCaptcha();
    }

    /**
     * Render the captcha text as an image.
     *
     * @param captcha captcha text
     * @param out     target output stream
     * @return rendered captcha text
     * @throws IOException when image rendering or writing fails
     */
    @Override
    protected String writeImage(String captcha, OutputStream out) throws IOException {
        try {
            if (gif) {
                GifEncoder gifEncoder = new GifEncoder();
                gifEncoder.start(out);
                gifEncoder.setQuality(180);
                gifEncoder.setDelay(100);
                gifEncoder.setRepeat(0);

                for (int i = 0; i < length; i++) {
                    gifEncoder.addFrame(graphicsImage(captcha, i));
                }
                gifEncoder.finish();
            } else {
                ImageIO.write(graphicsImage(captcha, 1), suffix, out);
            }
            out.flush();
            return captcha;
        } catch (IOException e) {
            throw new IOException("Generate captcha image failed", e);
        }
    }

    /**
     * Draw one captcha frame.
     *
     * @param code captcha text
     * @param flag gif frame index
     * @return rendered captcha frame
     */
    private BufferedImage graphicsImage(String code, int flag) {
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = (Graphics2D) bi.getGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setFont(font);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (interfere > 0) {
                for (int i = 0; i < interfere; i++) {
                    g.setColor(null == interfereColor ? GXRandomUtil.getColor(rgbArr) : interfereColor);
                    g.setStroke(new BasicStroke(1.1f + GXRandomUtil.nextFloat() / 2, BasicStroke.CAP_BUTT,
                            BasicStroke.JOIN_BEVEL));
                    int x1 = num(-10, width - 10);
                    int y1 = num(5, height - 5);
                    int x2 = num(10, width + 10);
                    int y2 = num(2, height - 2);
                    g.drawLine(x1, y1, x2, y2);
                    g.setColor(null == interfereColor ? GXRandomUtil.getColor(rgbArr) : interfereColor);
                    g.drawOval(num(width), num(height), 3 + num(15), 3 + num(15));
                }
            }
            int h = height - ((height - font.getSize()) >> 1);
            int w = width / length;
            for (int i = 0; i < length; i++) {
                g.setColor(null == color ? GXRandomUtil.getColor(rgbArr) : color);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, gif ? getAlpha(i, flag) : 0.75f));
                g.drawString(String.valueOf(code.charAt(i)), (width - (length - i) * w) + (w - font.getSize()) + 1,
                        h - 3);
            }
            return bi;
        } finally {
            g.dispose();
        }
    }

    /**
     * Calculate gif frame opacity.
     *
     * @param i character index
     * @param j frame index
     * @return alpha value from 0.0 to 1.0
     */
    private float getAlpha(int i, int j) {
        int num = i + j;
        float r = (float) 1 / (length - 1);
        float s = length * r;
        return num >= length ? (num * r - s) : num * r;
    }
}
