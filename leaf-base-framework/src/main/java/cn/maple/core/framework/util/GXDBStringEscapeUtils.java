package cn.maple.core.framework.util;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GXDBStringEscapeUtils {
    private static final Pattern SQL_SYNTAX_PATTERN = Pattern.compile(
            "(?i)" +
                    "(" +
                    "\\b(insert|delete|update|select|create|drop|truncate|grant|alter|deny|revoke|call|execute|exec|declare|show|rename|set)\\s+.*\\b(into|from|set|where|table|database|view|index|on|cursor|procedure|trigger|for|password|union)\\b" +
                    "|" +
                    "\\bselect\\s*\\*\\s*from\\s+" +
                    "|" +
                    "\\b(and|or)\\s+(?:" +
                    "\\d+\\s*=\\s*\\d+" +
                    "|\\d+\\s*=\\s*\\d+\\s*(?:--[\\s\\r\\n]*|#)" +
                    "|'[^']+'\\s*=\\s*'[^']+'" +
                    "|'[^']+'\\s*=\\s*'[^']+'\\s*(?:--[\\s\\r\\n]*|#)" +
                    ")\\b" +
                    ")",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SQL_COMMENT_PATTERN = Pattern.compile(
            "'[^']*'\\s*(?:--|#|/\\*|;)|\\s+(?:or|union)\\s+",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)('\\s*or\\s*'\\s*=\\s*')|" +
                    "(\\b(or|and)\\s+[\\w\\p{L}]+\\s*=\\s*[\\w\\p{L}]+)|" +
                    "(\\bexec\\s*\\()|" +
                    "(\\bunion\\s*(all|select))|" +
                    "(\\binsert\\s+into\\s+)|" +
                    "(\\bdrop\\s+table\\s+)|" +
                    "(\\balter\\s+table\\s+)|" +
                    "(\\bdelete\\s+from\\s+)|" +
                    "(\\bupdate\\s+.+\\s+set\\s+)|" +
                    "(;\\s*[\\w\\p{L}]+\\s*:)|" +
                    "(;\\s*declare\\s+)|" +
                    "(--[\\s\\r\\n])|" +
                    "(/\\*.*?\\*/)|" +
                    "(\\bwaitfor\\s+delay\\s+)|" +
                    "(\\bsleep\\s*\\(\\s*\\d+\\s*\\))|" +
                    "(\\bexecute\\s+immediate)|" +
                    "(\\bcall\\s+\\w+)|" +
                    "(\\bbatch\\s+processing)|" +
                    "(\\bbegin\\s+transaction)|" +
                    "(\\bcommit\\s*;)|" +
                    "(\\brollback\\s*;)");

    private static final Pattern SQL_BLIND_INJECTION_PATTERN = Pattern.compile(
            "(?i)(\\bsleep\\s*\\(\\s*\\d+\\s*\\))|" +
                    "(\\bwaitfor\\s+delay\\s+'\\d+:\\d+:\\d+')|" +
                    "(\\bbenchmark\\s*\\(\\s*\\d+\\s*,)|" +
                    "(\\bpg_sleep\\s*\\(\\s*\\d+\\s*\\))|" +
                    "(\\bdbms_pipe\\.receive_message\\s*\\()|" +
                    "(\\band\\s+\\d+=\\d+)|" +
                    "(\\band\\s+\\d+>\\d+)|" +
                    "(\\band\\s+\\d+<\\d+)|" +
                    "(\\bif\\s*\\(\\s*\\d+\\s*=\\s*\\d+\\s*\\))|" +
                    "(\\bselect\\s+case\\s+when\\s+)|" +
                    "(\\bextractvalue\\s*\\()|" +
                    "(\\bsys\\.\\w+\\s*\\()|" +
                    "(\\bsqlmap)|" +
                    "(\\btrue--)|" +
                    "(\\b1=1--)");

    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(?i)(<script[^>]*>)|" +
                    "(</script>)|" +
                    "(<[^>]*\\bon\\w+\\s*=)|" +
                    "(\\balert\\s*\\()|" +
                    "(\\bdocument\\.cookie)|" +
                    "(\\blocation\\.href)|" +
                    "(javascript:)");

    private static final Pattern JSON_INJECTION_PATTERN = Pattern.compile(
            "(?i)(\"\\s*:\\s*\\{)|" +
                    "(\\}\\s*,\\s*\")|" +
                    "(\\]\\s*,\\s*\\[)|" +
                    "(\\[\\s*\\]\\s*,\\s*\\[)|" +
                    "(\\}\\s*\\]\\s*,\\s*\\[\\s*\\{)|" +
                    "(\"\\s*:\\s*function\\s*\\()|" +
                    "(\"\\s*:\\s*new\\s+)|" +
                    "(\"?\\$where\"?\\s*:)|" +
                    "(\"?\\$regex\"?\\s*:)|" +
                    "(\"?\\$ne\"?\\s*:)|" +
                    "(\"?\\$gt\"?\\s*:)|" +
                    "(\"?\\$exists\"?\\s*:)|" +
                    "(\"?\\$elemMatch\"?\\s*:)|" +
                    "(\"?\\$text\"?\\s*:)|" +
                    "(\"?\\$expr\"?\\s*:)|" +
                    "(\"?\\$jsonSchema\"?\\s*:)|" +
                    "(\"?\\$mod\"?\\s*:)|" +
                    "(\"?\\$type\"?\\s*:)|" +
                    "(\"?\\$eval\"?\\s*:)|" +
                    "(\"?\\$function\"?\\s*:)");

    private static final Pattern SAFE_SQL_IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$");

    private static final Pattern SAFE_SQL_ALIAS_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private static final Pattern RAW_SQL_DANGEROUS_PATTERN = Pattern.compile("(?i)\\b(update|delete|insert|alter|drop|truncate|create|grant|revoke|call|exec|execute|merge|declare|rename)\\b");

    private static final Pattern RAW_SQL_DANGEROUS_FUNCTION_PATTERN = Pattern.compile(
            "(?i)(\\b(?:sleep|pg_sleep|benchmark|load_file)\\s*\\()|"
                    + "(\\bwaitfor\\s+delay\\b)|"
                    + "(\\bdbms_pipe\\.receive_message\\s*\\()|"
                    + "(\\binto\\s+(?:outfile|dumpfile)\\b)|"
                    + "(\\bxp_cmdshell\\b)|"
                    + "(\\bsp_executesql\\b)|"
                    + "(\\bexecute\\s+immediate\\b)"
    );

    private static final Pattern RAW_SQL_UNION_PATTERN = Pattern.compile("(?i)\\bunion\\b\\s*(?:all\\s*)?\\bselect\\b");

    private static final Pattern RAW_SQL_TAUTOLOGY_PATTERN = Pattern.compile(
            "(?i)\\b(?:and|or)\\s+(?:\\d+\\s*=\\s*\\d+|'[^']+'\\s*=\\s*'[^']+')\\b"
    );

    private GXDBStringEscapeUtils() {
        throw new AssertionError("GXDBStringEscapeUtils must not be instantiated");
    }

    private static boolean isEscapeNeededForString(String str, int len) {
        boolean needsHexEscape = false;
        for (int i = 0; i < len; ++i) {
            char c = str.charAt(i);
            switch (c) {
                case 0, '\n', '\r', '\\', '\'', '"', '\032' -> needsHexEscape = true;
                default -> {
                }
            }
            if (needsHexEscape) {
                break;
            }
        }
        return needsHexEscape;
    }

    private static String escapeBasicChars(String input, boolean useSqlStandardQuoteEscape) {
        if (input == null) {
            return null;
        }

        int stringLength = input.length();
        if (!isEscapeNeededForString(input, stringLength)) {
            return input;
        }

        StringBuilder buf = new StringBuilder((int) (input.length() * 1.1));

        for (int i = 0; i < stringLength; ++i) {
            char c = input.charAt(i);
            switch (c) {
                case 0 -> buf.append('\\').append('0');
                case '\n' -> buf.append('\\').append('n');
                case '\r' -> buf.append('\\').append('r');
                case '\\' -> buf.append('\\').append('\\');
                case '\'' -> {
                    if (useSqlStandardQuoteEscape) {
                        buf.append('\'').append('\'');
                    } else {
                        buf.append('\\').append('\'');
                    }
                }
                case '"' -> buf.append('\\').append('"');
                case '\032' -> buf.append('\\').append('Z');
                default -> buf.append(c);
            }
        }
        return buf.toString();
    }

    public static String escapeRawString(String escapeStr) {
        if (escapeStr == null) {
            return null;
        }
        return escapeBasicChars(escapeStr, false);
    }

    public static String escapeString(String escapeStr) {
        if (escapeStr == null) {
            return null;
        }
        if (escapeStr.length() >= 2 && escapeStr.charAt(0) == '\'' && escapeStr.charAt(escapeStr.length() - 1) == '\'') {
            escapeStr = escapeStr.substring(1, escapeStr.length() - 1);
        }
        return "'" + escapeBasicChars(escapeStr, true) + "'";
    }

    private static Matcher getMatcher(Pattern pattern, String input) {
        return pattern.matcher(input);
    }

    public static boolean check(String value) {
        Objects.requireNonNull(value);
        return getMatcher(SQL_COMMENT_PATTERN, value).find() ||
                getMatcher(SQL_SYNTAX_PATTERN, value).find() ||
                getMatcher(SQL_INJECTION_PATTERN, value).find() ||
                getMatcher(SQL_BLIND_INJECTION_PATTERN, value).find();
    }

    public static String normalizeAndValidateRawSqlCondition(String rawSql) {
        return normalizeAndValidateRawSqlFragment(rawSql, "Raw SQL condition");
    }

    public static String normalizeAndValidateRawSqlExpression(String rawSql) {
        return normalizeAndValidateRawSqlFragment(rawSql, "Raw SQL expression");
    }

    public static String normalizeAndValidateRawSqlQuery(String rawSql) {
        if (CharSequenceUtil.isBlank(rawSql)) {
            throw new GXSqlInjectionException("Raw SQL must not be blank");
        }

        String normalized = rawSql.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains(";") || lower.contains("--") || lower.contains("#") || lower.contains("/*") || lower.contains("*/")) {
            throw new GXSqlInjectionException("Raw SQL contains illegal control symbols");
        }
        if (!(lower.startsWith("select") || lower.startsWith("with"))) {
            throw new GXSqlInjectionException("Raw SQL only allows SELECT/WITH queries");
        }
        if (RAW_SQL_DANGEROUS_PATTERN.matcher(normalized).find()) {
            throw new GXSqlInjectionException("Raw SQL contains dangerous keywords");
        }
        if (RAW_SQL_DANGEROUS_FUNCTION_PATTERN.matcher(normalized).find()) {
            throw new GXSqlInjectionException("Raw SQL contains dangerous functions");
        }
        return normalized;
    }

    private static String normalizeAndValidateRawSqlFragment(String rawSql, String kindLabel) {
        if (CharSequenceUtil.isBlank(rawSql)) {
            throw new GXSqlInjectionException(kindLabel + " must not be blank");
        }

        String normalized = rawSql.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains(";") || lower.contains("--") || lower.contains("#") || lower.contains("/*") || lower.contains("*/")) {
            throw new GXSqlInjectionException(kindLabel + " contains illegal SQL control symbols");
        }
        if (RAW_SQL_DANGEROUS_PATTERN.matcher(normalized).find()) {
            throw new GXSqlInjectionException(kindLabel + " contains dangerous SQL keywords");
        }
        if (RAW_SQL_DANGEROUS_FUNCTION_PATTERN.matcher(normalized).find()) {
            throw new GXSqlInjectionException(kindLabel + " contains dangerous SQL functions");
        }
        if (RAW_SQL_UNION_PATTERN.matcher(normalized).find() || RAW_SQL_TAUTOLOGY_PATTERN.matcher(normalized).find()) {
            throw new GXSqlInjectionException("SQL injection risk detected in " + kindLabel.toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    public static boolean checkComprehensive(String value) {
        Objects.requireNonNull(value);
        return check(value) || getMatcher(XSS_PATTERN, value).find();
    }

    public static boolean checkJsonInjection(String jsonStr) {
        Objects.requireNonNull(jsonStr);
        return getMatcher(JSON_INJECTION_PATTERN, jsonStr).find() || check(jsonStr);
    }

    public static String removeEscapeCharacter(String text) {
        Objects.requireNonNull(text);
        return text.replaceAll("[\"']", "");
    }

    public static String removeAllSpecialChars(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("[^\\p{L}\\p{N}\\s]", "");
    }

    public static String escapeSql(String input) {
        if (input == null) {
            return null;
        }
        String result = escapeBasicChars(input, true);

        result = StrUtil.replace(result, ";", "\\;");
        result = StrUtil.replace(result, "=", "\\=");
        result = StrUtil.replace(result, "-", "\\-");
        result = StrUtil.replace(result, "#", "\\#");
        result = StrUtil.replace(result, "/*", "\\/\\*");
        result = StrUtil.replace(result, "*/", "\\*\\/");
        return result;
    }

    public static String escapeSqlForLike(String input, char escapeChar) {
        if (input == null) {
            return null;
        }
        String escaped = escapeSql(input);
        String escStr = String.valueOf(escapeChar);
        escaped = StrUtil.replace(escaped, escStr, escStr + escStr);
        escaped = StrUtil.replace(escaped, "%", escStr + "%");
        escaped = StrUtil.replace(escaped, "_", escStr + "_");
        return escaped;
    }

    public static String escapeSqlForLike(String input) {
        return escapeSqlForLike(input, '\\');
    }

    public static String escapeJson(String jsonInput) {
        if (jsonInput == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(jsonInput.length() + 20);
        for (int i = 0; i < jsonInput.length(); i++) {
            char c = jsonInput.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '/' -> sb.append("\\/");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        String hex = Integer.toHexString(c);
                        sb.append("\\u");
                        for (int j = 0; j < 4 - hex.length(); j++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public static String escapeJsonForSql(String jsonStr) {
        if (jsonStr == null) {
            return null;
        }

        if (check(jsonStr)) {
            throw new GXSqlInjectionException("JSON string contains SQL injection risk");
        }

        if (checkJsonInjection(jsonStr)) {
            throw new GXSqlInjectionException("JSON string contains JSON injection risk");
        }

        return escapeSql(jsonStr);
    }

    public static Object[] prepareParameterizedQuery(List<Object> values) {
        if (values == null || values.isEmpty()) {
            return new Object[]{"()", new ArrayList<>()};
        }

        int paramCount = values.size();
        StringBuilder placeholders = new StringBuilder(paramCount * 3);
        placeholders.append('(');
        for (int i = 0; i < paramCount; i++) {
            if (i > 0) {
                placeholders.append(", ");
            }
            placeholders.append('?');
        }
        placeholders.append(')');

        return new Object[]{placeholders.toString(), values};
    }

    public static String buildSafeInClause(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "('')";
        }

        StringBuilder sb = new StringBuilder(values.size() * 10);
        sb.append('(');
        boolean first = true;

        for (String value : values) {
            if (value != null && check(value)) {
                continue;
            }

            if (!first) {
                sb.append(", ");
            }
            sb.append('\'').append(value == null ? "" : escapeSql(value)).append('\'');
            first = false;
        }

        if (first) {
            return "('')";
        }

        sb.append(')');
        return sb.toString();
    }

    public static String validateAndCleanInput(String input) {
        if (input == null) {
            return null;
        }

        String trimmedInput = input.trim();

        if (check(trimmedInput)) {
            String message = CharSequenceUtil.format("SQL injection risk detected: {} (source: validateAndCleanInput)", trimmedInput);
            throw new GXSqlInjectionException(message);
        }

        return escapeSql(trimmedInput);
    }

    public static String buildSafeLikeCondition(String column, String value, String matchType) {
        if (CharSequenceUtil.isBlank(column) || value == null) {
            throw new IllegalArgumentException("Column name and query value must not be blank");
        }

        if (!isValidIdentifier(column)) {
            String message = CharSequenceUtil.format("Column name contains unsafe characters: {} (source: buildSafeLikeCondition)", column);
            throw new GXSqlInjectionException(message);
        }

        if (check(value)) {
            String message = CharSequenceUtil.format("SQL injection risk detected: {} (source: buildSafeLikeCondition)", value);
            throw new GXSqlInjectionException(message);
        }

        if (CharSequenceUtil.isBlank(matchType)) {
            throw new IllegalArgumentException("Match type must not be blank");
        }

        String escapedValue = escapeSqlForLike(value);
        String likePattern;

        switch (matchType.toLowerCase()) {
            case "start" -> likePattern = escapedValue + "%";
            case "end" -> likePattern = "%" + escapedValue;
            case "anywhere" -> likePattern = "%" + escapedValue + "%";
            default -> throw new IllegalArgumentException("Unsupported match type: " + matchType);
        }

        return column + " LIKE '" + likePattern + "' ESCAPE '\\'";
    }

    public static boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        return getMatcher(SAFE_SQL_IDENTIFIER_PATTERN, identifier).matches();
    }

    public static String validateSqlIdentifier(String identifier, String label) {
        if (CharSequenceUtil.isBlank(identifier) || !SAFE_SQL_IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new GXSqlInjectionException(label + " is not a safe SQL identifier: " + identifier);
        }
        return identifier;
    }

    public static String validateSqlAlias(String alias, String label) {
        if (CharSequenceUtil.isBlank(alias) || !SAFE_SQL_ALIAS_PATTERN.matcher(alias).matches()) {
            throw new GXSqlInjectionException(label + " is not a safe SQL alias: " + alias);
        }
        return alias;
    }

    public static String escapeJsonPath(String jsonPath) {
        if (jsonPath == null) {
            return null;
        }

        if (check(jsonPath)) {
            throw new GXSqlInjectionException("JSON path contains SQL injection risk");
        }

        if (getMatcher(JSON_INJECTION_PATTERN, jsonPath).find()) {
            throw new GXSqlInjectionException("JSON path contains NoSQL injection risk");
        }
        String escaped = escapeBasicChars(jsonPath, true);

        escaped = StrUtil.replace(escaped, ";", "\\;");
        escaped = StrUtil.replace(escaped, "--", "\\-\\-");
        escaped = StrUtil.replace(escaped, "/*", "\\/\\*");
        escaped = StrUtil.replace(escaped, "*/", "\\*\\/");

        return escaped;
    }

    public static void validateTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("Table name must not be empty");
        }
        validateSqlIdentifier(tableName, "Table name");
        if (check(tableName)) {
            String message = CharSequenceUtil.format("SQL injection risk detected in table name: {} (source: validateTableName)", tableName);
            throw new GXSqlInjectionException(message);
        }
    }

    public static void validateColumnName(String columnName) {
        if (columnName == null || columnName.isEmpty()) {
            throw new IllegalArgumentException("Column name must not be empty");
        }
        validateSqlIdentifier(columnName, "Column name");
        if (check(columnName)) {
            String message = CharSequenceUtil.format("SQL injection risk detected in column name: {} (source: validateColumnName)", columnName);
            throw new GXSqlInjectionException(message);
        }
    }

    public static List<String> processBatchSqlStatements(List<String> sqlStatements) {
        if (sqlStatements == null || sqlStatements.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> safeStatements = new ArrayList<>(sqlStatements.size());

        for (String sql : sqlStatements) {
            if (sql == null) {
                throw new IllegalArgumentException("SQL statement must not be null");
            }
            if (check(sql)) {
                String message = CharSequenceUtil.format("SQL injection risk detected in batch SQL: {}", sql);
                throw new GXSqlInjectionException(message);
            }

            safeStatements.add(sql);
        }

        return safeStatements;
    }

    public static Object[] prepareBatchParameterizedQuery(List<List<Object>> batchValues) {
        if (batchValues == null || batchValues.isEmpty()) {
            return new Object[]{"()", new ArrayList<>()};
        }

        List<Object> firstBatch = batchValues.get(0);
        if (firstBatch == null) {
            throw new IllegalArgumentException("First batch parameter list must not be null");
        }
        int paramCount = firstBatch.size();

        StringBuilder placeholders = new StringBuilder(paramCount * 3);
        placeholders.append('(');

        for (int i = 0; i < paramCount; i++) {
            if (i > 0) {
                placeholders.append(", ");
            }
            placeholders.append('?');
        }
        placeholders.append(')');

        for (List<Object> batch : batchValues) {
            if (batch == null) {
                throw new IllegalArgumentException("Batch parameter list must not be null");
            }
            if (batch.size() != paramCount) {
                throw new IllegalArgumentException("Batch parameter size must be consistent");
            }
            for (Object value : batch) {
                if (value instanceof String strValue && check(strValue)) {
                    String message = CharSequenceUtil.format("SQL injection risk detected in batch parameter: {}", strValue);
                    throw new GXSqlInjectionException(message);
                }
            }
        }

        return new Object[]{placeholders.toString(), batchValues};
    }
}
