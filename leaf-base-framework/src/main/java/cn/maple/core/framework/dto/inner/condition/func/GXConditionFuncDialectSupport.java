package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class GXConditionFuncDialectSupport {
    private static final String DB_TYPE_PROPERTY_KEY = "spring.datasource.druid.db-type";
    private static final Set<String> MYSQL_LIKE_DIALECTS = Set.of("mysql", "mariadb", "h2", "sqlite");
    private static final Set<String> POSTGRES_DIALECTS = Set.of("postgres", "postgresql", "postgre_sql");
    private static final Set<String> SQLSERVER_DIALECTS = Set.of("sqlserver", "sql_server", "mssql", "sql-server");
    private static final Set<String> ORACLE_DIALECTS = Set.of("oracle");

    private GXConditionFuncDialectSupport() {
    }

    static String qualifiedColumn(String tableNameAlias, String columnName) {
        String column = CharSequenceUtil.trim(columnName);
        if (CharSequenceUtil.isBlank(column)) {
            throw new GXBusinessException("JSON function field must not be blank");
        }
        GXDBStringEscapeUtils.validateColumnName(column);
        if (column.contains(".") || CharSequenceUtil.isBlank(tableNameAlias)) {
            return column;
        }
        GXDBStringEscapeUtils.validateColumnName(tableNameAlias);
        return tableNameAlias + "." + column;
    }

    static String safeColumn(String columnName) {
        String column = CharSequenceUtil.trim(columnName);
        if (CharSequenceUtil.isBlank(column)) {
            throw new GXBusinessException("JSON function field must not be blank");
        }
        GXDBStringEscapeUtils.validateColumnName(column);
        return column;
    }

    static String normalizeJsonPath(String jsonPath) {
        if (CharSequenceUtil.isBlank(jsonPath)) {
            return "$";
        }
        String trimmed = CharSequenceUtil.trim(jsonPath);
        if (GXDBStringEscapeUtils.check(trimmed)) {
            throw new GXSqlInjectionException("JSON path contains SQL injection risk");
        }
        if ("$".equals(trimmed) || trimmed.startsWith("$.") || trimmed.startsWith("$[")) {
            return trimmed;
        }
        if (trimmed.startsWith(".")) {
            return "$" + trimmed;
        }
        if (trimmed.startsWith("$")) {
            return "$." + trimmed.substring(1);
        }
        return "$." + trimmed;
    }

    static String toJsonString(Object values, String functionName) {
        if (values == null) {
            throw new GXBusinessException(functionName + " value must not be null");
        }
        return JSONUtil.toJsonStr(values);
    }

    static String renderJsonSearch(String field, String oneOrAllParamName, String valueParamName) {
        String dbType = resolveDbType();
        String valueParam = param(valueParamName);
        if (MYSQL_LIKE_DIALECTS.contains(dbType)) {
            return "JSON_SEARCH(" + field + ", " + param(oneOrAllParamName) + ", " + valueParam + ") IS NOT NULL";
        }
        if (POSTGRES_DIALECTS.contains(dbType)) {
            return "CAST(" + field + " AS text) LIKE ('%' || CAST(" + valueParam + " AS text) || '%')";
        }
        if (SQLSERVER_DIALECTS.contains(dbType)) {
            return "CAST(" + field + " AS NVARCHAR(MAX)) LIKE '%' + CAST(" + valueParam + " AS NVARCHAR(MAX)) + '%'";
        }
        if (ORACLE_DIALECTS.contains(dbType)) {
            return "JSON_SERIALIZE(" + field + " RETURNING VARCHAR2(4000)) LIKE '%' || CAST(" + valueParam + " AS VARCHAR2(4000)) || '%'";
        }
        return "JSON_SEARCH(" + field + ", " + param(oneOrAllParamName) + ", " + valueParam + ") IS NOT NULL";
    }

    static String renderJsonContains(String field, String pathParamName, String valueParamName) {
        String dbType = resolveDbType();
        String pathParam = param(pathParamName);
        String valueParam = param(valueParamName);
        if (MYSQL_LIKE_DIALECTS.contains(dbType)) {
            return "JSON_CONTAINS(" + field + ", CAST(" + valueParam + " AS JSON), " + pathParam + ")";
        }
        if (POSTGRES_DIALECTS.contains(dbType)) {
            return postgresJsonTarget(field, pathParam) + " @> CAST(" + valueParam + " AS jsonb)";
        }
        if (SQLSERVER_DIALECTS.contains(dbType)) {
            return "JSON_QUERY(" + field + ", " + pathParam + ") = JSON_QUERY(" + valueParam + ")";
        }
        if (ORACLE_DIALECTS.contains(dbType)) {
            return "JSON_EQUAL(JSON_QUERY(" + field + ", " + pathParam + "), JSON_QUERY(" + valueParam + ", '$'))";
        }
        return "JSON_CONTAINS(" + field + ", CAST(" + valueParam + " AS JSON), " + pathParam + ")";
    }

    static String renderJsonOverlaps(String field, String pathParamName, String valueParamName) {
        String dbType = resolveDbType();
        String pathParam = param(pathParamName);
        String valueParam = param(valueParamName);
        if (MYSQL_LIKE_DIALECTS.contains(dbType)) {
            return "JSON_OVERLAPS(JSON_EXTRACT(" + field + ", " + pathParam + "), CAST(" + valueParam + " AS JSON))";
        }
        if (POSTGRES_DIALECTS.contains(dbType)) {
            return "EXISTS (SELECT 1 FROM jsonb_array_elements(" + postgresJsonArrayTarget(field, pathParam)
                    + ") gx_src(value) JOIN jsonb_array_elements(" + postgresJsonArrayValue(valueParam)
                    + ") gx_candidate(value) ON gx_src.value = gx_candidate.value)";
        }
        if (SQLSERVER_DIALECTS.contains(dbType)) {
            return "EXISTS (SELECT 1 FROM OPENJSON(JSON_QUERY(" + field + ", " + pathParam
                    + ")) gx_src JOIN OPENJSON(" + valueParam + ") gx_candidate ON gx_src.[value] = gx_candidate.[value])";
        }
        if (ORACLE_DIALECTS.contains(dbType)) {
            return "EXISTS (SELECT 1 FROM JSON_TABLE(JSON_QUERY(" + field + ", " + pathParam
                    + "), '$[*]' COLUMNS (value VARCHAR2(4000) PATH '$')) gx_src JOIN JSON_TABLE(" + valueParam
                    + ", '$[*]' COLUMNS (value VARCHAR2(4000) PATH '$')) gx_candidate ON gx_src.value = gx_candidate.value)";
        }
        return "JSON_OVERLAPS(JSON_EXTRACT(" + field + ", " + pathParam + "), CAST(" + valueParam + " AS JSON))";
    }

    static String param(String paramName) {
        return "#{dbQueryParamInnerDto.paramMap." + paramName + "}";
    }

    private static String postgresJsonTarget(String field, String pathParam) {
        return "(CASE WHEN " + pathParam + " = '$' THEN CAST(" + field + " AS jsonb) ELSE CAST(" + field + " AS jsonb) #> "
                + postgresPathArray(pathParam) + " END)";
    }

    private static String postgresJsonArrayTarget(String field, String pathParam) {
        String target = postgresJsonTarget(field, pathParam);
        return "CASE WHEN jsonb_typeof(" + target + ") = 'array' THEN " + target + " ELSE jsonb_build_array(" + target + ") END";
    }

    private static String postgresJsonArrayValue(String valueParam) {
        String value = "CAST(" + valueParam + " AS jsonb)";
        return "CASE WHEN jsonb_typeof(" + value + ") = 'array' THEN " + value + " ELSE jsonb_build_array(" + value + ") END";
    }

    private static String postgresPathArray(String pathParam) {
        return "COALESCE(string_to_array(NULLIF(replace(" + pathParam + ", '$.', ''), '$'), '.'), ARRAY[]::text[])";
    }

    private static String resolveDbType() {
        String systemDbType = System.getProperty(DB_TYPE_PROPERTY_KEY);
        if (CharSequenceUtil.isNotBlank(systemDbType)) {
            return normalizeDbType(systemDbType);
        }
        String beanDbType = resolveDbTypeFromBean();
        if (CharSequenceUtil.isNotBlank(beanDbType)) {
            return normalizeDbType(beanDbType);
        }
        return normalizeDbType(GXCommonUtils.getEnvironmentValue(DB_TYPE_PROPERTY_KEY, String.class, "mysql"));
    }

    private static String resolveDbTypeFromBean() {
        Object properties = GXSpringContextUtils.getBean("dataSourceProperties");
        if (properties == null) {
            return null;
        }
        try {
            Method method = properties.getClass().getMethod("getDbType");
            Object dbType = method.invoke(properties);
            return dbType == null ? null : dbType.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeDbType(String dbType) {
        return CharSequenceUtil.isBlank(dbType) ? "mysql" : dbType.toLowerCase(Locale.ROOT);
    }
}
