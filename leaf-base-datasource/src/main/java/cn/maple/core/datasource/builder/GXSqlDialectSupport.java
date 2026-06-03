package cn.maple.core.datasource.builder;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXDBStringUtils;
import cn.maple.core.framework.util.GXSqlDbTypeResolver;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GXSqlDialectSupport {
    private static final Pattern SQLSERVER_SELECT_DISTINCT_PREFIX = Pattern.compile("(?is)^\\s*SELECT\\s+DISTINCT\\s+");
    private static final Pattern SQLSERVER_SELECT_PREFIX = Pattern.compile("(?is)^\\s*SELECT\\s+");

    private GXSqlDialectSupport() {
    }

    static String buildExistsQuery(String innerSql) {
        String dbType = resolveDbTypeFromContext().toLowerCase(Locale.ROOT);
        if (GXSqlConstants.MYSQL_LIKE_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("SELECT EXISTS ({})", innerSql);
        }
        if (GXSqlConstants.POSTGRES_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("SELECT EXISTS ({})", innerSql);
        }
        if (GXSqlConstants.SQLSERVER_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("SELECT CASE WHEN EXISTS ({}) THEN 1 ELSE 0 END AS exists_result", innerSql);
        }
        if (GXSqlConstants.ORACLE_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("SELECT CASE WHEN EXISTS ({}) THEN 1 ELSE 0 END AS exists_result FROM DUAL", innerSql);
        }
        if (GXSqlConstants.DB2_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("SELECT CASE WHEN EXISTS ({}) THEN 1 ELSE 0 END AS exists_result FROM SYSIBM.SYSDUMMY1", innerSql);
        }
        GXBaseBuilder.LOGGER.warn("Unrecognized dbType [{}] for exists query, falling back to standard CASE WHEN EXISTS syntax.", dbType);
        return CharSequenceUtil.format("SELECT CASE WHEN EXISTS ({}) THEN 1 ELSE 0 END AS exists_result", innerSql);
    }

    static String applySingleRowLimit(String sql) {
        String dbType = resolveDbTypeFromContext().toLowerCase(Locale.ROOT);
        if (GXSqlConstants.MYSQL_LIKE_DIALECTS.contains(dbType) || GXSqlConstants.POSTGRES_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("SELECT * FROM ({}) gx_tmp_one LIMIT 1", sql);
        }
        if (GXSqlConstants.SQLSERVER_DIALECTS.contains(dbType)) {
            return applySqlServerSingleRowLimit(sql);
        }
        if (GXSqlConstants.ORACLE_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("SELECT * FROM ({}) gx_tmp_one WHERE ROWNUM <= 1", sql);
        }
        if (GXSqlConstants.DB2_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("{} FETCH FIRST 1 ROW ONLY", sql);
        }
        GXBaseBuilder.LOGGER.warn("Unrecognized dbType [{}] for findOneByCondition, fallback to LIMIT 1 syntax.", dbType);
        return CharSequenceUtil.format("SELECT * FROM ({}) gx_tmp_one LIMIT 1", sql);
    }

    static String renderJsonEqByDialect(String dbType, String field, String pathParam, String valueParam) {
        if (isMysqlOrMariaDb(dbType)) {
            return CharSequenceUtil.format("JSON_UNQUOTE(JSON_EXTRACT({}, {})) = CAST({} AS CHAR)", field, pathParam, valueParam);
        }
        if (GXSqlConstants.MYSQL_LIKE_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("JSON_EXTRACT({}, {}) = {}", field, pathParam, valueParam);
        }
        if (GXSqlConstants.POSTGRES_DIALECTS.contains(dbType)) {
            String normalizedPathExpr = CharSequenceUtil.format("string_to_array(replace({}, '$.', ''), '.')", pathParam);
            return CharSequenceUtil.format("CAST({} AS jsonb) #>> {} = CAST({} AS text)", field, normalizedPathExpr, valueParam);
        }
        if (GXSqlConstants.SQLSERVER_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("JSON_VALUE({}, {}) = CAST({} AS NVARCHAR(MAX))", field, pathParam, valueParam);
        }
        if (GXSqlConstants.ORACLE_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("JSON_VALUE({}, {}) = CAST({} AS VARCHAR2(4000))", field, pathParam, valueParam);
        }
        GXBaseBuilder.LOGGER.warn("Unrecognized dbType [{}] for JSON_EQ condition, falling back to standard JSON_VALUE syntax.", dbType);
        return CharSequenceUtil.format("JSON_VALUE({}, {}) = CAST({} AS VARCHAR(4000))", field, pathParam, valueParam);
    }

    static String validateRawSqlStrict(String rawSQL) {
        return GXDBStringUtils.normalizeAndValidateRawSqlQuery(rawSQL);
    }

    static String resolveDbTypeFromContext() {
        return GXSqlDbTypeResolver.resolveDbType();
    }

    private static String applySqlServerSingleRowLimit(String sql) {
        Matcher distinctMatcher = SQLSERVER_SELECT_DISTINCT_PREFIX.matcher(sql);
        if (distinctMatcher.find()) {
            return "SELECT DISTINCT TOP 1 " + sql.substring(distinctMatcher.end());
        }
        Matcher selectMatcher = SQLSERVER_SELECT_PREFIX.matcher(sql);
        if (selectMatcher.find()) {
            return "SELECT TOP 1 " + sql.substring(selectMatcher.end());
        }
        GXBaseBuilder.LOGGER.warn("SQL Server single-row limit expects SELECT SQL, falling back to derived-table TOP syntax.");
        return CharSequenceUtil.format("SELECT TOP 1 * FROM ({}) gx_tmp_one", sql);
    }

    private static boolean isMysqlOrMariaDb(String dbType) {
        return "mysql".equals(dbType) || "mariadb".equals(dbType);
    }
}
