package cn.maple.core.datasource.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ReUtil;
import cn.maple.core.datasource.builder.GXBaseBuilder;
import cn.maple.core.framework.constant.GXBuilderConstant;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXExclusionDeletedFieldCondition;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.jdbc.SQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings({"unused"})
public class GXDBCommonUtils {
    private static final Logger LOG = LoggerFactory.getLogger(GXDBCommonUtils.class);

    private static final int MAX_INPUT_LENGTH = 1024 * 1024; // 1MB

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)" + // 忽略大小写
                    "(" +
                    // 模式 1：SQL 注释和语句分隔符（独立出现）
                    "(?:--[\\s\\r\\n]*|#|/\\*|\\*/|;)" +
                    "|" +
                    // 模式 2：单引号后的注释或分隔符（'value' -- 或 'value';）
                    "'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'\\s*(?:--[\\s\\r\\n]*|#|/\\*|\\*/|;)" +
                    "|" +
                    // 模式 3：SQL 关键字（union select, drop, alter 等），但需后跟注入模式
                    "\\b(?:union\\s+(?:all\\s+)?select|select\\s+.*\\s+from|insert\\s+into|update\\s+.*\\s+set|delete\\s+from|drop\\s+(?:table|database)|alter\\s+(?:table|database)|truncate\\s+table|create\\s+(?:table|database))\\b" +
                    "\\s*(?:--[\\s\\r\\n]*|#|/\\*|\\*/|;|\\b(?:or|and)\\s+(?:\\d+\\s*=\\s*\\d+|'[^']+'\\s*=\\s*'[^']+'))" +
                    "|" +
                    // 模式 4：系统表和函数（information_schema, xp_cmdshell 等）
                    "\\b(?:information_schema\\.|sys\\.|sysobjects\\.|xp_cmdshell|sp_executesql|@@version|user\\s*\\(\\s*\\)|database\\s*\\(\\s*\\)|schema\\s*\\(\\s*\\))\\b" +
                    "|" +
                    // 模式 5：文件操作和延迟函数（load_file, outfile, sleep 等）
                    "\\b(?:load_file\\s*\\(|outfile\\s*\\(|dumpfile\\s*\\(|into\\s+(?:outfile|dumpfile)|sleep\\s*\\(\\s*\\d+\\s*\\)|benchmark\\s*\\(\\s*\\d+\\s*,\\s*[^)]+\\))\\b" +
                    "|" +
                    // 模式 6：逻辑操作符注入（or 1=1, and 'a'='a' 等）
                    "\\b(?:or|and)\\s+(?:" +
                    "\\d+\\s*=\\s*\\d+" + // 1=1
                    "|\\d+\\s*=\\s*\\d+\\s*(?:--[\\s\\r\\n]*|#)" + // 1=1 --
                    "|'[^']+'\\s*=\\s*'[^']+'" + // 'a'='a'
                    "|'[^']+'\\s*=\\s*'[^']+'\\s*(?:--[\\s\\r\\n]*|#)" + // 'a'='a' --
                    ")\\b" +
                    "|" +
                    // 模式 7：其他常见注入模式（exec, execute 等）
                    "\\b(?:exec\\s+\\w+|execute\\s+\\w+)\\b" +
                    ")"
    );

    // 合法子查询的正则表达式，用于豁免检测
    private static final Pattern LEGITIMATE_SUBQUERY_PATTERN = Pattern.compile(
            "^\\s*\\(\\s*SELECT\\s+.*\\s+FROM\\s+.*\\s*(?:WHERE\\s+.*)?\\s*\\)\\s*(?:UNION\\s+(?:ALL\\s+)?\\s*\\(\\s*SELECT\\s+.*\\s+FROM\\s+.*\\s*(?:WHERE\\s+.*)?\\s*\\)\\s*)*$",
            Pattern.CASE_INSENSITIVE
    );


    private GXDBCommonUtils() {
        // 私有构造函数，防止实例化
    }

    public static Dict addSearchCondition(Dict requestParam, String key, Object value, boolean returnRequestParam) {
        final Object obj = requestParam.getObj(GXBuilderConstant.SEARCH_CONDITION_NAME);
        if (null == obj) {
            return requestParam;
        }

        if (CharSequenceUtil.isNotEmpty(key) && GXDBStringEscapeUtils.check(key)) {
            String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: addSearchCondition.key)", key);
            LOG.error(message);
            throw new GXSqlInjectionException(message);
        }

        if (value instanceof String && GXDBStringEscapeUtils.check((String) value)) {
            String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: addSearchCondition.value)", value);
            LOG.error(message);
            throw new GXSqlInjectionException(message);
        }

        final Dict data = Convert.convert(Dict.class, obj);
        data.set(key, value);
        if (returnRequestParam) {
            requestParam.put(GXBuilderConstant.SEARCH_CONDITION_NAME, data);
            return requestParam;
        }
        return data;
    }

    public static Dict addSearchCondition(Dict requestParam, Dict sourceData, boolean returnRequestParam) {
        final Object obj = requestParam.getObj(GXBuilderConstant.SEARCH_CONDITION_NAME);
        if (null == obj) {
            return requestParam;
        }

        if (Objects.nonNull(sourceData)) {
            sourceData.forEach((k, v) -> {
                if (CharSequenceUtil.isNotEmpty(k) && GXDBStringEscapeUtils.check(k)) {
                    String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: addSearchCondition.sourceData.key)", k);
                    LOG.error(message);
                    throw new GXSqlInjectionException(message);
                }

                if (v instanceof String && GXDBStringEscapeUtils.check((String) v)) {
                    String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: addSearchCondition.sourceData.value)", v);
                    LOG.error(message);
                    throw new GXSqlInjectionException(message);
                }
            });
        }

        final Dict data = Convert.convert(Dict.class, obj);
        data.putAll(sourceData);
        if (returnRequestParam) {
            requestParam.put(GXBuilderConstant.SEARCH_CONDITION_NAME, data);
            return requestParam;
        }
        return data;
    }

    public static String getTableName(Class<?> clazz) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(clazz);
        return tableInfo.getTableName();
    }

    public static <R> GXPaginationResDto<R> convertPageToPaginationResDto(IPage<R> page, List<R> records) {
        long pages = page.getPages();
        long currentPage = page.getCurrent();
        long pageSize = page.getSize();
        long totalCount = page.getTotal();
        return new GXPaginationResDto<>(records, totalCount, pages, pageSize, currentPage);
    }

    public static <R> GXPaginationResDto<R> convertPageToPaginationResDto(IPage<R> page) {
        long pages = page.getPages();
        long currentPage = page.getCurrent();
        long pageSize = page.getSize();
        long totalCount = page.getTotal();
        return new GXPaginationResDto<>(page.getRecords(), totalCount, pages, pageSize, currentPage);
    }

    public static String generateJSONSearchExpression(String searchField, String searchExpression, Set<Object> searchValue) {
        if (CharSequenceUtil.isEmpty(searchField)) {
            throw new GXBusinessException("请传递搜索的字段");
        }
        if (CollUtil.isEmpty(searchValue)) {
            throw new GXBusinessException("请传递搜索的值");
        }

        if (GXDBStringEscapeUtils.check(searchField)) {
            String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: generateJSONSearchExpression.searchField)", searchField);
            LOG.error(message);
            throw new GXSqlInjectionException(message);
        }

        String safeSearchField = GXDBStringEscapeUtils.escapeJsonPath(searchField);

        if (CharSequenceUtil.isEmpty(searchExpression)) {
            searchExpression = "$";
        } else {
            if (GXDBStringEscapeUtils.check(searchExpression)) {
                String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: generateJSONSearchExpression.searchExpression)", searchExpression);
                LOG.error(message);
                throw new GXSqlInjectionException(message);
            }
            searchExpression = CharSequenceUtil.format("$.{}", GXDBStringEscapeUtils.escapeJsonPath(searchExpression));
        }

        String expressionTemplate = GXBuilderConstant.JSON_SEARCH_EXPRESSION_TEMPLATE;
        String searchStr = searchValue.stream().map(o -> {
            if (o instanceof Number) {
                return CharSequenceUtil.format("{}", o.toString());
            }
            String strValue = o.toString();
            if (GXDBStringEscapeUtils.check(strValue)) {
                String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: generateJSONSearchExpression.searchValue)", strValue);
                LOG.error(message);
                throw new GXSqlInjectionException(message);
            }
            return CharSequenceUtil.format("\"{}\"", GXDBStringEscapeUtils.escapeJson(strValue));
        }).collect(Collectors.joining(","));

        return CharSequenceUtil.format(expressionTemplate, safeSearchField, searchExpression, searchStr);
    }

    public static String getSelectSql(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return GXBaseBuilder.findByCondition(dbQueryParamInnerDto);
    }

    public static String getSelectOneSql(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return GXBaseBuilder.findOneByCondition(dbQueryParamInnerDto);
    }

    public static <T> UpdateWrapper<T> assemblyUpdateWrapper(List<GXCondition<?>> condition) {
        UpdateWrapper<T> updateWrapper = new UpdateWrapper<>();
        Dict methodNameDict = Dict.create()
                .set("=", "eq")
                .set("!=", "ne")
                .set("in", "in")
                .set("not in", "notIn")
                .set(">=", "ge")
                .set(">", "gt")
                .set("<=", "le")
                .set("<", "lt");

        condition.forEach(c -> {
            if (!GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass())) {
                String column = c.getFieldExpression();
                //Object value = c.getFieldValue();
                Object value = c.getFieldOriginalValue();

                if (String.class.isAssignableFrom(value.getClass())) {
                    value = CharSequenceUtil.replace(value.toString(), "'", "");
                }

                String op = c.getOp();
                column = CharSequenceUtil.toUnderlineCase(column);
                String methodName = methodNameDict.getStr(op);

                if (Objects.nonNull(methodName)) {
                    GXCommonUtils.reflectCallObjectMethod(updateWrapper, methodName, true, column, value);
                }
            }
        });

        return updateWrapper;
    }

    public static <R> IPage<R> constructPageObject(Integer page, Integer pageSize) {
        return constructPageObject(page, pageSize, true);
    }

    public static <R> IPage<R> constructPageObject(Integer page, Integer pageSize, boolean searchCount) {
        int defaultCurrentPage = GXCommonConstant.DEFAULT_CURRENT_PAGE;
        int defaultPageSize = GXCommonConstant.DEFAULT_PAGE_SIZE;
        int defaultMaxPageSize = GXCommonConstant.DEFAULT_MAX_PAGE_SIZE;
        if (Objects.isNull(page) || page < 0) {
            page = defaultCurrentPage;
        }
        if (Objects.isNull(pageSize) || pageSize > defaultMaxPageSize || pageSize <= 0) {
            pageSize = defaultPageSize;
        }
        return new Page<>(page, pageSize, searchCount);
    }

    public static void assemblySqlObjectCondition(SQL sql, Dict condition) {
        if (Objects.isNull(condition) || condition.isEmpty()) {
            return;
        }

        condition.forEach((column, val) -> {
            if (CharSequenceUtil.isNotEmpty(column) && GXDBStringEscapeUtils.check(column)) {
                String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: assemblySqlObjectCondition.column)", column);
                LOG.error(message);
                throw new GXSqlInjectionException(message);
            }

            String safeColumn = safeColumnName(column);

            final String value = Convert.toStr(val);

            if (CharSequenceUtil.isNotEmpty(value) && GXDBStringEscapeUtils.check(value)) {
                String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: assemblySqlObjectCondition.value)", value);
                LOG.error(message);
                throw new GXSqlInjectionException(message);
            }

            String template;
            String safeValue;

            if (ReUtil.isMatch("^[+-]?(0|([1-9]\\d*))(\\.\\d+)?$", value)) {
                template = "{} = {}";
                safeValue = value;
            } else {
                template = "{} = '{}'";
                safeValue = GXDBStringEscapeUtils.escapeSql(value);
            }

            sql.WHERE(CharSequenceUtil.format(template, safeColumn, safeValue));
        });
    }

    public static void checkSQLInjection(String input, String source, boolean isUserInput) {
        if (CharSequenceUtil.isEmpty(input)) {
            LOG.debug("输入为空，直接返回 (来源: {})", source);
            return;
        }

        if (input.length() > MAX_INPUT_LENGTH) {
            String message = CharSequenceUtil.format("输入长度超过最大限制 ({}): {} (来源: {})", MAX_INPUT_LENGTH, input, source);
            LOG.error(message);
            throw new IllegalArgumentException(message);
        }

        if (!isUserInput && input.length() <= 2048 && LEGITIMATE_SUBQUERY_PATTERN.matcher(input).matches()) {
            LOG.debug("输入是合法的子查询，豁免检测: {} (来源: {})", input, source);
            return;
        }

        if (ReUtil.contains(SQL_INJECTION_PATTERN, input)) {
            String message = CharSequenceUtil.format("第一步检测到潜在的SQL注入攻击: {} (来源: {})", input, source);
            LOG.error(message);
            throw new GXSqlInjectionException(message);
        }

        try {
            if (GXDBStringEscapeUtils.check(input)) {
                String message = CharSequenceUtil.format("第二步检测到潜在的SQL注入攻击: {} (来源: {})", input, source);
                LOG.error(message);
                throw new GXSqlInjectionException(message);
            }
        } catch (Exception e) {
            LOG.warn("使用GXDBStringEscapeUtils.check方法检测SQL注入时发生异常: {}", e.getMessage());
        }
    }

    public static String safeTableName(String tableName) {
        if (CharSequenceUtil.isEmpty(tableName)) {
            throw new GXBusinessException("表名不能为空");
        }

        checkSQLInjection(tableName, "tableName", false);

        if (CharSequenceUtil.containsAny(tableName, ";", "'", "\"", "\\", "/*", "--", "#")) {
            throw new GXSqlInjectionException("表名包含不允许的特殊字符: " + tableName);
        }

        return tableName;
    }

    public static String safeTableAlias(String tableAlias) {
        if (CharSequenceUtil.isEmpty(tableAlias)) {
            return null;
        }

        checkSQLInjection(tableAlias, "tableAlias", false);

        if (CharSequenceUtil.containsAny(tableAlias, ";", "'", "\"", "\\", "/*", "--", "#")) {
            throw new GXSqlInjectionException("表别名包含不允许的特殊字符: " + tableAlias);
        }

        return tableAlias;
    }

    public static String safeColumnName(String columnName) {
        if (CharSequenceUtil.isEmpty(columnName)) {
            throw new GXBusinessException("列名不能为空");
        }

        boolean isSqlFunction = isSqlFunctionCall(columnName);

        if (!isSqlFunction) {
            checkSQLInjection(columnName, "columnName", true);

            if (CharSequenceUtil.containsAny(columnName, ";", "'", "\"", "\\", "/*", "--", "#")) {
                throw new GXSqlInjectionException("列名包含不允许的特殊字符: " + columnName);
                //LOGGER.error("列名包含不允许的特殊字符: {}", columnName);
            }
        } else {
            if (CharSequenceUtil.containsAny(columnName, ";", "--", "#", "/*")) {
                LOG.error("SQL函数调用中包含可疑字符: {}", columnName);
                throw new GXSqlInjectionException("SQL函数调用中包含可疑字符: " + columnName);
            }
        }

        return columnName;
    }

    public static boolean isSqlFunctionCall(String str) {
        if (CharSequenceUtil.isEmpty(str)) {
            return false;
        }

        String functionPattern = "(?i)(GROUP_CONCAT|CONCAT|COUNT|SUM|AVG|MIN|MAX|DISTINCT|SUBSTRING|CAST|CONVERT|DATE_FORMAT|" +
                "IF|IFNULL|NULLIF|COALESCE|CASE|WHEN|THEN|ELSE|END|ROUND|FLOOR|CEILING|ABS|RAND|" +
                "LENGTH|CHAR_LENGTH|TRIM|LTRIM|RTRIM|LOWER|UPPER|REPLACE|REGEXP_REPLACE|" +
                "DATE|DATETIME|TIME|YEAR|MONTH|DAY|HOUR|MINUTE|SECOND|" +
                "JSON_EXTRACT|JSON_CONTAINS|JSON_OBJECT|JSON_ARRAY|" +
                "ST_Distance|ST_Contains|ST_Within|ST_Intersects)";

        String functionCallPattern = functionPattern + "\\s*\\([^)]*\\)(\\s+AS\\s+\\w+|\\s+\\w+)?";

        return str.matches(functionCallPattern) ||
                str.matches(".*" + functionPattern + "\\s*\\(.*\\).*") ||
                str.matches("(?i).*GROUP_CONCAT\\s*\\([^)]*SEPARATOR[^)]*\\).*");
    }
}
