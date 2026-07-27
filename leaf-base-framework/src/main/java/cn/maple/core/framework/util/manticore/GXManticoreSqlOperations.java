package cn.maple.core.framework.util.manticore;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.inner.GXMantiCoreResDto;
import cn.maple.core.framework.exception.GXManticoreException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static cn.maple.core.framework.util.manticore.GXManticoreConfiguration.requireIdentifier;
import static cn.maple.core.framework.util.manticore.GXManticoreConfiguration.requireNonBlank;
import static cn.maple.core.framework.util.manticore.GXManticoreConfiguration.sendPost;
import static cn.maple.core.framework.util.manticore.GXManticoreConfiguration.toSqlResponse;

/**
 * SQL execution, SQL response parsing, and table-management operations.
 */
public final class GXManticoreSqlOperations {
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "^create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?[A-Za-z_][A-Za-z0-9_]*\\s*\\(.*$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private GXManticoreSqlOperations() {
    }

    /**
     * Executes raw Manticore SQL and returns the structured HTTP response.
     */
    public static GXMantiCoreResDto<JSON> executeSql(String sql) {
        requireNonBlank(sql, "sql");
        String encodedSql = java.net.URLEncoder.encode(sql, StandardCharsets.UTF_8).replace("*", "%2A");
        return toSqlResponse(sendPost("/sql?mode=raw", "query=" + encodedSql,
                "application/x-www-form-urlencoded"));
    }

    /**
     * Executes one validated, read-only SQL statement.
     */
    public static GXMantiCoreResDto<JSON> executeReadSql(String sql) {
        return executeSql(GXDBStringUtils.normalizeAndValidateRawSqlQuery(sql));
    }

    /**
     * Parses the first result set in a SQL response without making a remote request.
     */
    public static List<Map<String, Object>> parseSqlAsList(String responseJson) {
        List<Map<String, Object>> result = new ArrayList<>();
        JSONArray resultSets = JSONUtil.parseArray(responseJson);
        if (resultSets.isEmpty()) {
            return result;
        }
        JSONObject first = resultSets.getJSONObject(0);
        String error = first.getStr("error");
        if (StrUtil.isNotBlank(error)) {
            throw new GXManticoreException("SQL execution failed: " + error, responseJson);
        }
        addRows(first.getJSONArray("data"), result);
        return result;
    }

    /**
     * Parses all result sets in a SQL response without making a remote request.
     */
    public static List<List<Map<String, Object>>> parseSqlMultiAsList(String responseJson) {
        List<List<Map<String, Object>>> resultSets = new ArrayList<>();
        JSONArray responseSets = JSONUtil.parseArray(responseJson);
        for (int i = 0; i < responseSets.size(); i++) {
            JSONObject item = responseSets.getJSONObject(i);
            String error = item.getStr("error");
            if (StrUtil.isNotBlank(error)) {
                throw new GXManticoreException("SQL statement " + (i + 1) + " failed: " + error, responseJson);
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            addRows(item.getJSONArray("data"), rows);
            resultSets.add(rows);
        }
        return resultSets;
    }

    public static GXMantiCoreResDto<JSON> flushAttributes() {
        return executeSql("FLUSH ATTRIBUTES");
    }

    public static GXMantiCoreResDto<JSON> showTables() {
        return executeSql("SHOW TABLES");
    }

    public static GXMantiCoreResDto<JSON> describeTable(String index) {
        requireIdentifier(index, "index");
        return executeSql("DESCRIBE " + index);
    }

    public static GXMantiCoreResDto<JSON> showCreateTable(String index) {
        requireIdentifier(index, "index");
        return executeSql("SHOW CREATE TABLE " + index);
    }

    public static GXMantiCoreResDto<JSON> createTable(String createTableSql) {
        return executeSql(validateCreateTableSql(createTableSql));
    }

    public static GXMantiCoreResDto<JSON> dropTable(String index, boolean ifExists) {
        requireIdentifier(index, "index");
        String sql = ifExists ? "DROP TABLE IF EXISTS " + index : "DROP TABLE " + index;
        return executeSql(sql);
    }

    public static GXMantiCoreResDto<JSON> truncateTable(String index) {
        requireIdentifier(index, "index");
        return executeSql("TRUNCATE TABLE " + index);
    }

    public static GXMantiCoreResDto<JSON> optimizeTable(String index) {
        requireIdentifier(index, "index");
        return executeSql("OPTIMIZE INDEX " + index);
    }

    private static void addRows(JSONArray data, List<Map<String, Object>> rows) {
        if (data == null) {
            return;
        }
        for (int i = 0; i < data.size(); i++) {
            rows.add(data.getJSONObject(i));
        }
    }

    private static String validateCreateTableSql(String createTableSql) {
        if (StrUtil.isBlank(createTableSql)) {
            throw new GXSqlInjectionException("CREATE TABLE SQL must not be blank");
        }
        String normalized = createTableSql.trim();
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains(";") || lower.contains("--") || lower.contains("#")
                || lower.contains("/*") || lower.contains("*/")
                || !CREATE_TABLE_PATTERN.matcher(normalized).matches()) {
            throw new GXSqlInjectionException("Only one CREATE TABLE statement is allowed");
        }
        return normalized;
    }
}
