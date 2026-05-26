package cn.maple.core.datasource.builder;

import cn.maple.core.datasource.config.GXDynamicContextHolder;
import cn.maple.core.datasource.config.GXDynamicDbTypeRegistry;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
            assertEquals("SELECT TOP 1 * FROM (SELECT 1) gx_tmp_one", sql);
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
}
