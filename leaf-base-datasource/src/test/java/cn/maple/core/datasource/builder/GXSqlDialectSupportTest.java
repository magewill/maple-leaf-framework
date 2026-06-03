package cn.maple.core.datasource.builder;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.config.GXDynamicContextHolder;
import cn.maple.core.datasource.config.GXDynamicDbTypeRegistry;
import cn.maple.core.framework.dto.inner.condition.func.GXConditionFuncJsonContains;
import cn.maple.core.framework.dto.inner.field.GXUpdateJsonSetStrField;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXSqlDialectSupportTest {
    @AfterEach
    void cleanup() {
        GXDynamicContextHolder.clear();
        GXDynamicDbTypeRegistry.clear();
    }

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

    @Test
    void applySingleRowLimitUsesCurrentDatasourceDbTypeMysqlLike() {
        GXDynamicDbTypeRegistry.register("master", "mysql");
        try (GXDynamicContextHolder.AutoCloseableDataSource ignored = GXDynamicContextHolder.withDataSource("master")) {
            String sql = GXSqlDialectSupport.applySingleRowLimit("SELECT 1");
            assertEquals("SELECT * FROM (SELECT 1) gx_tmp_one LIMIT 1", sql);
        }
    }

    @Test
    void applySingleRowLimitUsesCurrentDatasourceDbTypeSqlServer() {
        GXDynamicDbTypeRegistry.register("slave", "sqlserver");
        try (GXDynamicContextHolder.AutoCloseableDataSource ignored = GXDynamicContextHolder.withDataSource("slave")) {
            String sql = GXSqlDialectSupport.applySingleRowLimit("SELECT 1");
            assertEquals("SELECT TOP 1 1", sql);
        }
    }

    @Test
    void applySingleRowLimitKeepsSqlServerOrderByInOuterQuery() {
        GXDynamicDbTypeRegistry.register("slave", "sqlserver");
        try (GXDynamicContextHolder.AutoCloseableDataSource ignored = GXDynamicContextHolder.withDataSource("slave")) {
            String sql = GXSqlDialectSupport.applySingleRowLimit("SELECT id FROM sys_menu ORDER BY created_at DESC");
            assertEquals("SELECT TOP 1 id FROM sys_menu ORDER BY created_at DESC", sql);
        }
    }

    @Test
    void applySingleRowLimitUsesCurrentDatasourceDbTypeOracle() {
        GXDynamicDbTypeRegistry.register("archive", "oracle");
        try (GXDynamicContextHolder.AutoCloseableDataSource ignored = GXDynamicContextHolder.withDataSource("archive")) {
            String sql = GXSqlDialectSupport.applySingleRowLimit("SELECT 1");
            assertEquals("SELECT * FROM (SELECT 1) gx_tmp_one WHERE ROWNUM <= 1", sql);
        }
    }

    @Test
    void applySingleRowLimitUsesCurrentDatasourceDbTypeDb2() {
        GXDynamicDbTypeRegistry.register("report", "db2");
        try (GXDynamicContextHolder.AutoCloseableDataSource ignored = GXDynamicContextHolder.withDataSource("report")) {
            String sql = GXSqlDialectSupport.applySingleRowLimit("SELECT id FROM sys_menu ORDER BY created_at DESC");
            assertEquals("SELECT id FROM sys_menu ORDER BY created_at DESC FETCH FIRST 1 ROW ONLY", sql);
        }
    }

    @Test
    void applySingleRowLimitFallsBackToSystemDbTypeWhenNoDynamicDatasource() {
        withSystemDbType("db2", () -> {
            String sql = GXSqlDialectSupport.applySingleRowLimit("SELECT id FROM sys_menu");

            assertEquals("SELECT id FROM sys_menu FETCH FIRST 1 ROW ONLY", sql);
        });
    }

    @Test
    void renderJsonEqUsesUnquotedScalarComparisonForMysql() {
        String sql = GXSqlDialectSupport.renderJsonEqByDialect("mysql", "u.extra",
                "#{dbQueryParamInnerDto.paramMap.path}", "#{dbQueryParamInnerDto.paramMap.value}");

        assertEquals("JSON_UNQUOTE(JSON_EXTRACT(u.extra, #{dbQueryParamInnerDto.paramMap.path})) = CAST(#{dbQueryParamInnerDto.paramMap.value} AS CHAR)", sql);
    }

    @Test
    void jsonFunctionConditionUsesCurrentDynamicDatasourceDbType() {
        withoutSystemDbType(() -> {
            GXDynamicDbTypeRegistry.register("report", "postgresql");
            try (GXDynamicContextHolder.AutoCloseableDataSource ignored = GXDynamicContextHolder.withDataSource("report")) {
                GXConditionFuncJsonContains condition = new GXConditionFuncJsonContains(
                        "u", "extra", Dict.create().set("name", "alpha"), "profile");

                String sql = condition.toSegment().sql();

                assertTrue(sql.contains("@> CAST(#{dbQueryParamInnerDto.paramMap."), sql);
                assertTrue(sql.contains(" AS jsonb)"), sql);
            }
        });
    }

    @Test
    void jsonUpdateFieldUsesCurrentDynamicDatasourceDbType() {
        withoutSystemDbType(() -> {
            GXDynamicDbTypeRegistry.register("report", "postgresql");
            try (GXDynamicContextHolder.AutoCloseableDataSource ignored = GXDynamicContextHolder.withDataSource("report")) {
                GXUpdateJsonSetStrField field = new GXUpdateJsonSetStrField("u", "extra", "profile.name", "alice");

                String sql = field.updateString();

                assertTrue(sql.contains("u.extra = jsonb_set("), sql);
                assertTrue(sql.contains("to_jsonb(CAST(#{dbQueryParamInnerDto.paramMap."), sql);
            }
        });
    }

    @Test
    void buildExistsQueryUsesMysqlCompatibleSyntax() {
        GXDynamicDbTypeRegistry.register("master", "mysql");
        try (GXDynamicContextHolder.AutoCloseableDataSource ignored = GXDynamicContextHolder.withDataSource("master")) {
            String sql = GXSqlDialectSupport.buildExistsQuery("SELECT 1 FROM sys_menu");
            assertEquals("SELECT EXISTS (SELECT 1 FROM sys_menu)", sql);
        }
    }

    @Test
    void buildExistsQueryUsesPostgresCompatibleSyntax() {
        GXDynamicDbTypeRegistry.register("report", "postgresql");
        try (GXDynamicContextHolder.AutoCloseableDataSource ignored = GXDynamicContextHolder.withDataSource("report")) {
            String sql = GXSqlDialectSupport.buildExistsQuery("SELECT 1 FROM sys_menu");
            assertEquals("SELECT EXISTS (SELECT 1 FROM sys_menu)", sql);
        }
    }

    @Test
    void buildExistsQueryUsesSqlServerCompatibleSyntax() {
        GXDynamicDbTypeRegistry.register("slave", "sqlserver");
        try (GXDynamicContextHolder.AutoCloseableDataSource ignored = GXDynamicContextHolder.withDataSource("slave")) {
            String sql = GXSqlDialectSupport.buildExistsQuery("SELECT 1 FROM sys_menu");
            assertEquals("SELECT CASE WHEN EXISTS (SELECT 1 FROM sys_menu) THEN 1 ELSE 0 END AS exists_result", sql);
        }
    }

    @Test
    void buildExistsQueryUsesOracleCompatibleSyntax() {
        GXDynamicDbTypeRegistry.register("archive", "oracle");
        try (GXDynamicContextHolder.AutoCloseableDataSource ignored = GXDynamicContextHolder.withDataSource("archive")) {
            String sql = GXSqlDialectSupport.buildExistsQuery("SELECT 1 FROM sys_menu");
            assertEquals("SELECT CASE WHEN EXISTS (SELECT 1 FROM sys_menu) THEN 1 ELSE 0 END AS exists_result FROM DUAL", sql);
        }
    }

    @Test
    void buildExistsQueryUsesDb2CompatibleSyntax() {
        GXDynamicDbTypeRegistry.register("report", "db2");
        try (GXDynamicContextHolder.AutoCloseableDataSource ignored = GXDynamicContextHolder.withDataSource("report")) {
            String sql = GXSqlDialectSupport.buildExistsQuery("SELECT 1 FROM sys_menu");
            assertEquals("SELECT CASE WHEN EXISTS (SELECT 1 FROM sys_menu) THEN 1 ELSE 0 END AS exists_result FROM SYSIBM.SYSDUMMY1", sql);
        }
    }

    private static void withoutSystemDbType(Runnable runnable) {
        String oldValue = System.getProperty("spring.datasource.druid.db-type");
        System.clearProperty("spring.datasource.druid.db-type");
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

    private static void withSystemDbType(String dbType, Runnable runnable) {
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
