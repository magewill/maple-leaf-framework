package cn.maple.core.datasource.builder;

import cn.hutool.core.collection.CollUtil;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXJoinDto;
import cn.maple.core.framework.dto.inner.GXJoinTypeEnums;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionRaw;
import cn.maple.core.framework.dto.inner.op.GXDbJoinEQ;
import cn.maple.core.framework.dto.inner.op.GXDbJoinValueEQ;
import cn.maple.core.framework.exception.GXDBConditionException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXCommonUtils;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.jdbc.SQL;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXBaseBuilderTest {

    @Test
    void deleteConditionRejectsUnsafeTableName() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("user_table where 1=1")
                .condition(CollUtil.newArrayList(new GXConditionEQ("", "id", 1)))
                .build();

        assertThrows(GXDBConditionException.class, () -> GXBaseBuilder.deleteCondition(query));
    }

    @Test
    void deleteConditionKeepsSafeQualifiedTableName() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("tenant_a.user_table")
                .condition(CollUtil.newArrayList(new GXConditionEQ("", "id", 1)))
                .build();

        String sql = GXBaseBuilder.deleteCondition(query);

        assertTrue(sql.contains("DELETE FROM tenant_a.user_table"));
    }

    @Test
    void copyQueryParamUsesIndependentCollectionAndParamMapContainers() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("user")
                .condition(CollUtil.newArrayList(new GXConditionEQ("u", "id", 1)))
                .joins(CollUtil.newArrayList(GXJoinDto.builder()
                        .joinTableName("order")
                        .joinTableNameAlias("o")
                        .masterTableName("order")
                        .masterTableNameAlias("o")
                        .joinType(GXJoinTypeEnums.LEFT)
                        .and(CollUtil.newArrayList(new GXDbJoinEQ("id", "user_id")))
                        .build()))
                .paramMap(Map.of("existing", 1))
                .build();

        GXBaseQueryParamInnerDto copied = cn.maple.core.datasource.util.GXQueryParamUtils.copy(query);

        assertNotSame(query.getCondition(), copied.getCondition());
        assertNotSame(query.getJoins(), copied.getJoins());
        assertNotSame(query.getParamMap(), copied.getParamMap());
        copied.getCondition().clear();
        copied.getJoins().clear();
        copied.getParamMap().put("copied", 2);
        assertEquals(1, query.getCondition().size());
        assertEquals(1, query.getJoins().size());
        assertFalse(query.getParamMap().containsKey("copied"));
    }

    @Test
    void findByConditionRejectsUnsafeTableAlias() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as id)")
                .tableNameAlias("tmp where 1=1")
                .columns(CollUtil.newLinkedHashSet("id"))
                .build();

        assertThrows(GXDBConditionException.class, () -> GXBaseBuilder.findByCondition(query));
    }

    @Test
    void findByConditionUsesSafeDefaultAliasForDerivedTable() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as id)")
                .columns(CollUtil.newLinkedHashSet("id"))
                .build();

        String sql = GXBaseBuilder.findByCondition(query);

        assertTrue(sql.contains("FROM (select 1 as id) tmp"), sql);
    }

    @Test
    void findByConditionRejectsUnsafeDerivedTableExpression() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as id; select 2 as id)")
                .columns(CollUtil.newLinkedHashSet("id"))
                .build();

        assertThrows(GXSqlInjectionException.class, () -> GXBaseBuilder.findByCondition(query));
    }

    @Test
    void expressionWhitelistIgnoresQuotedStringLiterals() {
        Set<String> allowedColumns = new HashSet<>();
        allowedColumns.add("t.created_at");
        allowedColumns.add("created_at");

        assertDoesNotThrow(() -> GXSqlExpressionIdentifierRenderer.validateColumnRefsInExpression(
                "DATE_FORMAT(t.created_at, '%Y-%m') AS stat_month", allowedColumns, "SELECT"));
    }

    @Test
    void joinValueConditionUsesParameterMap() {
        SQL sql = new SQL().SELECT("u.*").FROM("user u");
        GXJoinDto join = GXJoinDto.builder()
                .joinTableName("order")
                .joinTableNameAlias("o")
                .masterTableName("order")
                .masterTableNameAlias("o")
                .joinType(GXJoinTypeEnums.LEFT)
                .and(CollUtil.newArrayList(new GXDbJoinEQ("id", "user_id"), new GXDbJoinValueEQ("o", "status", "paid")))
                .build();

        Map<String, Object> params = GXBaseBuilder.handleSQLJoin(sql, CollUtil.newArrayList(join));
        String renderedSql = sql.toString();

        assertTrue(renderedSql.contains("dbQueryParamInnerDto.paramMap.join_"), renderedSql);
        assertFalse(renderedSql.contains("status=paid"));
        assertTrue(params.containsValue("paid"));
    }

    @Test
    void joinValueConditionUsesDefaultJoinAliasWhenAliasIsBlank() {
        SQL sql = new SQL().SELECT("u.*").FROM("user u");
        GXJoinDto join = GXJoinDto.builder()
                .joinTableName("order")
                .joinTableNameAlias("o")
                .masterTableName("order")
                .masterTableNameAlias("o")
                .joinType(GXJoinTypeEnums.LEFT)
                .and(CollUtil.newArrayList(new GXDbJoinEQ("id", "user_id"), new GXDbJoinValueEQ("", "status", "paid")))
                .build();

        Map<String, Object> params = GXBaseBuilder.handleSQLJoin(sql, CollUtil.newArrayList(join));
        String renderedSql = sql.toString();

        assertTrue(renderedSql.contains("o.status=#{dbQueryParamInnerDto.paramMap.join_"), renderedSql);
        assertFalse(renderedSql.contains("(.status="), renderedSql);
        assertTrue(params.containsValue("paid"));
    }

    @Test
    void joinOpStringDoesNotMutateFieldsAcrossCalls() {
        GXDbJoinEQ op = new GXDbJoinEQ("id", "user_id");
        op.setMasterTableNameAlias("o");
        op.setJoinTableNameAlias("u");

        assertEquals("o.id=u.user_id", op.opString());
        assertEquals("o.id=u.user_id", op.opString());
    }

    @Test
    void handleSQLJoinDoesNotMutateDefaultedOpAliases() {
        SQL sql = new SQL().SELECT("u.*").FROM("user u");
        GXDbJoinEQ op = new GXDbJoinEQ("id", "user_id");
        GXJoinDto join = GXJoinDto.builder()
                .joinTableName("user")
                .joinTableNameAlias("u")
                .masterTableName("order")
                .masterTableNameAlias("o")
                .joinType(GXJoinTypeEnums.LEFT)
                .and(CollUtil.newArrayList(op))
                .build();

        GXBaseBuilder.handleSQLJoin(sql, CollUtil.newArrayList(join));

        assertTrue(sql.toString().contains("o.id=u.user_id"), sql.toString());
        assertEquals(null, op.getMasterTableNameAlias());
        assertEquals(null, op.getJoinTableNameAlias());
    }

    @Test
    void handleSQLJoinRejectsUnsafeTableAlias() {
        SQL sql = new SQL().SELECT("u.*").FROM("user u");
        GXJoinDto join = GXJoinDto.builder()
                .joinTableName("user")
                .joinTableNameAlias("u on 1=1")
                .masterTableName("order")
                .masterTableNameAlias("o")
                .joinType(GXJoinTypeEnums.LEFT)
                .and(CollUtil.newArrayList(new GXDbJoinEQ("id", "user_id")))
                .build();

        assertThrows(GXDBConditionException.class, () -> GXBaseBuilder.handleSQLJoin(sql, CollUtil.newArrayList(join)));
    }

    @Test
    void handleSQLJoinRejectsUnsafeAliasDeclaredOnJoinOp() {
        SQL sql = new SQL().SELECT("u.*").FROM("user u");
        GXDbJoinEQ op = new GXDbJoinEQ("id", "user_id");
        op.setMasterTableNameAlias("o where 1=1");
        GXJoinDto join = GXJoinDto.builder()
                .joinTableName("user")
                .joinTableNameAlias("u")
                .masterTableName("order")
                .masterTableNameAlias("o")
                .joinType(GXJoinTypeEnums.LEFT)
                .and(CollUtil.newArrayList(op))
                .build();

        assertThrows(GXDBConditionException.class, () -> GXBaseBuilder.handleSQLJoin(sql, CollUtil.newArrayList(join)));
    }

    @Test
    void handleSQLJoinRejectsUnsafeJoinedTableExpression() {
        SQL sql = new SQL().SELECT("u.*").FROM("user u");
        GXJoinDto join = GXJoinDto.builder()
                .joinTableName("order")
                .joinTableNameAlias("o")
                .masterTableName("order where 1=1")
                .masterTableNameAlias("o")
                .joinType(GXJoinTypeEnums.LEFT)
                .and(CollUtil.newArrayList(new GXDbJoinEQ("id", "user_id")))
                .build();

        assertThrows(GXDBConditionException.class, () -> GXBaseBuilder.handleSQLJoin(sql, CollUtil.newArrayList(join)));
    }

    @Test
    void handleSQLJoinRejectsMissingJoinType() {
        SQL sql = new SQL().SELECT("u.*").FROM("user u");
        GXJoinDto join = GXJoinDto.builder()
                .joinTableName("order")
                .joinTableNameAlias("o")
                .masterTableName("order")
                .masterTableNameAlias("o")
                .and(CollUtil.newArrayList(new GXDbJoinEQ("id", "user_id")))
                .build();

        assertThrows(GXDBConditionException.class, () -> GXBaseBuilder.handleSQLJoin(sql, CollUtil.newArrayList(join)));
    }

    @Test
    void selectKeepsComplexExpressionAndNormalizesAliasByDefault() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as amount)")
                .tableNameAlias("tmp")
                .columns(CollUtil.newLinkedHashSet("SUM(amount) AS totalAmount"))
                .build();

        String sql = GXBaseBuilder.findByCondition(query);

        assertTrue(sql.contains("SELECT SUM(amount) AS total_amount"), sql);
        assertFalse(sql.contains("s_u_m"), sql);
    }

    @Test
    void joinLogicDeleteConditionWrapsWholeOrClause() {
        SQL sql = new SQL().SELECT("u.*").FROM("user u");
        GXJoinDto join = GXJoinDto.builder()
                .joinTableName("order")
                .joinTableNameAlias("o")
                .masterTableName("order")
                .masterTableNameAlias("o")
                .joinType(GXJoinTypeEnums.LEFT)
                .autoFillIsDeleteCondition(true)
                .and(CollUtil.newArrayList(new GXDbJoinEQ("id", "user_id")))
                .or(CollUtil.newArrayList(new GXDbJoinEQ("legacy_id", "user_id")))
                .build();
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        TableFieldInfo logicFieldInfo = Mockito.mock(TableFieldInfo.class);

        try (MockedStatic<TableInfoHelper> tableInfoHelper = Mockito.mockStatic(TableInfoHelper.class)) {
            tableInfoHelper.when(() -> TableInfoHelper.getTableInfo("order")).thenReturn(tableInfo);
            Mockito.when(tableInfo.getLogicDeleteFieldInfo()).thenReturn(logicFieldInfo);
            Mockito.when(logicFieldInfo.getColumn()).thenReturn("is_deleted");
            Mockito.when(logicFieldInfo.getLogicNotDeleteValue()).thenReturn("0");

            GXBaseBuilder.handleSQLJoin(sql, CollUtil.newArrayList(join));
        }

        String renderedSql = sql.toString();
        String normalizedSql = renderedSql.replaceAll("\\s+", " ");
        int orIndex = renderedSql.indexOf(" OR ");
        int logicIndex = renderedSql.indexOf("o.is_deleted = 0");

        assertTrue(orIndex > 0, renderedSql);
        assertTrue(logicIndex > orIndex, renderedSql);
        assertTrue(normalizedSql.contains("((o.id=o.user_id) OR (o.legacy_id=o.user_id)) AND (o.is_deleted = 0)"), renderedSql);
    }

    @Test
    void findByConditionCachesTableInfoDuringSingleSqlBuild() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("order")
                .tableNameAlias("o")
                .columns(CollUtil.newLinkedHashSet("id"))
                .build();
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        TableFieldInfo deletedFieldInfo = Mockito.mock(TableFieldInfo.class);

        try (MockedStatic<TableInfoHelper> tableInfoHelper = Mockito.mockStatic(TableInfoHelper.class)) {
            tableInfoHelper.when(() -> TableInfoHelper.getTableInfo("order")).thenReturn(tableInfo);
            Mockito.when(tableInfo.getKeyColumn()).thenReturn("id");
            Mockito.when(tableInfo.getFieldList()).thenReturn(CollUtil.newArrayList(deletedFieldInfo));
            Mockito.when(tableInfo.getLogicDeleteFieldInfo()).thenReturn(null);
            Mockito.when(deletedFieldInfo.getColumn()).thenReturn("is_deleted");

            String sql = GXBaseBuilder.findByCondition(query);

            assertTrue(sql.contains("o.is_deleted = 0"), sql);
            tableInfoHelper.verify(() -> TableInfoHelper.getTableInfo("order"), Mockito.times(1));
        }
    }

    @Test
    void joinDefaultAliasForQualifiedTableIsRegisteredInWhitelist() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as agency_id)")
                .tableNameAlias("tmp")
                .columns(CollUtil.newLinkedHashSet("agency.name AS agencyName"))
                .joins(CollUtil.newArrayList(GXJoinDto.builder()
                        .joinTableName("(select 1 as agency_id)")
                        .joinTableNameAlias("tmp")
                        .masterTableName("tenant_a.agency")
                        .joinType(GXJoinTypeEnums.INNER)
                        .and(CollUtil.newArrayList(new GXDbJoinEQ("id", "agency_id")))
                        .build()))
                .build();
        TableInfo agencyTableInfo = Mockito.mock(TableInfo.class);
        TableFieldInfo nameFieldInfo = Mockito.mock(TableFieldInfo.class);

        try (MockedStatic<TableInfoHelper> tableInfoHelper = Mockito.mockStatic(TableInfoHelper.class)) {
            tableInfoHelper.when(() -> TableInfoHelper.getTableInfo("tenant_a.agency")).thenReturn(agencyTableInfo);
            Mockito.when(agencyTableInfo.getKeyColumn()).thenReturn("id");
            Mockito.when(agencyTableInfo.getFieldList()).thenReturn(CollUtil.newArrayList(nameFieldInfo));
            Mockito.when(nameFieldInfo.getColumn()).thenReturn("name");

            String sql = GXBaseBuilder.findByCondition(query);

            assertTrue(sql.contains("SELECT agency.name AS agency_name"), sql);
            assertTrue(sql.contains("INNER JOIN tenant_a.agency agency ON (agency.id=tmp.agency_id)"), sql);
        }
    }

    @Test
    void orderByRejectsFieldOutsideSelectOutputForDerivedTableWhenAutoUnderlineDisabled() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as id)")
                .tableNameAlias("tmp")
                .columns(CollUtil.newLinkedHashSet("id"))
                .orderByField(Map.of("badColumn", "ASC"))
                .build();

        try (MockedStatic<GXCommonUtils> commonUtils = Mockito.mockStatic(GXCommonUtils.class)) {
            commonUtils.when(() -> GXCommonUtils.getEnvironmentValue(
                    GXBaseBuilder.AUTO_UNDERLINE_FIELD_ENABLED_KEY, Boolean.class, Boolean.TRUE)
            ).thenReturn(Boolean.FALSE);

            assertThrows(GXDBConditionException.class, () -> GXBaseBuilder.findByCondition(query));
        }
    }

    @Test
    void groupByRejectsFieldOutsideSelectOutputForDerivedTableWhenAutoUnderlineDisabled() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as id)")
                .tableNameAlias("tmp")
                .columns(CollUtil.newLinkedHashSet("id"))
                .groupByField(CollUtil.newLinkedHashSet("badColumn"))
                .build();

        try (MockedStatic<GXCommonUtils> commonUtils = Mockito.mockStatic(GXCommonUtils.class)) {
            commonUtils.when(() -> GXCommonUtils.getEnvironmentValue(
                    GXBaseBuilder.AUTO_UNDERLINE_FIELD_ENABLED_KEY, Boolean.class, Boolean.TRUE)
            ).thenReturn(Boolean.FALSE);

            assertThrows(GXDBConditionException.class, () -> GXBaseBuilder.findByCondition(query));
        }
    }

    @Test
    void defaultGroupOrderCompatibilityAllowsSelfRegisteredFields() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as id)")
                .tableNameAlias("tmp")
                .columns(CollUtil.newLinkedHashSet("id"))
                .groupByField(CollUtil.newLinkedHashSet("badColumn"))
                .orderByField(Map.of("badColumn", "ASC"))
                .build();

        String sql = GXBaseBuilder.findByCondition(query);

        assertTrue(sql.contains("GROUP BY bad_column"));
        assertTrue(sql.contains("ORDER BY bad_column ASC"));
    }

    @Test
    void defaultSelectAliasAndOrderByUseUnderlineAlias() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as amount)")
                .tableNameAlias("tmp")
                .columns(CollUtil.newLinkedHashSet("SUM(amount) AS salesTotalPrice"))
                .orderByField(Map.of("salesTotalPrice", "DESC"))
                .build();

        String sql = GXBaseBuilder.findByCondition(query);

        assertTrue(sql.contains("SUM(amount) AS sales_total_price"), sql);
        assertTrue(sql.contains("ORDER BY sales_total_price DESC"), sql);
    }

    @Test
    void disablingAutoUnderlineKeepsCamelAliasInSelectAndOrderBy() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as amount)")
                .tableNameAlias("tmp")
                .columns(CollUtil.newLinkedHashSet("SUM(amount) AS salesTotalPrice"))
                .orderByField(Map.of("salesTotalPrice", "DESC"))
                .build();

        try (MockedStatic<GXCommonUtils> commonUtils = Mockito.mockStatic(GXCommonUtils.class)) {
            commonUtils.when(() -> GXCommonUtils.getEnvironmentValue(
                    GXBaseBuilder.AUTO_UNDERLINE_FIELD_ENABLED_KEY, Boolean.class, Boolean.TRUE)
            ).thenReturn(Boolean.FALSE);

            String sql = GXBaseBuilder.findByCondition(query);

            assertTrue(sql.contains("SUM(amount) AS salesTotalPrice"), sql);
            assertTrue(sql.contains("ORDER BY salesTotalPrice DESC"), sql);
        }
    }

    @Test
    void disablingAutoUnderlineKeepsSimpleSelectFieldOriginal() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as salesTotalPrice)")
                .tableNameAlias("tmp")
                .columns(CollUtil.newLinkedHashSet("salesTotalPrice"))
                .build();

        try (MockedStatic<GXCommonUtils> commonUtils = Mockito.mockStatic(GXCommonUtils.class)) {
            commonUtils.when(() -> GXCommonUtils.getEnvironmentValue(
                    GXBaseBuilder.AUTO_UNDERLINE_FIELD_ENABLED_KEY, Boolean.class, Boolean.TRUE)
            ).thenReturn(Boolean.FALSE);

            String sql = GXBaseBuilder.findByCondition(query);

            assertTrue(sql.contains("SELECT salesTotalPrice"), sql);
            assertFalse(sql.contains("sales_total_price"), sql);
        }
    }

    @Test
    void defaultSelectExpressionFieldsUseUnderline() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as pay_price)")
                .tableNameAlias("tmp")
                .columns(CollUtil.newLinkedHashSet("SUM(payPrice) AS salesTotalPrice"))
                .build();

        String sql = GXBaseBuilder.findByCondition(query);

        assertTrue(sql.contains("SUM(pay_price) AS sales_total_price"), sql);
    }

    @Test
    void defaultSelectExpressionKeepsSqlTypeKeywordsWhenRenderingUnderline() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as pay_price)")
                .tableNameAlias("tmp")
                .columns(CollUtil.newLinkedHashSet("CAST(payPrice AS SIGNED) AS signedPayPrice"))
                .build();

        String sql = GXBaseBuilder.findByCondition(query);

        assertTrue(sql.contains("CAST(pay_price AS SIGNED) AS signed_pay_price"), sql);
        assertFalse(sql.contains("s_i_g_n_e_d"), sql);
    }

    @Test
    void havingRejectsFunctionOutsideWhitelist() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as amount)")
                .tableNameAlias("tmp")
                .columns(CollUtil.newLinkedHashSet("amount"))
                .groupByField(CollUtil.newLinkedHashSet("amount"))
                .having(CollUtil.newLinkedHashSet("database() = 'test'"))
                .build();

        assertThrows(GXDBConditionException.class, () -> GXBaseBuilder.findByCondition(query));
    }

    @Test
    void havingAllowsWhitelistedFunctionAndIgnoresQuotedLiterals() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as amount)")
                .tableNameAlias("tmp")
                .columns(CollUtil.newLinkedHashSet("amount"))
                .groupByField(CollUtil.newLinkedHashSet("amount"))
                .having(CollUtil.newLinkedHashSet("SUM(amount) > 0 AND 'pending' IS NOT NULL"))
                .build();

        String sql = GXBaseBuilder.findByCondition(query);

        assertTrue(sql.contains("HAVING (SUM(amount) > 0 AND 'pending' IS NOT NULL)"));
    }

    @Test
    void defaultHavingFieldsUseUnderlineAndKeepQuotedLiterals() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as sales_total_price)")
                .tableNameAlias("tmp")
                .columns(CollUtil.newLinkedHashSet("salesTotalPrice"))
                .groupByField(CollUtil.newLinkedHashSet("salesTotalPrice"))
                .having(CollUtil.newLinkedHashSet("SUM(salesTotalPrice) > 0 AND 'salesTotalPrice' IS NOT NULL"))
                .build();

        String sql = GXBaseBuilder.findByCondition(query);

        assertTrue(sql.contains("GROUP BY sales_total_price"), sql);
        assertTrue(sql.contains("HAVING (SUM(sales_total_price) > 0 AND 'salesTotalPrice' IS NOT NULL)"), sql);
    }

    @Test
    void disablingAutoUnderlineKeepsHavingFieldOriginal() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as salesTotalPrice)")
                .tableNameAlias("tmp")
                .columns(CollUtil.newLinkedHashSet("salesTotalPrice"))
                .groupByField(CollUtil.newLinkedHashSet("salesTotalPrice"))
                .having(CollUtil.newLinkedHashSet("salesTotalPrice > 0"))
                .build();

        try (MockedStatic<GXCommonUtils> commonUtils = Mockito.mockStatic(GXCommonUtils.class)) {
            commonUtils.when(() -> GXCommonUtils.getEnvironmentValue(
                    GXBaseBuilder.AUTO_UNDERLINE_FIELD_ENABLED_KEY, Boolean.class, Boolean.TRUE)
            ).thenReturn(Boolean.FALSE);

            String sql = GXBaseBuilder.findByCondition(query);

            assertTrue(sql.contains("GROUP BY salesTotalPrice"), sql);
            assertTrue(sql.contains("HAVING (salesTotalPrice > 0)"), sql);
        }
    }

    @Test
    void disablingAutoUnderlineKeepsComplexGroupAndOrderExpressionsOriginal() {
        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as payPrice, 2 as salesTotalPrice)")
                .tableNameAlias("tmp")
                .columns(CollUtil.newLinkedHashSet("SUM(payPrice) AS salesTotalPrice"))
                .groupByField(CollUtil.newLinkedHashSet("DATE_FORMAT(salesTotalPrice, '%Y-%m')"))
                .orderByField(Map.of("salesTotalPrice", "DESC"))
                .build();

        try (MockedStatic<GXCommonUtils> commonUtils = Mockito.mockStatic(GXCommonUtils.class)) {
            commonUtils.when(() -> GXCommonUtils.getEnvironmentValue(
                    GXBaseBuilder.AUTO_UNDERLINE_FIELD_ENABLED_KEY, Boolean.class, Boolean.TRUE)
            ).thenReturn(Boolean.FALSE);

            String sql = GXBaseBuilder.findByCondition(query);

            assertTrue(sql.contains("SUM(payPrice) AS salesTotalPrice"), sql);
            assertTrue(sql.contains("GROUP BY DATE_FORMAT(salesTotalPrice, '%Y-%m')"), sql);
            assertTrue(sql.contains("ORDER BY salesTotalPrice DESC"), sql);
        }
    }

    @Test
    void unionFindOneAppliesSingleRowLimitAndRestoresBranchAlias() {
        GXBaseQueryParamInnerDto root = GXBaseQueryParamInnerDto.builder()
                .tableName("ignored")
                .columns(CollUtil.newLinkedHashSet("id"))
                .build();
        GXBaseQueryParamInnerDto branch = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as id)")
                .columns(CollUtil.newLinkedHashSet("id"))
                .build();

        String sql = GXBaseBuilder.unionFindOneByCondition(root, CollUtil.newArrayList(branch), GXUnionTypeEnums.UNION_ALL);

        assertTrue(sql.toLowerCase().contains("limit 1"));
        assertEquals("ignored", root.getTableName());
        assertEquals(null, branch.getTableNameAlias());
    }

    @Test
    void unionFindByConditionDoesNotMutateRootConditionAlias() {
        GXConditionEQ rootCondition = new GXConditionEQ("u", "id", 1);
        GXBaseQueryParamInnerDto root = GXBaseQueryParamInnerDto.builder()
                .tableName("ignored")
                .columns(CollUtil.newLinkedHashSet("id"))
                .condition(CollUtil.newArrayList(rootCondition))
                .build();
        GXBaseQueryParamInnerDto branch = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as id)")
                .columns(CollUtil.newLinkedHashSet("id"))
                .build();

        String sql = GXBaseBuilder.unionFindByCondition(root, CollUtil.newArrayList(branch), GXUnionTypeEnums.UNION_ALL);

        assertTrue(sql.contains("tmp.id = #{dbQueryParamInnerDto.paramMap."), sql);
        assertEquals("u", rootCondition.getTableNameAlias());
        assertEquals("ignored", root.getTableName());
        assertEquals(null, root.getTableNameAlias());
    }

    @Test
    void unionFindByConditionKeepsRawRootConditionWithOuterAliasOverride() {
        GXBaseQueryParamInnerDto root = GXBaseQueryParamInnerDto.builder()
                .tableName("ignored")
                .columns(CollUtil.newLinkedHashSet("id"))
                .condition(CollUtil.newArrayList(new GXConditionRaw("1 = 1")))
                .build();
        GXBaseQueryParamInnerDto branch = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as id)")
                .columns(CollUtil.newLinkedHashSet("id"))
                .build();

        String sql = GXBaseBuilder.unionFindByCondition(root, CollUtil.newArrayList(branch), GXUnionTypeEnums.UNION_ALL);

        assertTrue(sql.contains("1 = 1"), sql);
    }

    @Test
    void unionFindByConditionNamespacesSharedBranchConditionParams() {
        GXConditionEQ sharedCondition = new GXConditionEQ("", "status", 1);
        GXBaseQueryParamInnerDto root = GXBaseQueryParamInnerDto.builder()
                .tableName("ignored")
                .columns(CollUtil.newLinkedHashSet("id"))
                .build();
        GXBaseQueryParamInnerDto firstBranch = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 1 as id, 1 as status)")
                .columns(CollUtil.newLinkedHashSet("id"))
                .condition(CollUtil.newArrayList(sharedCondition))
                .build();
        GXBaseQueryParamInnerDto secondBranch = GXBaseQueryParamInnerDto.builder()
                .tableName("(select 2 as id, 2 as status)")
                .columns(CollUtil.newLinkedHashSet("id"))
                .condition(CollUtil.newArrayList(sharedCondition))
                .build();

        String sql = GXBaseBuilder.unionFindByCondition(root, CollUtil.newArrayList(firstBranch, secondBranch), GXUnionTypeEnums.UNION_ALL);

        long unionParamCount = root.getParamMap().keySet().stream()
                .filter(key -> key.startsWith("union_") && key.endsWith(sharedCondition.getParamName()))
                .count();
        assertEquals(2, unionParamCount);
        assertEquals(2, countOccurrences(sql, sharedCondition.getParamName()));
        assertEquals(2, root.getParamMap().size());
    }

    @Test
    void unionBranchWithQualifiedTableNameUsesSafeDefaultAlias() {
        GXBaseQueryParamInnerDto root = GXBaseQueryParamInnerDto.builder()
                .tableName("ignored")
                .columns(CollUtil.newLinkedHashSet("id"))
                .build();
        GXBaseQueryParamInnerDto branch = GXBaseQueryParamInnerDto.builder()
                .tableName("tenant_a.user")
                .columns(CollUtil.newLinkedHashSet("*"))
                .build();
        TableInfo tableInfo = Mockito.mock(TableInfo.class);

        try (MockedStatic<TableInfoHelper> tableInfoHelper = Mockito.mockStatic(TableInfoHelper.class)) {
            tableInfoHelper.when(() -> TableInfoHelper.getTableInfo("tenant_a.user")).thenReturn(tableInfo);
            Mockito.when(tableInfo.getKeyColumn()).thenReturn("id");
            Mockito.when(tableInfo.getFieldList()).thenReturn(java.util.Collections.emptyList());

            String sql = GXBaseBuilder.unionFindByCondition(root, CollUtil.newArrayList(branch), GXUnionTypeEnums.UNION_ALL);

            assertTrue(sql.contains("FROM tenant_a.user user"), sql);
        }
        assertEquals(null, branch.getTableNameAlias());
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
