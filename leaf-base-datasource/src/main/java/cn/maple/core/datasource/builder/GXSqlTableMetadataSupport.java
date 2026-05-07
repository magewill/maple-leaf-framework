package cn.maple.core.datasource.builder;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.dto.inner.GXJoinDto;
import cn.maple.core.framework.exception.GXDBConditionException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import com.baomidou.mybatisplus.core.metadata.TableInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class GXSqlTableMetadataSupport {
    private GXSqlTableMetadataSupport() {
    }

    static String validateMutableTableName(String tableName) {
        String trimmed = CharSequenceUtil.trim(tableName);
        if (CharSequenceUtil.isBlank(trimmed)) {
            throw new GXDBConditionException("Mutable SQL table name must not be blank");
        }
        if (!GXBaseBuilder.SAFE_IDENTIFIER_PATTERN.matcher(trimmed).matches()) {
            throw new GXDBConditionException(CharSequenceUtil.format("Mutable SQL table name is invalid: {}", tableName));
        }
        if (GXDBStringEscapeUtils.check(trimmed)) {
            throw new GXSqlInjectionException(CharSequenceUtil.format("Mutable SQL table name has SQL injection risk: {}", tableName));
        }
        return trimmed;
    }

    static String validateQueryableTableExpression(String tableName, String scope) {
        String trimmed = CharSequenceUtil.trim(tableName);
        if (CharSequenceUtil.isBlank(trimmed)) {
            throw new GXDBConditionException(CharSequenceUtil.format("{} table name must not be blank", scope));
        }
        if (trimmed.startsWith("(")) {
            return validateDerivedTableExpression(trimmed, scope);
        }
        if (!GXBaseBuilder.SAFE_IDENTIFIER_PATTERN.matcher(trimmed).matches()) {
            throw new GXDBConditionException(CharSequenceUtil.format("{} table name is invalid: {}", scope, tableName));
        }
        if (GXDBStringEscapeUtils.check(trimmed)) {
            throw new GXSqlInjectionException(CharSequenceUtil.format("{} table name has SQL injection risk: {}", scope, tableName));
        }
        return trimmed;
    }

    static String resolveTableAlias(String tableName, String tableAlias, String scope) {
        return validateTableAlias(CharSequenceUtil.isBlank(tableAlias) ? defaultAliasForTable(tableName) : tableAlias, scope);
    }

    static String resolveOptionalTableAlias(String tableName, String tableAlias, String scope) {
        if (CharSequenceUtil.isBlank(tableName) && CharSequenceUtil.isBlank(tableAlias)) {
            return "";
        }
        return resolveTableAlias(tableName, tableAlias, scope);
    }

    static String defaultAliasForTable(String tableName) {
        String trimmed = CharSequenceUtil.trim(tableName);
        if (CharSequenceUtil.isBlank(trimmed) || trimmed.startsWith("(")) {
            return "tmp";
        }
        return extractBareColumnName(trimmed);
    }

    static String validateTableAlias(String tableAlias, String scope) {
        String trimmed = CharSequenceUtil.trim(tableAlias);
        if (CharSequenceUtil.isBlank(trimmed)) {
            throw new GXDBConditionException(CharSequenceUtil.format("{} table alias must not be blank", scope));
        }
        if (!GXBaseBuilder.SAFE_ALIAS_PATTERN.matcher(trimmed).matches()) {
            throw new GXDBConditionException(CharSequenceUtil.format("{} table alias is invalid: {}", scope, tableAlias));
        }
        if (GXDBStringEscapeUtils.check(trimmed)) {
            throw new GXSqlInjectionException(CharSequenceUtil.format("{} table alias has SQL injection risk: {}", scope, tableAlias));
        }
        return trimmed;
    }

    static Set<String> buildAllowedColumns(GXSqlBuildContext context, String tableName, String tableAlias, List<GXJoinDto> joins) {
        Set<String> allowedColumns = new HashSet<>();
        ensureWhitelistCoverage(context, allowedColumns, tableName, tableAlias, "MAIN");
        if (CollUtil.isNotEmpty(joins)) {
            for (GXJoinDto join : joins) {
                String joinTableName = join.getJoinTableName();
                if (CharSequenceUtil.isNotBlank(joinTableName)) {
                    String joinTableAlias = resolveOptionalTableAlias(joinTableName, join.getJoinTableNameAlias(), "JOIN");
                    ensureWhitelistCoverage(context, allowedColumns, joinTableName, joinTableAlias, "JOIN");
                } else {
                    GXBaseBuilder.LOGGER.warn("JOIN table name is blank, skipping whitelist coverage for alias [{}]", join.getJoinTableNameAlias());
                }
                String masterTableName = join.getMasterTableName();
                if (CharSequenceUtil.isNotBlank(masterTableName)
                        && !CharSequenceUtil.equalsIgnoreCase(masterTableName, tableName)) {
                    String masterTableAlias = resolveOptionalTableAlias(masterTableName, join.getMasterTableNameAlias(), "JOIN_MASTER");
                    ensureWhitelistCoverage(context, allowedColumns, masterTableName, masterTableAlias, "JOIN_MASTER");
                }
            }
        }
        if (allowedColumns.isEmpty()) {
            GXBaseBuilder.LOGGER.warn("No allowed columns were resolved for table [{}], whitelist will be empty.", tableName);
        }
        return allowedColumns;
    }

    static void ensureWhitelistCoverage(GXSqlBuildContext context, Set<String> allowedColumns, String tableName, String tableAlias, String scope) {
        if (CharSequenceUtil.isBlank(tableName) || tableName.trim().startsWith("(")) {
            return;
        }
        boolean loaded = collectAllowedColumns(context, allowedColumns, tableName, tableAlias);
        if (!loaded) {
            throw new GXDBConditionException(CharSequenceUtil.format("{} table [{}] has no TableInfo metadata, cannot build whitelist", scope, tableName));
        }
    }

    static boolean collectAllowedColumns(GXSqlBuildContext context, Set<String> allowedColumns, String tableName, String tableAlias) {
        TableInfo tableInfo = getTableInfoSafely(context, tableName);
        if (Objects.isNull(tableInfo)) {
            return false;
        }
        String keyColumn = tableInfo.getKeyColumn();
        if (CharSequenceUtil.isNotBlank(keyColumn)) {
            registerAllowedColumn(allowedColumns, keyColumn, tableName, tableAlias);
        }
        CollUtil.emptyIfNull(tableInfo.getFieldList()).forEach(fieldInfo -> {
            String column = fieldInfo.getColumn();
            if (CharSequenceUtil.isNotBlank(column)) {
                registerAllowedColumn(allowedColumns, column, tableName, tableAlias);
            }
        });
        return true;
    }

    static void registerAllowedColumn(Set<String> allowedColumns, String column, String tableName, String tableAlias) {
        String normalizedColumn = column.toLowerCase(Locale.ROOT);
        allowedColumns.add(normalizedColumn);
        if (CharSequenceUtil.isNotBlank(tableName)) {
            allowedColumns.add(CharSequenceUtil.format("{}.{}", tableName.toLowerCase(Locale.ROOT), normalizedColumn));
            allowedColumns.add(CharSequenceUtil.format("{}.*", tableName.toLowerCase(Locale.ROOT)));
        }
        if (CharSequenceUtil.isNotBlank(tableAlias)) {
            allowedColumns.add(CharSequenceUtil.format("{}.{}", tableAlias.toLowerCase(Locale.ROOT), normalizedColumn));
            allowedColumns.add(CharSequenceUtil.format("{}.*", tableAlias.toLowerCase(Locale.ROOT)));
        }
    }

    static boolean hasColumn(GXSqlBuildContext context, String tableName, String columnName) {
        if (CharSequenceUtil.isBlank(tableName) || CharSequenceUtil.isBlank(columnName)) {
            return false;
        }
        TableInfo tableInfo = getTableInfoSafely(context, tableName);
        return hasColumn(tableInfo, columnName);
    }

    static boolean hasColumn(TableInfo tableInfo, String columnName) {
        if (CharSequenceUtil.isBlank(columnName) || Objects.isNull(tableInfo)) {
            return false;
        }
        String keyColumn = tableInfo.getKeyColumn();
        if (CharSequenceUtil.isNotBlank(keyColumn) && CharSequenceUtil.equalsIgnoreCase(keyColumn, columnName)) {
            return true;
        }
        return CollUtil.emptyIfNull(tableInfo.getFieldList()).stream()
                .anyMatch(fieldInfo -> CharSequenceUtil.equalsIgnoreCase(fieldInfo.getColumn(), columnName));
    }

    static TableInfo getTableInfoSafely(GXSqlBuildContext context, String tableName) {
        return Optional.ofNullable(context).orElseGet(GXSqlBuildContext::new).tableInfo(tableName);
    }

    static String extractBareColumnName(String identifier) {
        int dotIndex = identifier.lastIndexOf('.');
        if (dotIndex >= 0) {
            return identifier.substring(dotIndex + 1);
        }
        return identifier;
    }

    private static String validateDerivedTableExpression(String tableExpression, String scope) {
        if (!tableExpression.endsWith(")")) {
            throw new GXDBConditionException(CharSequenceUtil.format("{} derived table expression must be wrapped by parentheses", scope));
        }
        if (tableExpression.contains(";")
                || tableExpression.contains("--")
                || tableExpression.contains("/*")
                || tableExpression.contains("*/")) {
            throw new GXSqlInjectionException(CharSequenceUtil.format("{} derived table expression has SQL injection risk", scope));
        }
        String lower = tableExpression.toLowerCase(Locale.ROOT);
        if (!lower.contains("select") && !lower.contains("with")) {
            throw new GXDBConditionException(CharSequenceUtil.format("{} derived table expression must contain SELECT/WITH query", scope));
        }
        if (GXBaseBuilder.DANGEROUS_SQL_TOKEN_PATTERN.matcher(tableExpression).find()) {
            throw new GXSqlInjectionException(CharSequenceUtil.format("{} derived table expression contains dangerous SQL keywords", scope));
        }
        return tableExpression;
    }
}
