package cn.maple.sso.captcha;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaBehaviorTest {
    @Test
    void verificationConsumesCaptcha() throws Exception {
        TestCaptcha captcha = new TestCaptcha();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        captcha.generate(request, response.getOutputStream(), "ticket");
        String stored = new CaptchaStoreSession(request).get("ticket");

        assertTrue(captcha.verification(request, "ticket", stored));
        assertFalse(captcha.verification(request, "ticket", stored));
        assertNull(new CaptchaStoreSession(request).get("ticket"));
    }

    @Test
    void sessionStoreDoesNotOverrideSessionTimeout() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setMaxInactiveInterval(1800);

        new CaptchaStoreSession(request).put("ticket", "abc", 1);

        assertEquals(1800, request.getSession().getMaxInactiveInterval());
    }

    @Test
    void expiredSessionCaptchaIsRemoved() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        CaptchaStoreSession store = new CaptchaStoreSession(request);

        store.put("ticket", "abc", 1);
        Thread.sleep(1100L);

        assertNull(store.get("ticket"));
        assertNull(request.getSession().getAttribute("ticket"));
    }

    @Test
    void generateRemovesCaptchaWhenResponseWriteFails() {
        MemoryCaptchaStore store = new MemoryCaptchaStore();
        TestCaptcha captcha = new TestCaptcha();
        captcha.setCaptchaStore(store);

        assertThrows(IOException.class, () -> captcha.generate(new MockHttpServletRequest(), new FailingOutputStream(), "ticket"));

        assertNull(store.get("ticket"));
    }

    @Test
    void imageCaptchaGetInstanceReturnsIndependentInstances() {
        assertNotSame(ImageCaptcha.getInstance(), ImageCaptcha.getInstance());
    }

    private static class TestCaptcha extends AbstractCaptcha {
        @Override
        protected String writeImage(String captcha, OutputStream out) throws IOException {
            out.write(captcha.getBytes());
            return captcha;
        }
    }

    private static class MemoryCaptchaStore implements ICaptchaStore {
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
        public void remove(String ticket) {
            values.remove(ticket);
        }
    }

    private static class FailingOutputStream extends ServletOutputStream {
        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }

        @Override
        public void write(int b) throws IOException {
            throw new IOException("write failed");
        }
    }
}
