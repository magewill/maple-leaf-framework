package cn.maple.core.datasource.builder;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXExclusionDeletedFieldCondition;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

final class GXSqlLogicDeleteSupport {
    private GXSqlLogicDeleteSupport() {
    }

    static String buildLogicNotDeletedCondition(GXSqlBuildContext context, String tableName, String tableAliasName, List<GXCondition<?>> conditions) {
        if (CollUtil.contains(conditions, c -> GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass()))) {
            return null;
        }
        String trimmedName = CharSequenceUtil.trimToEmpty(tableName);
        if (trimmedName.isEmpty() || trimmedName.startsWith("(")) {
            return null;
        }
        String alias = CharSequenceUtil.isBlank(tableAliasName) ? tableName : tableAliasName;
        String logicColumn = null;
        String logicNotDeletedValue = null;
        TableInfo tableInfo = GXSqlTableMetadataSupport.getTableInfoSafely(context, tableName);
        if (tableInfo != null) {
            Optional<TableFieldInfo> logicFieldInfo = getLogicDeleteFieldInfo(tableInfo);
            if (logicFieldInfo.isPresent()) {
                logicColumn = logicFieldInfo.get().getColumn();
                logicNotDeletedValue = getLogicNotDeleteValue(tableInfo);
            }
        }
        if (CharSequenceUtil.isBlank(logicColumn) && GXSqlTableMetadataSupport.hasColumn(tableInfo, "is_deleted")) {
            logicColumn = "is_deleted";
            logicNotDeletedValue = "0";
            GXBaseBuilder.LOGGER.warn("Table [{}] has no @TableLogic configuration, fallback to logical-delete condition [{}.{} = {}].",
                    tableName, alias, logicColumn, logicNotDeletedValue);
        }
        if (CharSequenceUtil.isBlank(logicColumn)) {
            return null;
        }
        String literal = toSqlLiteral(logicNotDeletedValue);
        if (literal == null) {
            return CharSequenceUtil.format("{}.{} IS NULL", alias, logicColumn);
        }
        return CharSequenceUtil.format("{}.{} = {}", alias, logicColumn, literal);
    }

    static String buildLogicDeletedSetSql(TableInfo tableInfo) {
        if (Objects.isNull(tableInfo)) {
            return null;
        }
        String logicColumn = null;
        String logicDeletedValue = null;
        Optional<TableFieldInfo> logicFieldInfo = getLogicDeleteFieldInfo(tableInfo);
        if (logicFieldInfo.isPresent()) {
            logicColumn = logicFieldInfo.get().getColumn();
            logicDeletedValue = getLogicDeleteValue(tableInfo);
        }
        if (CharSequenceUtil.isBlank(logicColumn)) {
            boolean hasIsDeleted = CollUtil.emptyIfNull(tableInfo.getFieldList()).stream()
                    .anyMatch(f -> CharSequenceUtil.equalsIgnoreCase("is_deleted", f.getColumn()));
            if (!hasIsDeleted) {
                return null;
            }
            String keyColumn = tableInfo.getKeyColumn();
            if (CharSequenceUtil.isBlank(keyColumn)) {
                GXBaseBuilder.LOGGER.warn("Table [{}] fallback logical-delete field [is_deleted] requires primary key column, but got null.", tableInfo.getTableName());
                logicColumn = "is_deleted";
                logicDeletedValue = "1";
            } else if (!GXBaseBuilder.SAFE_IDENTIFIER_PATTERN.matcher(keyColumn).matches()) {
                GXBaseBuilder.LOGGER.warn("Table [{}] primary key column [{}] is not a safe identifier, fallback to '1'.", tableInfo.getTableName(), keyColumn);
                logicColumn = "is_deleted";
                logicDeletedValue = "1";
            } else {
                logicColumn = "is_deleted";
                logicDeletedValue = keyColumn;
            }
        }
        String logicDeletedSetSql = CharSequenceUtil.format("{} = {}", logicColumn, logicDeletedValue);
        boolean hasDeletedAt = CollUtil.emptyIfNull(tableInfo.getFieldList()).stream()
                .anyMatch(f -> CharSequenceUtil.equalsIgnoreCase("deleted_at", f.getColumn()));
        if (hasDeletedAt) {
            long currentTimestamp = System.currentTimeMillis() / 1000L;
            logicDeletedSetSql = CharSequenceUtil.format("{}, deleted_at = {}", logicDeletedSetSql, currentTimestamp);
        }
        return logicDeletedSetSql;
    }

    private static Optional<TableFieldInfo> getLogicDeleteFieldInfo(TableInfo tableInfo) {
        if (Objects.isNull(tableInfo)) {
            return Optional.empty();
        }
        return Optional.ofNullable(tableInfo.getLogicDeleteFieldInfo());
    }

    private static String getLogicDeleteValue(TableInfo tableInfo) {
        if (Objects.isNull(tableInfo) || Objects.isNull(tableInfo.getLogicDeleteFieldInfo())) {
            return null;
        }
        return tableInfo.getLogicDeleteFieldInfo().getLogicDeleteValue();
    }

    private static String getLogicNotDeleteValue(TableInfo tableInfo) {
        if (Objects.isNull(tableInfo) || Objects.isNull(tableInfo.getLogicDeleteFieldInfo())) {
            return null;
        }
        return tableInfo.getLogicDeleteFieldInfo().getLogicNotDeleteValue();
    }

    private static String toSqlLiteral(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
            return value;
        }
        if (GXBaseBuilder.NUMERIC_PATTERN.matcher(value).matches()) {
            return value;
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return value.toLowerCase(Locale.ROOT);
        }
        if (GXBaseBuilder.SQL_FUNCTION_PATTERN.matcher(value).matches()) {
            return value;
        }
        return "'" + value.replace("'", "''") + "'";
    }
}
