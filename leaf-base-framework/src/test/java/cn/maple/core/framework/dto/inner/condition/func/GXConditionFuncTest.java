package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.inner.condition.GXConditionSegment;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXConditionFuncTest {
    @Test
    void concatConditionBuildsSegmentWithoutMutatingInternalParamMap() {
        GXConditionFuncConcat condition = new GXConditionFuncConcat("u", "like", "alpha", "first_name", "last_name");
        Map<String, Object> before = new HashMap<>(condition.getParamMap());

        GXConditionSegment segment = condition.toSegment();

        assertTrue(segment.sql().contains("concat(u.first_name,u.last_name) LIKE"), segment.sql());
        assertEquals("alpha%", segment.params().get(condition.getParamName()));
        assertEquals(before, condition.getParamMap());
    }

    @Test
    void functionExpressionRejectsUnsafeColumnName() {
        GXConditionFuncConcat condition = new GXConditionFuncConcat("u", "like", "alpha", "name;drop table user");

        assertThrows(GXSqlInjectionException.class, condition::toSegment);
    }

    @Test
    void jsonSearchUsesMysqlDialectAndStableParamsByDefault() {
        withDbType("mysql", () -> {
            GXConditionFuncJsonSearch condition = new GXConditionFuncJsonSearch("u", "extra", "alpha");
            Map<String, Object> before = new HashMap<>(condition.getParamMap());

            GXConditionSegment segment = condition.toSegment();

            assertTrue(segment.sql().contains("JSON_SEARCH(u.extra"), segment.sql());
            assertTrue(segment.sql().contains(" IS NOT NULL"), segment.sql());
            assertEquals("one", segment.params().get(condition.getParamName() + "_oneOrAll"));
            assertEquals("alpha", segment.params().get(condition.getParamName()));
            assertEquals(before, condition.getParamMap());
        });
    }

    @Test
    void jsonContainsUsesPostgresDialectWhenConfigured() {
        withDbType("postgresql", () -> {
            GXConditionFuncJsonContains condition = new GXConditionFuncJsonContains(
                    "u", "extra", Dict.create().set("name", "alpha"), "profile");
            Map<String, Object> before = new HashMap<>(condition.getParamMap());

            GXConditionSegment segment = condition.toSegment();

            assertTrue(segment.sql().contains("@> CAST(#{dbQueryParamInnerDto.paramMap."), segment.sql());
            assertTrue(segment.sql().contains(" AS jsonb)"), segment.sql());
            assertEquals("$.profile", segment.params().get(condition.getParamName() + "_path"));
            assertEquals("{\"name\":\"alpha\"}", segment.params().get(condition.getParamName()));
            assertEquals(before, condition.getParamMap());
        });
    }

    @Test
    void jsonOverlapsUsesPostgresArrayComparisonWhenConfigured() {
        withDbType("postgresql", () -> {
            GXConditionFuncJsonOverlaps condition = new GXConditionFuncJsonOverlaps(
                    "u", "tags", List.of("a", "b"), "$");

            GXConditionSegment segment = condition.toSegment();

            assertTrue(segment.sql().contains("jsonb_array_elements"), segment.sql());
            assertTrue(segment.sql().contains("JOIN jsonb_array_elements"), segment.sql());
            assertEquals("$", segment.params().get(condition.getParamName() + "_path"));
            assertEquals("[\"a\",\"b\"]", segment.params().get(condition.getParamName()));
        });
    }

    @Test
    void jsonConditionsRejectUnsafeFieldAndBlankValue() {
        GXConditionFuncJsonSearch unsafeField = new GXConditionFuncJsonSearch("u", "extra;drop table user", "alpha");
        GXConditionFuncJsonSearch nullValue = new GXConditionFuncJsonSearch("u", "extra", null);

        assertThrows(GXSqlInjectionException.class, unsafeField::toSegment);
        assertThrows(GXBusinessException.class, nullValue::toSegment);
    }

    @Test
    void jsonPathRejectsInjectionText() {
        GXConditionFuncJsonContains condition = new GXConditionFuncJsonContains(
                "u", "extra", Dict.create().set("name", "alpha"), "profile;drop table user");

        assertThrows(GXSqlInjectionException.class, condition::toSegment);
    }

    @Test
    void jsonFunctionsRejectUnsupportedH2AndSqliteDialects() {
        withDbType("h2", () -> {
            GXConditionFuncJsonSearch search = new GXConditionFuncJsonSearch("u", "extra", "alpha");
            assertThrows(GXBusinessException.class, search::toSegment);
        });
        withDbType("sqlite", () -> {
            GXConditionFuncJsonContains contains = new GXConditionFuncJsonContains(
                    "u", "extra", Dict.create().set("name", "alpha"), "profile");
            GXConditionFuncJsonOverlaps overlaps = new GXConditionFuncJsonOverlaps("u", "tags", List.of("a"), "$");
            assertThrows(GXBusinessException.class, contains::toSegment);
            assertThrows(GXBusinessException.class, overlaps::toSegment);
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
