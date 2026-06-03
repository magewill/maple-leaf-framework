package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXDBStringUtils;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSqlDbTypeResolver;

import java.util.Set;

final class GXUpdateJsonDialectSupport {
    private static final Set<String> MYSQL_JSON_DIALECTS = Set.of("mysql", "mariadb");
    private static final Set<String> POSTGRES_DIALECTS = Set.of("postgres", "postgresql", "postgre_sql");
    private static final Set<String> SQLSERVER_DIALECTS = Set.of("sqlserver", "sql_server", "mssql", "sql-server");
    private static final Set<String> ORACLE_DIALECTS = Set.of("oracle");
    private static final Set<String> UNSUPPORTED_JSON_DIALECTS = Set.of("h2", "sqlite");

    private GXUpdateJsonDialectSupport() {
    }

    static String renderJsonSet(String tableNameAlias, String fieldName, String pathParamName, String valueParamName, boolean jsonValue) {
        String field = qualifiedField(tableNameAlias, fieldName);
        String pathParam = param(pathParamName);
        String valueParam = valueParamName == null ? null : param(valueParamName);
        String dbType = resolveDbType();
        String valueExpr = renderJsonSetValue(dbType, valueParam, jsonValue);

        if (MYSQL_JSON_DIALECTS.contains(dbType)) {
            return field + " = JSON_SET(" + field + ", " + pathParam + ", " + valueExpr + ")";
        }
        if (POSTGRES_DIALECTS.contains(dbType)) {
            String target = "COALESCE(CAST(" + field + " AS jsonb), '{}'::jsonb)";
            return field + " = jsonb_set(" + target + ", " + postgresPath(pathParam) + ", " + valueExpr + ", true)";
        }
        if (SQLSERVER_DIALECTS.contains(dbType)) {
            return field + " = JSON_MODIFY(COALESCE(" + field + ", '{}'), " + pathParam + ", " + valueExpr + ")";
        }
        if (ORACLE_DIALECTS.contains(dbType)) {
            String formatJson = jsonValue && valueParam != null ? " FORMAT JSON" : "";
            return field + " = JSON_TRANSFORM(COALESCE(" + field + ", '{}'), SET " + pathParam + " = " + valueExpr + formatJson + ")";
        }
        rejectUnsupportedJsonDialect(dbType, "JSON_SET");
        return field + " = JSON_SET(" + field + ", " + pathParam + ", " + valueExpr + ")";
    }

    static String renderJsonRemove(String tableNameAlias, String fieldName, String pathParamName) {
        String field = qualifiedField(tableNameAlias, fieldName);
        String pathParam = param(pathParamName);
        String dbType = resolveDbType();

        if (MYSQL_JSON_DIALECTS.contains(dbType)) {
            return field + " = JSON_REMOVE(" + field + ", " + pathParam + ")";
        }
        if (POSTGRES_DIALECTS.contains(dbType)) {
            return field + " = CASE WHEN " + pathParam + " = '$' THEN NULL ELSE CAST(" + field + " AS jsonb) #- " + postgresPath(pathParam) + " END";
        }
        if (SQLSERVER_DIALECTS.contains(dbType)) {
            return field + " = JSON_MODIFY(" + field + ", " + pathParam + ", NULL)";
        }
        if (ORACLE_DIALECTS.contains(dbType)) {
            return field + " = JSON_TRANSFORM(" + field + ", REMOVE " + pathParam + ")";
        }
        rejectUnsupportedJsonDialect(dbType, "JSON_REMOVE");
        return field + " = JSON_REMOVE(" + field + ", " + pathParam + ")";
    }

    static String renderMapSet(String tableNameAlias, String fieldName, String paramName) {
        String field = qualifiedField(tableNameAlias, fieldName);
        String valueParam = mapParam(paramName);
        String dbType = resolveDbType();

        if (MYSQL_JSON_DIALECTS.contains(dbType)) {
            return field + " = CAST(" + valueParam + " AS JSON)";
        }
        if (POSTGRES_DIALECTS.contains(dbType)) {
            return field + " = CAST(" + valueParam + " AS jsonb)";
        }
        if (SQLSERVER_DIALECTS.contains(dbType)) {
            return field + " = JSON_QUERY(" + valueParam + ")";
        }
        if (ORACLE_DIALECTS.contains(dbType)) {
            return field + " = JSON_QUERY(" + valueParam + ", '$')";
        }
        rejectUnsupportedJsonDialect(dbType, "JSON object assignment");
        return field + " = CAST(" + valueParam + " AS JSON)";
    }

    static String qualifiedField(String tableNameAlias, String fieldName) {
        GXDBStringUtils.validateSqlIdentifier(fieldName, "JSON update field name");
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return fieldName;
        }
        GXDBStringUtils.validateSqlAlias(tableNameAlias, "JSON update table alias");
        return tableNameAlias + "." + fieldName;
    }

    private static String renderJsonSetValue(String dbType, String valueParam, boolean jsonValue) {
        if (valueParam == null) {
            return POSTGRES_DIALECTS.contains(dbType) ? "'null'::jsonb" : "NULL";
        }
        if (POSTGRES_DIALECTS.contains(dbType)) {
            return jsonValue ? "CAST(" + valueParam + " AS jsonb)" : "to_jsonb(CAST(" + valueParam + " AS text))";
        }
        if (SQLSERVER_DIALECTS.contains(dbType)) {
            return jsonValue ? "JSON_QUERY(" + valueParam + ")" : valueParam;
        }
        return jsonValue ? "CAST(" + valueParam + " AS JSON)" : valueParam;
    }

    private static String postgresPath(String pathParam) {
        return "COALESCE(string_to_array(NULLIF(replace(" + pathParam + ", '$.', ''), '$'), '.'), ARRAY[]::text[])";
    }

    private static String param(String paramName) {
        return "#{dbQueryParamInnerDto.paramMap." + paramName + "}";
    }

    private static String mapParam(String paramName) {
        return "#{dbQueryParamInnerDto.paramMap." + paramName
                + ", javaType=java.util.Map,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler}";
    }

    private static String resolveDbType() {
        return GXSqlDbTypeResolver.resolveDbType();
    }

    private static void rejectUnsupportedJsonDialect(String dbType, String operationName) {
        if (UNSUPPORTED_JSON_DIALECTS.contains(dbType)) {
            throw new GXBusinessException(CharSequenceUtil.format("{} is not supported for dbType: {}", operationName, dbType));
        }
    }
}
