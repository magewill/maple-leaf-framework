package cn.maple.core.datasource.builder;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.properties.GXDataSourceProperties;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;

import java.util.Locale;
import java.util.Objects;

final class GXSqlDialectSupport {
    private GXSqlDialectSupport() {
    }

    static String applySingleRowLimit(String sql) {
        String dbType = resolveDbTypeFromContext().toLowerCase(Locale.ROOT);
        if (GXBaseBuilder.MYSQL_LIKE_DIALECTS.contains(dbType) || GXBaseBuilder.POSTGRES_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("SELECT * FROM ({}) gx_tmp_one LIMIT 1", sql);
        }
        if (GXBaseBuilder.SQLSERVER_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("SELECT TOP 1 * FROM ({}) gx_tmp_one", sql);
        }
        if (GXBaseBuilder.ORACLE_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("SELECT * FROM ({}) gx_tmp_one WHERE ROWNUM <= 1", sql);
        }
        GXBaseBuilder.LOGGER.warn("Unrecognized dbType [{}] for findOneByCondition, fallback to LIMIT 1 syntax.", dbType);
        return CharSequenceUtil.format("SELECT * FROM ({}) gx_tmp_one LIMIT 1", sql);
    }

    static String renderJsonEqByDialect(String dbType, String field, String pathParam, String valueParam) {
        if (GXBaseBuilder.MYSQL_LIKE_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("JSON_EXTRACT({}, {}) = {}", field, pathParam, valueParam);
        }
        if (GXBaseBuilder.POSTGRES_DIALECTS.contains(dbType)) {
            String normalizedPathExpr = CharSequenceUtil.format("string_to_array(replace({}, '$.', ''), '.')", pathParam);
            return CharSequenceUtil.format("CAST({} AS jsonb) #>> {} = CAST({} AS text)", field, normalizedPathExpr, valueParam);
        }
        if (GXBaseBuilder.SQLSERVER_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("JSON_VALUE({}, {}) = CAST({} AS NVARCHAR(MAX))", field, pathParam, valueParam);
        }
        if (GXBaseBuilder.ORACLE_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("JSON_VALUE({}, {}) = CAST({} AS VARCHAR2(4000))", field, pathParam, valueParam);
        }
        GXBaseBuilder.LOGGER.warn("Unrecognized dbType [{}] for JSON_EQ condition, falling back to standard JSON_VALUE syntax.", dbType);
        return CharSequenceUtil.format("JSON_VALUE({}, {}) = CAST({} AS VARCHAR(4000))", field, pathParam, valueParam);
    }

    static String validateRawSqlStrict(String rawSQL) {
        return GXDBStringEscapeUtils.normalizeAndValidateRawSqlQuery(rawSQL);
    }

    static String resolveDbTypeFromContext() {
        try {
            GXDataSourceProperties properties = GXSpringContextUtils.getBean(GXDataSourceProperties.class);
            if (Objects.nonNull(properties) && CharSequenceUtil.isNotBlank(properties.getDbType())) {
                return properties.getDbType();
            }
        } catch (Exception ignored) {
        }
        GXBaseBuilder.LOGGER.error("Unable to resolve database type, please configure 'dbType' in GXDataSourceProperties");
        return "mysql";
    }
}
