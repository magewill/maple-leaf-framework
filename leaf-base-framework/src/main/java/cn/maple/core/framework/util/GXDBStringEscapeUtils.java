package cn.maple.core.framework.util;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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
            "(?i)('\\s*or\\s*'\\s*=\\s*')|" +  // 'or'=''
                    "(\\b(or|and)\\s+[\\w\\p{L}]+\\s*=\\s*[\\w\\p{L}]+)|" + // or column=value
                    "(\\bexec\\s*\\()|" +  // exec(
                    "(\\bunion\\s*(all|select))|" +  // union all/select
                    "(\\binsert\\s+into\\s+)|" +  // insert into
                    "(\\bdrop\\s+table\\s+)|" +  // drop table
                    "(\\balter\\s+table\\s+)|" +  // alter table
                    "(\\bdelete\\s+from\\s+)|" +  // delete from
                    "(\\bupdate\\s+.+\\s+set\\s+)|" +  // update set
                    "(;\\s*[\\w\\p{L}]+\\s*:)|" +  // ;label:
                    "(;\\s*declare\\s+)|" +  // ;declare
                    "(--[\\s\\r\\n])|" +  // SQL行注释
                    "(/\\*.*?\\*/)|" +  // SQL块注释
                    "(\\bwaitfor\\s+delay\\s+)|" +  // waitfor delay
                    "(\\bsleep\\s*\\(\\s*\\d+\\s*\\))|" +  // sleep()
                    "(\\bexecute\\s+immediate)|" +  // execute immediate
                    "(\\bcall\\s+\\w+)|" +  // call procedure
                    "(\\bbatch\\s+processing)|" +  // batch processing
                    "(\\bbegin\\s+transaction)|" +  // transaction
                    "(\\bcommit\\s*;)|" +  // commit
                    "(\\brollback\\s*;)");  // rollback


    private static final Pattern SQL_BLIND_INJECTION_PATTERN = Pattern.compile(
            "(?i)(\\bsleep\\s*\\(\\s*\\d+\\s*\\))|" +  // sleep()
                    "(\\bwaitfor\\s+delay\\s+'\\d+:\\d+:\\d+')|" +  // waitfor delay
                    "(\\bbenchmark\\s*\\(\\s*\\d+\\s*,)|" +  // benchmark()
                    "(\\bpg_sleep\\s*\\(\\s*\\d+\\s*\\))|" +  // pg_sleep()
                    "(\\bdbms_pipe\\.receive_message\\s*\\()|" +  // Oracle sleep
                    "(\\band\\s+\\d+=\\d+)|" +  // and 1=1
                    "(\\band\\s+\\d+>\\d+)|" +  // and 1>0
                    "(\\band\\s+\\d+<\\d+)|" +  // and 1<0
                    "(\\bif\\s*\\(\\s*\\d+\\s*=\\s*\\d+\\s*\\))|" +  // if(1=1)
                    "(\\bselect\\s+case\\s+when\\s+)|" +  // select case when
                    "(\\bextractvalue\\s*\\()|" +  // extractvalue()
                    "(\\bsys\\.\\w+\\s*\\()|" +  // sys.function()
                    "(\\bsqlmap)|" +  // sqlmap signature
                    "(\\btrue--)|" +  // true--
                    "(\\b1=1--)");  // 1=1--

    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(?i)(<script[^>]*>)|" +  // <script>
                    "(</script>)|" +  // </script>
                    "(<[^>]*\\bon\\w+\\s*=)|" +  // 事件处理程序
                    "(\\balert\\s*\\()|" +  // alert()
                    "(\\bdocument\\.cookie)|" +  // document.cookie
                    "(\\blocation\\.href)|" +  // location.href
                    "(javascript:)");  // javascript:

    private static final Pattern JSON_INJECTION_PATTERN = Pattern.compile(
            "(?i)(\"\\s*:\\s*\\{)|" +  // ":{
                    "(\\}\\s*,\\s*\")|" +  // },"
                    "(\\]\\s*,\\s*\\[)|" +  // ],[
                    "(\\[\\s*\\]\\s*,\\s*\\[)|" +  // ,[]
                    "(\\}\\s*\\]\\s*,\\s*\\[\\s*\\{)|" +  // }],[{
                    "(\"\\s*:\\s*function\\s*\\()|" +  // ":function(
                    "(\"\\s*:\\s*new\\s+)|" +  // ":new
                    "(\\$where\\s*:)|" +  // $where: (MongoDB注入)
                    "(\\$regex\\s*:)|" +  // $regex: (MongoDB注入)
                    "(\\$ne\\s*:)|" +  // $ne: (MongoDB注入)
                    "(\\$gt\\s*:)|" +  // $gt: (MongoDB注入)
                    "(\\$exists\\s*:)|" +  // $exists: (MongoDB注入)
                    "(\\$elemMatch\\s*:)|" +  // $elemMatch: (MongoDB注入)
                    "(\\$text\\s*:)|" +  // $text: (MongoDB注入)
                    "(\\$expr\\s*:)|" +  // $expr: (MongoDB注入)
                    "(\\$jsonSchema\\s*:)|" +  // $jsonSchema: (MongoDB注入)
                    "(\\$mod\\s*:)|" +  // $mod: (MongoDB注入)
                    "(\\$type\\s*:)|" +  // $type: (MongoDB注入)
                    "(\\$eval\\s*:)|" +  // $eval: (MongoDB注入，高危)
                    "(\\$function\\s*:)");  // $function: (MongoDB注入，高危)

    private static final Pattern SAFE_IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\.]+$");

    private static final ConcurrentHashMap<Pattern, ThreadLocal<Matcher>> MATCHER_CACHE = new ConcurrentHashMap<>();

    private static boolean isEscapeNeededForString(String str, int len) {
        boolean needsHexEscape = false;
        for (int i = 0; i < len; ++i) {
            char c = str.charAt(i);
            switch (c) {
                /* Must be escaped for 'mysql' */
                case 0:
                    needsHexEscape = true;
                    break;
                /* Must be escaped for logs */
                case '\n':
                    needsHexEscape = true;
                    break;
                case '\r':
                    needsHexEscape = true;
                    break;
                case '\\':
                    needsHexEscape = true;
                    break;
                case '\'':
                    needsHexEscape = true;
                    break;
                /* Better safe than sorry */
                case '"':
                    needsHexEscape = true;
                    break;
                /* This gives problems on Win32 */
                case '\032':
                    needsHexEscape = true;
                    break;
                default:
                    break;
            }
            if (needsHexEscape) {
                // no need to scan more
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
                /* Must be escaped for 'mysql' */
                case 0:
                    buf.append('\\');
                    buf.append('0');
                    break;
                /* Must be escaped for logs */
                case '\n':
                    buf.append('\\');
                    buf.append('n');
                    break;
                case '\r':
                    buf.append('\\');
                    buf.append('r');
                    break;
                case '\\':
                    buf.append('\\');
                    buf.append('\\');
                    break;
                case '\'':
                    if (useSqlStandardQuoteEscape) {
                        buf.append('\'');
                        buf.append('\'');
                    } else {
                        buf.append('\\');
                        buf.append('\'');
                    }
                    break;
                /* Better safe than sorry */
                case '"':
                    buf.append('\\');
                    buf.append('"');
                    break;
                /* This gives problems on Win32 */
                case '\032':
                    buf.append('\\');
                    buf.append('Z');
                    break;
                default:
                    buf.append(c);
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
        ThreadLocal<Matcher> threadLocal = MATCHER_CACHE.computeIfAbsent(pattern, p -> new ThreadLocal<>());
        Matcher matcher = threadLocal.get();
        if (matcher == null) {
            matcher = pattern.matcher("");
            threadLocal.set(matcher);
        }
        return matcher.reset(input);
    }

    public static boolean check(String value) {
        Objects.requireNonNull(value);
        return getMatcher(SQL_COMMENT_PATTERN, value).find() ||
                getMatcher(SQL_SYNTAX_PATTERN, value).find() ||
                getMatcher(SQL_INJECTION_PATTERN, value).find() ||
                getMatcher(SQL_BLIND_INJECTION_PATTERN, value).find();
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
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '/':
                    sb.append("\\/");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
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
        return sb.toString();
    }

    public static String escapeJsonForSql(String jsonStr) {
        if (jsonStr == null) {
            return null;
        }

        if (check(jsonStr)) {
            throw new GXSqlInjectionException("JSON字符串中包含SQL注入风险");
        }

        if (checkJsonInjection(jsonStr)) {
            throw new GXSqlInjectionException("JSON字符串中包含JSON注入风险");
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
            String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: validateAndCleanInput)", trimmedInput);
            throw new GXSqlInjectionException(message);
        }

        return escapeSql(trimmedInput);
    }

    public static String buildSafeLikeCondition(String column, String value, String matchType) {
        if (CharSequenceUtil.isBlank(column) || value == null) {
            throw new IllegalArgumentException("列名和查询值不能为空");
        }

        if (!isValidIdentifier(column)) {
            String message = CharSequenceUtil.format("列名包含不安全的字符: {} (来源: buildSafeLikeCondition)", column);
            throw new GXSqlInjectionException(message);
        }

        if (check(value)) {
            String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: buildSafeLikeCondition)", value);
            throw new GXSqlInjectionException(message);
        }

        String escapedValue = escapeSqlForLike(value);
        String likePattern;

        switch (matchType.toLowerCase()) {
            case "start":
                likePattern = escapedValue + "%";
                break;
            case "end":
                likePattern = "%" + escapedValue;
                break;
            case "anywhere":
                likePattern = "%" + escapedValue + "%";
                break;
            default:
                throw new IllegalArgumentException("不支持的匹配类型: " + matchType);
        }

        return column + " LIKE '" + likePattern + "' ESCAPE '\\'";
    }

    public static boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        return getMatcher(SAFE_IDENTIFIER_PATTERN, identifier).matches();
    }

    public static String escapeJsonPath(String jsonPath) {
        if (jsonPath == null) {
            return null;
        }

        if (check(jsonPath)) {
            throw new GXSqlInjectionException("JSON路径中包含SQL注入风险");
        }

        if (getMatcher(JSON_INJECTION_PATTERN, jsonPath).find()) {
            throw new GXSqlInjectionException("JSON路径中包含NoSQL注入风险");
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
            throw new IllegalArgumentException("表名不能为空");
        }

        if (!isValidIdentifier(tableName)) {
            String message = CharSequenceUtil.format("表名包含不安全的字符: {} (来源: validateTableName)", tableName);
            throw new GXSqlInjectionException(message);
        }

        if (check(tableName)) {
            String message = CharSequenceUtil.format("表名中检测到潜在的SQL注入攻击: {} (来源: validateTableName)", tableName);
            throw new GXSqlInjectionException(message);
        }
    }

    public static void validateColumnName(String columnName) {
        if (columnName == null || columnName.isEmpty()) {
            throw new IllegalArgumentException("列名不能为空");
        }

        if (!isValidIdentifier(columnName)) {
            String message = CharSequenceUtil.format("列名包含不安全的字符: {} (来源: validateColumnName)", columnName);
            throw new GXSqlInjectionException(message);
        }

        if (check(columnName)) {
            String message = CharSequenceUtil.format("列名中检测到潜在的SQL注入攻击: {} (来源: validateColumnName)", columnName);
            throw new GXSqlInjectionException(message);
        }
    }

    public static List<String> processBatchSqlStatements(List<String> sqlStatements) {
        if (sqlStatements == null || sqlStatements.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> safeStatements = new ArrayList<>(sqlStatements.size());

        for (String sql : sqlStatements) {
            if (check(sql)) {
                String message = CharSequenceUtil.format("批量SQL操作中检测到潜在的SQL注入攻击: {}", sql);
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
            for (Object value : batch) {
                if (value instanceof String strValue) {
                    if (check(strValue)) {
                        String message = CharSequenceUtil.format("批量参数化查询中检测到潜在的SQL注入攻击: {}", strValue);
                        throw new GXSqlInjectionException(message);
                    }
                }
            }
        }

        return new Object[]{placeholders.toString(), batchValues};
    }
}