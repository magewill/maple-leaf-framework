package cn.maple.core.datasource.builder;

import cn.maple.core.framework.exception.GXSqlInjectionException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXSqlFieldRenderSupportTest {
    @Test
    void sanitizeHavingClauseAllowsLegitimateAggregatesWithPredicates() {
        String clause = "count(*) > 1 and status = 1";

        assertEquals(clause, GXSqlFieldRenderSupport.sanitizeHavingClause(
                clause,
                Set.of("status"),
                GXSqlFieldRenderSupport.SqlFieldRenderMode.PRESERVE_ORIGINAL));
    }

    @Test
    void sanitizeHavingClauseRejectsRawInjectionPatterns() {
        assertThrows(GXSqlInjectionException.class,
                () -> GXSqlFieldRenderSupport.sanitizeHavingClause(
                        "count(*) > 1; drop table sys_menu",
                        Set.of("status"),
                        GXSqlFieldRenderSupport.SqlFieldRenderMode.PRESERVE_ORIGINAL));
    }
}
