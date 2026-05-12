package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;

final class GXUpdateJsonDialectSupport {
    private static final String DB_TYPE_PROPERTY_KEY = "spring.datasource.druid.db-type";
    private static final Set<String> MYSQL_LIKE_DIALECTS = Set.of("mysql", "mariadb", "h2", "sqlite");
    private static final Set<String> POSTGRES_DIALECTS = Set.of("postgres", "postgresql", "postgre_sql");
    private static final Set<String> SQLSERVER_DIALECTS = Set.of("sqlserver", "sql_server", "mssql", "sql-server");
    private static final Set<String> ORACLE_DIALECTS = Set.of("oracle");

    private GXUpdateJsonDialectSupport() {
    }

    static String renderJsonSet(String tableNameAlias, String fieldName, String pathParamName, String valueParamName, boolean jsonValue) {
        String field = qualifiedField(tableNameAlias, fieldName);
        String pathParam = param(pathParamName);
        String valueParam = valueParamName == null ? null : param(valueParamName);
        String dbType = resolveDbType();
        String valueExpr = renderJsonSetValue(dbType, valueParam, jsonValue);

        if (MYSQL_LIKE_DIALECTS.contains(dbType)) {
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
        return field + " = JSON_SET(" + field + ", " + pathParam + ", " + valueExpr + ")";
    }

    static String renderJsonRemove(String tableNameAlias, String fieldName, String pathParamName) {
        String field = qualifiedField(tableNameAlias, fieldName);
        String pathParam = param(pathParamName);
        String dbType = resolveDbType();

        if (MYSQL_LIKE_DIALECTS.contains(dbType)) {
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
        return field + " = JSON_REMOVE(" + field + ", " + pathParam + ")";
    }

    static String renderMapSet(String tableNameAlias, String fieldName, String paramName) {
        String field = qualifiedField(tableNameAlias, fieldName);
        String valueParam = mapParam(paramName);
        String dbType = resolveDbType();

        if (MYSQL_LIKE_DIALECTS.contains(dbType)) {
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
        return field + " = CAST(" + valueParam + " AS JSON)";
    }

    static String qualifiedField(String tableNameAlias, String fieldName) {
        GXDBStringEscapeUtils.validateSqlIdentifier(fieldName, "JSON update field name");
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return fieldName;
        }
        GXDBStringEscapeUtils.validateSqlAlias(tableNameAlias, "JSON update table alias");
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
