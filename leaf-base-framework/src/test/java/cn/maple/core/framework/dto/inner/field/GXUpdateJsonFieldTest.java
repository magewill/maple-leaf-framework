package cn.maple.core.framework.dto.inner.field;

import cn.maple.core.framework.exception.GXBusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXUpdateJsonFieldTest {
    @Test
    void jsonSetStringUsesPostgresDialectWhenConfigured() {
        withDbType("postgresql", () -> {
            GXUpdateJsonSetStrField field = new GXUpdateJsonSetStrField("u", "extra", "profile.name", "alice");

            String sql = field.updateString();

            assertTrue(sql.contains("u.extra = jsonb_set("), sql);
            assertTrue(sql.contains("to_jsonb(CAST(#{dbQueryParamInnerDto.paramMap."), sql);
            assertTrue(sql.contains("ARRAY[]::text[]"), sql);
        });
    }

    @Test
    void jsonSetMapUsesPostgresJsonCastWhenConfigured() {
        withDbType("postgresql", () -> {
            GXUpdateJsonSetMapField<Map<String, Object>> field = new GXUpdateJsonSetMapField<>(
                    "u", "extra", "profile", Map.of("name", "alice"));

            String sql = field.updateString();

            assertTrue(sql.contains("CAST(#{dbQueryParamInnerDto.paramMap."), sql);
            assertTrue(sql.contains(" AS jsonb)"), sql);
        });
    }

    @Test
    void jsonRemoveUsesPostgresRemoveOperatorWhenConfigured() {
        withDbType("postgresql", () -> {
            GXUpdateJsonRemoveField field = new GXUpdateJsonRemoveField("u", "extra", "profile.name");

            String sql = field.updateString();

            assertTrue(sql.contains("u.extra = CASE WHEN"), sql);
            assertTrue(sql.contains(" #- "), sql);
        });
    }

    @Test
    void mapFieldUsesPostgresJsonbCastWhenConfigured() {
        withDbType("postgresql", () -> {
            GXUpdateMapField<Map<String, Object>> field = new GXUpdateMapField<>("u", "extra", Map.of("name", "alice"));

            String sql = field.updateString();

            assertTrue(sql.startsWith("u.extra = CAST("), sql);
            assertTrue(sql.endsWith(" AS jsonb)"), sql);
        });
    }

    @Test
    void jsonUpdateFieldsRejectUnsupportedH2AndSqliteDialects() {
        withDbType("h2", () -> {
            GXUpdateJsonSetStrField field = new GXUpdateJsonSetStrField("u", "extra", "profile.name", "alice");
            assertThrows(GXBusinessException.class, field::updateString);
        });
        withDbType("sqlite", () -> {
            GXUpdateJsonRemoveField field = new GXUpdateJsonRemoveField("u", "extra", "profile.name");
            assertThrows(GXBusinessException.class, field::updateString);
        });
    }

    private static void withDbType(String dbType, Runnable runnable) {
        String oldValue = System.getProperty("spring.datasource.druid.db-type");
        System.setProperty("spring.datasource.druid.db-type", dbType);
        try {
            runnable.run();
        } finally {
            if (oldValue == null) {
                System.clearProperty("spring.datasource.druid.db-type");
            } else {
                System.setProperty("spring.datasource.druid.db-type", oldValue);
            }
        }
    }
}
