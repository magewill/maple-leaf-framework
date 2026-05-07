package cn.maple.sso.enums;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GXEnumBehaviorTest {
    private final Locale defaultLocale = Locale.getDefault();

    @AfterEach
    void tearDown() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    void randomTypeParsingUsesRootLocaleAndTrimsInput() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));

        assertEquals(GXRandomType.CHINESE, GXRandomType.fromName(" chinese "));
        assertEquals(GXRandomType.MIX, GXRandomType.fromName("har"));
        assertEquals(GXRandomType.MIX, GXRandomType.fromName("unknown"));
    }

    @Test
    void tokenOriginParsingUsesExactStableValues() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));

        assertEquals(GXTokenOrigin.IOS, GXTokenOrigin.fromValue(" 2 "));
        assertEquals(GXTokenOrigin.IOS, GXTokenOrigin.fromDesc("APPLE IOS"));
        assertEquals(GXTokenOrigin.COOKIE, GXTokenOrigin.fromDesc("apple"));
    }

    @Test
    void enumDescriptionsAreAscii() {
        assertEquals("verify permission", GXAction.Normal.getDesc());
        assertEquals("skip permission", GXAction.Skip.getDesc());
        assertEquals("normal", GXTokenFlag.NORMAL.desc());
        assertEquals("cache shut", GXTokenFlag.CACHE_SHUT.desc());
    }
}
