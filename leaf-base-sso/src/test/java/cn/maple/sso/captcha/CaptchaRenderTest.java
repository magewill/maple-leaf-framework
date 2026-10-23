package cn.maple.sso.captcha;

import cn.maple.core.framework.exception.GXBusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real rendering tests for {@link ImageCaptcha}. The legacy coverage replaced
 * {@code writeImage}, so the rendering path (and its edge cases) was never executed.
 */
class CaptchaRenderTest {
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};

    private static final byte[] GIF_MAGIC = {'G', 'I', 'F', '8', '9', 'a'};

    @Test
    void pngStartsWithMagicBytes() throws Exception {
        ImageCaptcha captcha = captcha(4, 120, 48, false);
        MemoryStore store = new MemoryStore();
        captcha.setCaptchaStore(store);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        captcha.generate(new MockHttpServletRequest(), out, "ticket");

        assertTrue(out.size() > 0, "captcha image must be written");
        assertTrue(startsWith(out.toByteArray(), PNG_MAGIC), "PNG magic bytes expected");
        assertNotNull(store.get("ticket"));
    }

    @Test
    void gifStartsWithGif89a() throws Exception {
        ImageCaptcha captcha = captcha(3, 90, 40, true);
        captcha.setCaptchaStore(new MemoryStore());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        captcha.generate(new MockHttpServletRequest(), out, "ticket");

        assertTrue(startsWith(out.toByteArray(), GIF_MAGIC), "GIF89a magic bytes expected");
    }

    @Test
    void singleCharGifDoesNotThrow() throws Exception {
        ImageCaptcha captcha = captcha(1, 60, 30, true);
        captcha.setCaptchaStore(new MemoryStore());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        captcha.generate(new MockHttpServletRequest(), out, "ticket");

        assertTrue(startsWith(out.toByteArray(), GIF_MAGIC));
    }

    @Test
    void widthSmallerThanLengthIsHandled() throws Exception {
        ImageCaptcha captcha = captcha(8, 4, 30, false);
        captcha.setCaptchaStore(new MemoryStore());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        captcha.generate(new MockHttpServletRequest(), out, "ticket");

        assertTrue(out.size() > 0);
    }

    @Test
    void nullCaptchaDoesNotInvalidateTicket() throws Exception {
        ImageCaptcha captcha = captcha(4, 80, 30, false);
        MemoryStore store = new MemoryStore();
        captcha.setCaptchaStore(store);
        captcha.generate(new MockHttpServletRequest(), new ByteArrayOutputStream(), "ticket");

        boolean verified = captcha.verification(new MockHttpServletRequest(), "ticket", null);

        assertFalse(verified, "null input must not authenticate");
        assertNotNull(store.get("ticket"), "null input must not invalidate the user's captcha");
    }

    @Test
    void storeFailureRaisesBusinessException() {
        ImageCaptcha captcha = captcha(4, 80, 30, false);
        captcha.setCaptchaStore(new ICaptchaStore() {
            @Override
            public String get(String ticket) {
                return null;
            }

            @Override
            public boolean put(String ticket, String captcha) {
                return false;
            }
        });

        assertThrows(GXBusinessException.class,
                () -> captcha.generate(new MockHttpServletRequest(), new ByteArrayOutputStream(), "ticket"));
    }

    @Test
    void instanceIsFrozenAfterFirstGenerate() throws Exception {
        ImageCaptcha captcha = captcha(4, 80, 30, false);
        captcha.setCaptchaStore(new MemoryStore());
        captcha.generate(new MockHttpServletRequest(), new ByteArrayOutputStream(), "ticket");

        assertThrows(GXBusinessException.class, () -> captcha.setLength(6),
                "configuration must be frozen once the instance produced an image");
        assertEquals(4, captcha.getLength());
    }

    private static ImageCaptcha captcha(int length, int width, int height, boolean gif) {
        ImageCaptcha captcha = ImageCaptcha.getInstance();
        captcha.setLength(length).setWidth(width).setHeight(height).setGif(gif);
        return captcha;
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static class MemoryStore implements ICaptchaStore {
        private final Map<String, String> values = new ConcurrentHashMap<>();

        @Override
        public String get(String ticket) {
            return values.get(ticket);
        }

        @Override
        public boolean put(String ticket, String captcha) {
            values.put(ticket, captcha);
            return true;
        }

        @Override
        public boolean put(String ticket, String captcha, int expireSeconds) {
            return put(ticket, captcha);
        }

        @Override
        public void remove(String ticket) {
            values.remove(ticket);
        }
    }
}
