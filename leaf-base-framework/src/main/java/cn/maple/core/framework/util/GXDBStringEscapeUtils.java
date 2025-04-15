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

/**
 * GXDBStringEscapeUtils ，数据库字符串转义
 * 提供SQL注入防护和字符串转义功能
 *
 * @author 塵子曦
 */
public class GXDBStringEscapeUtils {
    /**
     * SQL语法检测模式 - 检测常见SQL语句结构
     */
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

    /**
     * SQL注释检测模式 - 检测SQL注释和常见注入手段
     */
    private static final Pattern SQL_COMMENT_PATTERN = Pattern.compile(
            "'[^']*'\\s*(?:--|#|/\\*|;)|\\s+(?:or|union)\\s+",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 检测常见的SQL注入攻击模式
     * 增强了对各种SQL注入技术的检测，包括联合查询、批量操作和存储过程调用
     */
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

    /**
     * 检测SQL盲注攻击模式
     * 增强了对时间延迟注入和条件注入的检测
     */
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

    /**
     * 检测XSS攻击模式（在SQL上下文中）
     */
    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(?i)(<script[^>]*>)|" +  // <script>
                    "(</script>)|" +  // </script>
                    "(<[^>]*\\bon\\w+\\s*=)|" +  // 事件处理程序
                    "(\\balert\\s*\\()|" +  // alert()
                    "(\\bdocument\\.cookie)|" +  // document.cookie
                    "(\\blocation\\.href)|" +  // location.href
                    "(javascript:)");  // javascript:

    /**
     * 检测JSON注入模式
     * 增强了对JSON结构破坏和NoSQL注入攻击的检测
     */
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

    /**
     * 表名和列名安全验证模式
     * 用于验证表名和列名是否包含不安全的字符
     * 只允许字母、数字、下划线和点号
     */
    private static final Pattern SAFE_IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\.]+$");

    /**
     * 检查字符串是否包含需要转义的特殊字符
     * 该方法会扫描字符串中的每个字符，一旦发现需要转义的字符就返回true
     *
     * @param str 需要检查的字符串
     * @param len 字符串的长度
     * @return 如果字符串包含需要转义的特殊字符返回true，否则返回false
     */
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

    /**
     * 基础字符转义方法，处理常见的SQL特殊字符
     * 该方法是内部使用的，提取了escapeRawString和escapeSql的共同转义逻辑
     *
     * @param input                     需要转义的字符串
     * @param useSqlStandardQuoteEscape 是否使用SQL标准的单引号转义（''而不是\'）
     * @return 转义后的字符串
     */
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

    /**
     * 转义字符串。纯转义，不添加单引号。
     *
     * @param escapeStr 被转义的字符串
     * @return 转义后的字符串
     */
    public static String escapeRawString(String escapeStr) {
        if (escapeStr == null) {
            return null;
        }
        return escapeBasicChars(escapeStr, false);
    }

    /**
     * 转义字符串
     *
     * @param escapeStr 被转义的字符串
     * @return 转义后的字符串
     */
    public static String escapeString(String escapeStr) {
        if (escapeStr == null) {
            return null;
        }
        // 优化字符串匹配，避免使用正则表达式提高性能
        if (escapeStr.length() >= 2 && escapeStr.charAt(0) == '\'' && escapeStr.charAt(escapeStr.length() - 1) == '\'') {
            escapeStr = escapeStr.substring(1, escapeStr.length() - 1);
        }
        // 使用SQL标准的单引号转义（''而不是\'）
        return "'" + escapeBasicChars(escapeStr, true) + "'";
    }

    /**
     * 线程安全的Pattern匹配器缓存，避免重复创建Matcher对象
     */
    private static final ConcurrentHashMap<Pattern, ThreadLocal<Matcher>> MATCHER_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 获取线程安全的Matcher对象
     * 
     * @param pattern 正则表达式模式
     * @param input 输入字符串
     * @return 匹配器对象
     */
    private static Matcher getMatcher(Pattern pattern, String input) {
        ThreadLocal<Matcher> threadLocal = MATCHER_CACHE.computeIfAbsent(pattern, p -> new ThreadLocal<>());
        Matcher matcher = threadLocal.get();
        if (matcher == null) {
            matcher = pattern.matcher("");
            threadLocal.set(matcher);
        }
        return matcher.reset(input);
    }
    
    /**
     * 检查字符串是否包含SQL注入风险
     * 使用多种模式进行全面检测
     *
     * @param value 需要检查的字符串
     * @return 如果存在SQL注入风险返回true，否则返回false
     * @throws NullPointerException 如果输入为null
     */
    public static boolean check(String value) {
        Objects.requireNonNull(value);
        return getMatcher(SQL_COMMENT_PATTERN, value).find() ||
               getMatcher(SQL_SYNTAX_PATTERN, value).find() ||
               getMatcher(SQL_INJECTION_PATTERN, value).find() ||
               getMatcher(SQL_BLIND_INJECTION_PATTERN, value).find();
    }

    /**
     * 全面检查字符串是否包含SQL注入或XSS风险
     *
     * @param value 需要检查的字符串
     * @return 如果存在安全风险返回true，否则返回false
     * @throws NullPointerException 如果输入为null
     */
    public static boolean checkComprehensive(String value) {
        Objects.requireNonNull(value);
        return check(value) || getMatcher(XSS_PATTERN, value).find();
    }

    /**
     * 检查JSON字符串是否包含注入风险
     *
     * @param jsonStr JSON字符串
     * @return 如果存在注入风险返回true，否则返回false
     * @throws NullPointerException 如果输入为null
     */
    public static boolean checkJsonInjection(String jsonStr) {
        Objects.requireNonNull(jsonStr);
        return getMatcher(JSON_INJECTION_PATTERN, jsonStr).find() || check(jsonStr);
    }

    /**
     * 移除字符串中的转义字符
     *
     * @param text 需要处理的字符串
     * @return 处理后的字符串
     * @throws NullPointerException 如果输入为null
     */
    public static String removeEscapeCharacter(String text) {
        Objects.requireNonNull(text);
        // 使用单次替换提高性能
        return text.replaceAll("[\"']", "");
    }

    /**
     * 移除所有可能导致SQL注入的特殊字符
     * 适用于不需要保留原始格式的场景
     *
     * @param input 需要处理的字符串
     * @return 处理后的字符串
     */
    public static String removeAllSpecialChars(String input) {
        if (input == null) {
            return null;
        }
        // 移除所有非字母数字字符
        return input.replaceAll("[^\\p{L}\\p{N}\\s]", "");
    }

    /**
     * 转义 SQL 中的特殊字符，处理包括：
     * <ul>
     *   <li>反斜杠： "\" 替换为 "\\\\"</li>
     *   <li>空字符： "\0" 替换为 "\\0"</li>
     *   <li>换行符： "\n" 替换为 "\\n"</li>
     *   <li>回车符： "\r" 替换为 "\\r"</li>
     *   <li>Ctrl+Z（\032）： 替换为 "\\Z"</li>
     *   <li>单引号： "'" 替换为 "''"（SQL 标准转义）</li>
     *   <li>双引号： "\"" 替换为 "\\\""</li>
     *   <li>分号： ";" 替换为 "\\;"</li>
     *   <li>等号： "=" 替换为 "\\="</li>
     *   <li>连字符： "-" 替换为 "\\-"</li>
     * </ul>
     *
     * @param input 原始字符串
     * @return 转义后的字符串
     */
    public static String escapeSql(String input) {
        if (input == null) {
            return null;
        }
        // 使用基础转义方法处理常见的SQL特殊字符，使用SQL标准的单引号转义
        String result = escapeBasicChars(input, true);

        // 增加对其他可能用于SQL注入的字符进行转义
        result = StrUtil.replace(result, ";", "\\;");
        result = StrUtil.replace(result, "=", "\\=");
        result = StrUtil.replace(result, "-", "\\-");
        result = StrUtil.replace(result, "#", "\\#");
        result = StrUtil.replace(result, "/*", "\\/\\*");
        result = StrUtil.replace(result, "*/", "\\*\\/");
        return result;
    }

    /**
     * 针对 SQL LIKE 查询的转义方法。
     * 除了执行常规 SQL 字符转义外，还需要对 LIKE 模式下的通配符进行转义：
     * <ul>
     *   <li>% -> ESCAPE字符 + %</li>
     *   <li>_ -> ESCAPE字符 + _</li>
     * </ul>
     * 并且在 SQL 中需要指定 ESCAPE 字符。
     *
     * @param input      原始字符串
     * @param escapeChar 用于转义的字符，一般为 '\'（反斜杠）
     * @return 转义后的字符串
     */
    public static String escapeSqlForLike(String input, char escapeChar) {
        if (input == null) {
            return null;
        }
        // 先进行常规 SQL 转义
        String escaped = escapeSql(input);
        String escStr = String.valueOf(escapeChar);
        // 对 LIKE 模式下的特殊字符 % 和 _ 进行转义
        escaped = StrUtil.replace(escaped, "%", escStr + "%");
        escaped = StrUtil.replace(escaped, "_", escStr + "_");
        return escaped;
    }

    /**
     * 使用默认的反斜杠作为转义字符进行LIKE查询转义
     *
     * @param input 原始字符串
     * @return 转义后的字符串
     */
    public static String escapeSqlForLike(String input) {
        return escapeSqlForLike(input, '\\');
    }

    /**
     * 转义JSON字符串中的特殊字符，防止JSON注入
     * <p>
     * 该方法专门用于处理JSON格式的字符串，确保JSON特殊字符被正确转义。
     * 与SQL转义不同，JSON转义主要关注JSON语法中的特殊字符。
     * </p>
     *
     * @param jsonInput JSON格式的字符串
     * @return 转义后的JSON字符串
     */
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
                    // 处理控制字符
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

    /**
     * 转义JSON字符串用于SQL语句
     * <p>
     * 该方法专门用于处理需要在SQL语句中使用的JSON字符串。
     * 它只进行SQL转义而不进行JSON转义，避免双重转义问题。
     * 适用于需要将JSON字符串作为参数传递给SQL语句的场景，特别是在CAST('{}' AS JSON)语句中。
     * </p>
     *
     * @param jsonStr JSON格式的字符串
     * @return 转义后可以安全用于SQL语句的JSON字符串
     * @throws GXSqlInjectionException 当检测到SQL注入风险时抛出
     */
    public static String escapeJsonForSql(String jsonStr) {
        if (jsonStr == null) {
            return null;
        }

        // 检查是否存在SQL注入风险
        if (check(jsonStr)) {
            throw new GXSqlInjectionException("JSON字符串中包含SQL注入风险");
        }

        // 检查是否存在JSON注入风险
        if (checkJsonInjection(jsonStr)) {
            throw new GXSqlInjectionException("JSON字符串中包含JSON注入风险");
        }

        // 对于JSON字符串，我们只需要进行SQL转义，不需要进行JSON转义
        // 因为JSON字符串本身已经是合法的JSON格式，只需要确保SQL语法安全
        return escapeSql(jsonStr);
    }

    /**
     * 生成用于参数化查询的占位符和参数列表
     * 帮助开发者构建安全的参数化SQL查询
     *
     * @param values 参数值列表
     * @return 包含占位符字符串和参数列表的数组，索引0为占位符字符串，索引1为参数列表
     */
    public static Object[] prepareParameterizedQuery(List<Object> values) {
        if (values == null || values.isEmpty()) {
            return new Object[]{"()", new ArrayList<>()};
        }

        int paramCount = values.size();
        // 使用预计算的容量初始化StringBuilder，提高性能
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
    

    /**
     * 安全地构建SQL IN子句，防止SQL注入
     * 该方法会过滤掉包含SQL注入风险的值，并对其余值进行SQL转义
     * 如果所有值都被过滤掉，将返回一个不匹配任何内容的条件 ('')
     *
     * @param values 字符串值列表
     * @return 安全的IN子句字符串，格式如 ('value1', 'value2')
     */
    public static String buildSafeInClause(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "('')"; // 返回一个不匹配任何内容的条件
        }

        // 预估StringBuilder的初始容量，避免频繁扩容
        StringBuilder sb = new StringBuilder(values.size() * 10);
        sb.append('(');
        boolean first = true;

        for (String value : values) {
            // 检查每个值是否有SQL注入风险
            if (value != null && check(value)) {
                continue; // 跳过有风险的值
            }

            if (!first) {
                sb.append(", ");
            }
            sb.append('\'').append(value == null ? "" : escapeSql(value)).append('\'');
            first = false;
        }

        // 如果所有值都被过滤掉了
        if (first) {
            return "('')";
        }

        sb.append(')');
        return sb.toString();
    }
    

    /**
     * 验证并清理输入字符串，确保其可以安全用于SQL查询
     * <p>
     * 该方法会检查输入字符串是否包含SQL注入风险，如果有风险则抛出异常。
     * 否则，对字符串进行转义处理，确保其可以安全用于SQL查询。
     * 适用于需要直接拼接到SQL语句中的字符串参数。
     * </p>
     *
     * @param input 需要验证和清理的输入字符串
     * @return 清理后的安全字符串
     * @throws GXSqlInjectionException 当检测到SQL注入风险时抛出
     */
    public static String validateAndCleanInput(String input) {
        if (input == null) {
            return null;
        }

        // 先去除首尾空白字符
        String trimmedInput = input.trim();

        // 检查是否存在SQL注入风险
        if (check(trimmedInput)) {
            String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: validateAndCleanInput)", trimmedInput);
            throw new GXSqlInjectionException(message);
        }

        // 清理并返回安全的字符串
        return escapeSql(trimmedInput);
    }

    /**
     * 安全地构建SQL LIKE模糊查询条件
     * <p>
     * 该方法会检查输入值是否包含SQL注入风险，如果有风险则抛出异常。
     * 根据指定的匹配类型，构建不同的LIKE模式（前缀、后缀或包含匹配）。
     * 同时会验证列名是否安全，确保不会引入SQL注入风险。
     * </p>
     *
     * @param column    列名，不能为空
     * @param value     查询值，不能为null
     * @param matchType 匹配类型：'start'(前缀匹配), 'end'(后缀匹配), 'anywhere'(包含匹配)
     * @return 安全的LIKE条件字符串
     * @throws GXSqlInjectionException 当检测到SQL注入风险时抛出
     */
    public static String buildSafeLikeCondition(String column, String value, String matchType) {
        if (CharSequenceUtil.isBlank(column) || value == null) {
            throw new IllegalArgumentException("列名和查询值不能为空");
        }

        // 验证列名是否安全
        if (!isValidIdentifier(column)) {
            String message = CharSequenceUtil.format("列名包含不安全的字符: {} (来源: buildSafeLikeCondition)", column);
            throw new GXSqlInjectionException(message);
        }

        // 检查是否存在SQL注入风险
        if (check(value)) {
            String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: buildSafeLikeCondition)", value);
            throw new GXSqlInjectionException(message);
        }

        // 对值进行LIKE特定的转义处理
        String escapedValue = escapeSqlForLike(value);
        String likePattern;

        // 根据匹配类型构建不同的LIKE模式
        switch (matchType.toLowerCase()) {
            case "start":
                likePattern = escapedValue + "%"; // 前缀匹配
                break;
            case "end":
                likePattern = "%" + escapedValue; // 后缀匹配
                break;
            case "anywhere":
                likePattern = "%" + escapedValue + "%"; // 包含匹配
                break;
            default:
                throw new IllegalArgumentException("不支持的匹配类型: " + matchType);
        }

        // 构建完整的LIKE条件，并指定转义字符
        return column + " LIKE '" + likePattern + "' ESCAPE '\\'";
    }

    /**
     * 验证标识符（表名、列名等）是否安全
     * <p>
     * 该方法用于验证SQL标识符是否只包含安全的字符。
     * 安全的标识符只应包含字母、数字、下划线和点号。
     * 这有助于防止SQL注入攻击，特别是在动态构建SQL语句时。
     * </p>
     *
     * @param identifier 需要验证的标识符（表名、列名等）
     * @return 如果标识符安全返回true，否则返回false
     */
    public static boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        return getMatcher(SAFE_IDENTIFIER_PATTERN, identifier).matches();
    }

    /**
     * 转义JSON路径字符串，确保路径中的特殊字符被正确处理
     * 该方法专门用于处理JSON_SET、JSON_EXTRACT等函数中的路径参数
     * <p>
     * JSON路径在MySQL中需要特别处理，因为它们通常被单引号包裹，
     * 并且可能包含特殊字符如$、[、]、.等，这些在SQL上下文中需要正确转义。
     * </p>
     *
     * @param jsonPath JSON路径字符串，例如 $.name 或 $[0].items
     * @return 转义后的JSON路径字符串
     * @throws GXSqlInjectionException 当检测到SQL注入风险时抛出
     */
    public static String escapeJsonPath(String jsonPath) {
        if (jsonPath == null) {
            return null;
        }

        // 检查是否存在SQL注入风险
        if (check(jsonPath)) {
            throw new GXSqlInjectionException("JSON路径中包含SQL注入风险");
        }

        // 检查是否包含NoSQL注入风险
        if (getMatcher(JSON_INJECTION_PATTERN, jsonPath).find()) {
            throw new GXSqlInjectionException("JSON路径中包含NoSQL注入风险");
        }

        // MySQL的JSON路径处理比较特殊
        // 1. 单引号需要使用SQL标准转义（''）
        // 2. 其他特殊字符如$、[、]等在MySQL的JSON_SET等函数中有特殊含义，不需要转义
        // 3. 但是需要处理可能导致SQL注入的字符，如分号、注释符等

        // 只处理单引号和基本SQL特殊字符，保留JSON路径的特殊语法
        String escaped = escapeBasicChars(jsonPath, true);

        // 额外处理可能导致SQL注入的字符
        escaped = StrUtil.replace(escaped, ";", "\\;");
        escaped = StrUtil.replace(escaped, "--", "\\-\\-");
        escaped = StrUtil.replace(escaped, "/*", "\\/\\*");
        escaped = StrUtil.replace(escaped, "*/", "\\*\\/");

        return escaped;
    }

    /**
     * 验证表名是否安全
     * <p>
     * 该方法用于验证表名是否只包含安全的字符，并且不包含SQL注入风险。
     * 如果表名不安全，将抛出异常。适用于需要动态构建表名的场景。
     * </p>
     *
     * @param tableName 需要验证的表名
     * @throws GXSqlInjectionException 当表名包含不安全字符或SQL注入风险时抛出
     */
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

    /**
     * 验证列名是否安全
     * <p>
     * 该方法用于验证列名是否只包含安全的字符，并且不包含SQL注入风险。
     * 如果列名不安全，将抛出异常。适用于需要动态构建列名的场景。
     * </p>
     *
     * @param columnName 需要验证的列名
     * @throws GXSqlInjectionException 当列名包含不安全字符或SQL注入风险时抛出
     */
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

    /**
     * 安全地处理批量SQL操作
     * <p>
     * 该方法用于处理批量SQL操作，确保每个SQL语句都经过安全检查和转义处理。
     * 它会检查每个SQL语句是否包含SQL注入风险，如果有风险则抛出异常。
     * 适用于需要执行多条SQL语句的场景，如批量插入、更新等。
     * </p>
     *
     * @param sqlStatements 需要执行的SQL语句列表
     * @return 经过安全处理的SQL语句列表
     * @throws GXSqlInjectionException 当检测到SQL注入风险时抛出
     */
    public static List<String> processBatchSqlStatements(List<String> sqlStatements) {
        if (sqlStatements == null || sqlStatements.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> safeStatements = new ArrayList<>(sqlStatements.size());

        for (String sql : sqlStatements) {
            // 检查是否存在SQL注入风险
            if (check(sql)) {
                String message = CharSequenceUtil.format("批量SQL操作中检测到潜在的SQL注入攻击: {}", sql);
                throw new GXSqlInjectionException(message);
            }
            
            // 已经通过安全检查的SQL语句直接添加到结果列表中
            safeStatements.add(sql);
        }

        return safeStatements;
    }

    /**
     * 安全地构建批量参数化查询
     * <p>
     * 该方法用于构建批量参数化查询，确保每个参数都经过安全检查和转义处理。
     * 它会为每组参数生成对应的占位符，并返回包含占位符和参数列表的数组。
     * 适用于需要执行批量参数化查询的场景，如批量插入、更新等。
     * </p>
     *
     * @param batchValues 批量参数值列表，每个元素是一组参数
     * @return 包含占位符字符串和参数列表的数组，索引0为占位符字符串，索引1为参数列表的列表
     * @throws GXSqlInjectionException 当检测到SQL注入风险时抛出
     */
    public static Object[] prepareBatchParameterizedQuery(List<List<Object>> batchValues) {
        if (batchValues == null || batchValues.isEmpty()) {
            return new Object[]{"()", new ArrayList<>()};
        }

        // 假设所有批次的参数数量相同，使用第一组参数构建占位符
        List<Object> firstBatch = batchValues.get(0);
        int paramCount = firstBatch.size();
        
        // 使用预计算的容量初始化StringBuilder，提高性能
        StringBuilder placeholders = new StringBuilder(paramCount * 3);
        placeholders.append('(');

        for (int i = 0; i < paramCount; i++) {
            if (i > 0) {
                placeholders.append(", ");
            }
            placeholders.append('?');
        }
        placeholders.append(')');

        // 检查每组参数的值是否存在SQL注入风险
        for (List<Object> batch : batchValues) {
            for (Object value : batch) {
                if (value instanceof String) {
                    String strValue = (String) value;
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