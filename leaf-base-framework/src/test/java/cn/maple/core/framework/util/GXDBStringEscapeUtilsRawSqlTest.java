package cn.maple.core.framework.util;

import cn.maple.core.framework.exception.GXSqlInjectionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXDBStringEscapeUtilsRawSqlTest {
    @Test
    void rawConditionValidationAllowsLegitimateSubqueryPredicate() {
        String rawSql = "sys_menu.menu_id not in (select m.parent_id from sys_menu m inner join sys_role_menu rm on m.menu_id = rm.menu_id and rm.role_id = 88)";

        assertEquals(rawSql, GXDBStringEscapeUtils.normalizeAndValidateRawSqlCondition(rawSql));
        assertThrows(GXSqlInjectionException.class,
                () -> GXDBStringEscapeUtils.normalizeAndValidateRawSqlCondition("name = 1; drop table sys_menu"));
        assertThrows(GXSqlInjectionException.class,
                () -> GXDBStringEscapeUtils.normalizeAndValidateRawSqlCondition("name = 1 or 1=1"));
        assertThrows(GXSqlInjectionException.class,
                () -> GXDBStringEscapeUtils.normalizeAndValidateRawSqlCondition("menu_id in (select id from a union select id from b)"));
    }

    @Test
    void rawQueryValidationAllowsLegitimateSelectWithJoinAndSubquery() {
        String sql = """
                select * from sys_menu m
                inner join sys_role_menu rm on m.menu_id = rm.menu_id and rm.role_id = 88
                where m.menu_id not in (select m2.parent_id from sys_menu m2)
                """;

        assertEquals(sql.trim(), GXDBStringEscapeUtils.normalizeAndValidateRawSqlQuery(sql));
    }

    @Test
    void rawQueryValidationRejectsControlSymbolsAndNonQuerySql() {
        assertThrows(GXSqlInjectionException.class,
                () -> GXDBStringEscapeUtils.normalizeAndValidateRawSqlQuery("select * from sys_menu; drop table sys_menu"));
        assertThrows(GXSqlInjectionException.class,
                () -> GXDBStringEscapeUtils.normalizeAndValidateRawSqlQuery("update sys_menu set menu_name = 'x'"));
        assertThrows(GXSqlInjectionException.class,
                () -> GXDBStringEscapeUtils.normalizeAndValidateRawSqlQuery("select sleep(10)"));
        assertThrows(GXSqlInjectionException.class,
                () -> GXDBStringEscapeUtils.normalizeAndValidateRawSqlQuery("select name into outfile '/tmp/x' from sys_menu"));
    }

    @Test
    void rawExpressionValidationAllowsLegitimateHavingAndUpdateFragments() {
        String expression = "case when status = 1 or status = 2 then score else 0 end";

        assertEquals(expression, GXDBStringEscapeUtils.normalizeAndValidateRawSqlExpression(expression));
        assertThrows(GXSqlInjectionException.class,
                () -> GXDBStringEscapeUtils.normalizeAndValidateRawSqlExpression("if(status = 1, sleep(10), 0)"));
    }
}
