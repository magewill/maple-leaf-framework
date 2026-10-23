package cn.maple.sso.properties;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.maple.sso.properties.local.GXLocalSSOConfigProperties;
import cn.maple.sso.properties.local.GXLocalUrlWhiteListsConfigProperties;
import cn.maple.sso.properties.nacos.GXNacosSSOConfigProperties;
import cn.maple.sso.properties.nacos.GXNacosUrlWhiteListsConfigProperties;
import cn.maple.sso.service.GXTokenConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Configuration source and default value contract.
 */
@ResourceLock("sso-global-config")
class GXSSOConfigSourceTest {
    /**
     * Keys that must be registered in the shipped metadata: the three keys added by this
     * feature plus the two switches promoted from interceptor setters to bound configuration.
     */
    private static final List<String> REQUIRED_METADATA_KEYS = List.of(
            "sso.config.source",
            "sso.config.cache-fallback-enabled",
            "sso.config.capacity",
            "sso.config.nothing-annotation-pass",
            "sso.config.illegal-url");

    @AfterEach
    void tearDown() {
        System.clearProperty("sso.config.source");
        System.clearProperty("spring.profiles.active");
    }

    @Test
    void sourceNacosIsDefault() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(GXLocalSSOConfigProperties.class, GXNacosSSOConfigProperties.class,
                    GXLocalUrlWhiteListsConfigProperties.class, GXNacosUrlWhiteListsConfigProperties.class);
            context.refresh();

            assertEquals(1, context.getBeanNamesForType(GXNacosSSOConfigProperties.class).length);
            assertEquals(0, context.getBeanNamesForType(GXLocalSSOConfigProperties.class).length,
                    "the local variant must not activate without an explicit switch");
            assertEquals(1, context.getBeanNamesForType(GXNacosUrlWhiteListsConfigProperties.class).length);
        }
    }

    @Test
    void sourceLocalBindsLocalBeanOnly() {
        System.setProperty("spring.profiles.active", "dev");
        System.setProperty("sso.config.source", "local");

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(GXLocalSSOConfigProperties.class, GXNacosSSOConfigProperties.class,
                    GXLocalUrlWhiteListsConfigProperties.class, GXNacosUrlWhiteListsConfigProperties.class);
            context.refresh();

            assertEquals(1, context.getBeanNamesForType(GXLocalSSOConfigProperties.class).length,
                    "sso.config.source=local must make the local bean reachable");
            assertEquals(0, context.getBeanNamesForType(GXNacosSSOConfigProperties.class).length);
            assertEquals(1, context.getBeanNamesForType(GXLocalUrlWhiteListsConfigProperties.class).length);
        }
    }

    @Test
    void defaultValuesMatchContract() {
        GXSSOProperties properties = new GXSSOProperties();

        assertEquals("nacos", properties.getSource());
        assertEquals(GXSSOProperties.DEFAULT_CACHE_EXPIRES, properties.getCacheExpires());
        assertEquals(7200, properties.getCacheExpires());
        assertFalse(properties.isCacheFallbackEnabled());
        assertEquals(4096, properties.getCapacity());
        assertEquals("token", properties.getTokenName());
        assertEquals("uid", properties.getCookieName());
        assertEquals("/", properties.getCookiePath());
        assertTrue(properties.isCookieHttpOnly());
        assertFalse(properties.isCookieSecure());
        assertEquals("Lax", properties.getCookieSameSite());
        assertEquals(-1, properties.getCookieMaxAge());
        assertFalse(properties.isPermissionUri());
        assertFalse(properties.isTrustForwardedIpHeaders());
        assertTrue(properties.isNothingAnnotationPass(), "no-annotation handlers stay allowed by default");
        assertNull(properties.getIllegalUrl(), "no default illegal-url redirect target");
    }

    @Test
    void sensitiveFieldsHaveNoSourceDefaults() {
        GXSSOProperties properties = new GXSSOProperties();

        assertNull(properties.getSignKey(), "sign key must not carry a hard coded default");
        assertNull(properties.getRsaKeypass(), "RSA key password must not be hard coded");
        assertNull(properties.getRsaStorepass(), "RSA store password must not be hard coded");
    }

    @Test
    void defaultSecretIsDetected() {
        GXTokenConfigService tokenConfigService = new GXTokenConfigService() {
        };

        assertTrue(tokenConfigService.isDefaultTokenSecret(),
                "the framework default secret must be detectable for startup validation");
    }

    @Test
    void metadataContainsAllKeys() throws Exception {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("META-INF/additional-spring-configuration-metadata.json")) {
            assertNotNull(inputStream, "configuration metadata must be shipped with the module");
            String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject metadata = JSONUtil.parseObj(json);
            List<String> names = metadata.getJSONArray("properties").stream()
                    .map(item -> ((JSONObject) item).getStr("name"))
                    .toList();

            assertTrue(names.containsAll(REQUIRED_METADATA_KEYS), "required keys must be registered: " + names);
            assertTrue(names.contains("sso.config.token-name"));
            assertTrue(names.contains("url.white-lists"));
        }
    }
}
