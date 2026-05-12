package cn.maple.core.datasource.builder;

import cn.maple.core.framework.exception.GXSqlInjectionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXSqlDialectSupportTest {
    @Test
    void validateRawSqlStrictAllowsLegitimateSelectQuery() {
        String sql = """
                select * from sys_menu m
                inner join sys_role_menu rm on m.menu_id = rm.menu_id and rm.role_id = 88
                where m.menu_id not in (select m2.parent_id from sys_menu m2)
                """;

        assertEquals(sql.trim(), GXSqlDialectSupport.validateRawSqlStrict(sql));
    }

    @Test
    void validateRawSqlStrictRejectsDangerousSql() {
        assertThrows(GXSqlInjectionException.class,
                () -> GXSqlDialectSupport.validateRawSqlStrict("select * from sys_menu; drop table sys_menu"));
        assertThrows(GXSqlInjectionException.class,
                () -> GXSqlDialectSupport.validateRawSqlStrict("delete from sys_menu"));
    }
}
