package cn.maple.sso.utils;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.sso.properties.GXSSOProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXSSOUtilsBehaviorTest {
    @BeforeEach
    void setUp() {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());
    }

    @AfterEach
    void tearDown() {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties());
    }

    @Test
    void ajaxStatusUsesRequestedStatusInBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        GXHttpUtil.ajaxStatus(response, 403, "Forbidden");

        assertEquals(403, response.getStatus());
        assertEquals(403, JSONUtil.parseObj(response.getContentAsString()).getInt("code"));
    }

    @Test
    void requestUrlDoesNotTrustRawHostHeader() {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setCookieDomain("app.example.com"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure/page");
        request.setScheme("https");
        request.setServerPort(443);
        request.setQueryString("a=1");
        request.addHeader("Host", "evil.example");

        assertEquals("https://app.example.com/secure/page?a=1", GXHttpUtil.getRequestUrl(request));
    }

    @Test
    void browserFingerprintUsesFullSha256AndAcceptsLegacyFingerprint() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 Maple Test");

        String fingerprint = GXBrowserUtil.getUserAgent(request);
        String legacy = SecureUtil.md5("Mozilla/5.0 Maple Test").substring(3, 8);

        assertEquals(64, fingerprint.length());
        assertTrue(GXBrowserUtil.isLegalUserAgent(request, fingerprint));
        assertTrue(GXBrowserUtil.isLegalUserAgent(request, legacy));
    }

    @Test
    void ipHelperIgnoresForwardedHeadersByDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("x-forwarded-for", "203.0.113.7");

        assertEquals("10.0.0.8", GXIpHelperUtil.getIpAddr(request));
    }

    @Test
    void ipHelperUsesForwardedHeadersOnlyWhenTrusted() {
        GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setTrustForwardedIpHeaders(true));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("x-forwarded-for", "203.0.113.7, 10.0.0.1");

        assertEquals("203.0.113.7", GXIpHelperUtil.getIpAddr(request));
    }

    @Test
    void randomGeneratorStateIsNotPublic() throws Exception {
        assertFalse(Modifier.isPublic(GXRandomUtil.class.getDeclaredField("SECURE_RANDOM").getModifiers()));
        assertTrue(GXRandomUtil.nextInt(10) >= 0);
        assertTrue(GXRandomUtil.nextFloat() >= 0.0f);
    }
}
