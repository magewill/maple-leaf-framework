package cn.maple.core.framework.dto.inner.condition;

import cn.maple.core.framework.exception.GXSqlInjectionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXConditionRawTest {
    @Test
    void rawConditionAllowsSubqueryWithJoinPredicate() {
        String rawSql = "sys_menu.menu_id not in (select m.parent_id from sys_menu m inner join sys_role_menu rm on m.menu_id = rm.menu_id and rm.role_id = 88)";

        GXConditionRaw condition = new GXConditionRaw(rawSql);

        assertEquals(rawSql, condition.whereString());
        assertEquals(rawSql, condition.getFieldValue());
        assertEquals(rawSql, condition.getFieldOriginalValue());
        assertEquals(rawSql, condition.toSegment().sql());
    }

    @Test
    void rawConditionRejectsControlSymbolsAndInjectionPatterns() {
        assertThrows(GXSqlInjectionException.class, () -> new GXConditionRaw("name = 1; drop table sys_menu").whereString());
        assertThrows(GXSqlInjectionException.class, () -> new GXConditionRaw("name = 1 or 1=1").whereString());
        assertThrows(GXSqlInjectionException.class, () -> new GXConditionRaw("menu_id in (select id from a union select id from b)").whereString());
    }
}
