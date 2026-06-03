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
import cn.maple.core.framework.dto.inner.condition.GXConditionIsNULL;
import cn.maple.core.framework.dto.inner.condition.GXConditionIsNotNULL;
import cn.maple.core.framework.dto.inner.condition.GXConditionRaw;
import cn.maple.core.framework.dto.inner.condition.GXExclusionDeletedFieldCondition;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXDBStringUtils;
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
public final class GXDBCommonUtils {
    private static final Logger LOG = LoggerFactory.getLogger(GXDBCommonUtils.class);

    private static final int MAX_INPUT_LENGTH = 1024 * 1024;

    private static final Pattern SAFE_QUALIFIED_IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$");

    private static final Pattern SAFE_ALIAS_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)" +
                    "(" +
                    "(?:--[\\s\\r\\n]*|#|/\\*|\\*/|;)" +
                    "|" +
                    "'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'\\s*(?:--[\\s\\r\\n]*|#|/\\*|\\*/|;)" +
                    "|" +
                    "\\b(?:union\\s+(?:all\\s+)?select|select\\s+.*\\s+from|insert\\s+into|update\\s+.*\\s+set|delete\\s+from|drop\\s+(?:table|database)|alter\\s+(?:table|database)|truncate\\s+table|create\\s+(?:table|database))\\b" +
                    "\\s*(?:--[\\s\\r\\n]*|#|/\\*|\\*/|;|\\b(?:or|and)\\s+(?:\\d+\\s*=\\s*\\d+|'[^']+'\\s*=\\s*'[^']+'))" +
                    "|" +
                    "\\b(?:information_schema\\.|sys\\.|sysobjects\\.|xp_cmdshell|sp_executesql|@@version|user\\s*\\(\\s*\\)|database\\s*\\(\\s*\\)|schema\\s*\\(\\s*\\))\\b" +
                    "|" +
                    "\\b(?:load_file\\s*\\(|outfile\\s*\\(|dumpfile\\s*\\(|into\\s+(?:outfile|dumpfile)|sleep\\s*\\(\\s*\\d+\\s*\\)|benchmark\\s*\\(\\s*\\d+\\s*,\\s*[^)]+\\))\\b" +
                    "|" +
                    "\\b(?:or|and)\\s+(?:" +
                    "\\d+\\s*=\\s*\\d+" +
                    "|\\d+\\s*=\\s*\\d+\\s*(?:--[\\s\\r\\n]*|#)" +
                    "|'[^']+'\\s*=\\s*'[^']+'" +
                    "|'[^']+'\\s*=\\s*'[^']+'\\s*(?:--[\\s\\r\\n]*|#)" +
                    ")\\b" +
                    "|" +
                    "\\b(?:exec\\s+\\w+|execute\\s+\\w+)\\b" +
                    ")"
    );

    private static final Pattern LEGITIMATE_SUBQUERY_PATTERN = Pattern.compile(
            "^\\s*\\(\\s*SELECT\\s+.*\\s+FROM\\s+.*\\s*(?:WHERE\\s+.*)?\\s*\\)\\s*(?:UNION\\s+(?:ALL\\s+)?\\s*\\(\\s*SELECT\\s+.*\\s+FROM\\s+.*\\s*(?:WHERE\\s+.*)?\\s*\\)\\s*)*$",
            Pattern.CASE_INSENSITIVE
    );

    private GXDBCommonUtils() {
    }

    public static Dict addSearchCondition(Dict requestParam, String key, Object value, boolean returnRequestParam) {
        if (Objects.isNull(requestParam)) {
            throw new GXBusinessException("Request param must not be null");
        }
        final Object obj = requestParam.getObj(GXBuilderConstant.SEARCH_CONDITION_NAME);
        if (null == obj) {
            return requestParam;
        }

        if (CharSequenceUtil.isNotEmpty(key) && GXDBStringUtils.check(key)) {
            String message = CharSequenceUtil.format("Potential SQL injection detected: {} (source: addSearchCondition.key)", key);
            LOG.error(message);
            throw new GXSqlInjectionException(message);
        }

        if (value instanceof String && GXDBStringUtils.check((String) value)) {
            String message = CharSequenceUtil.format("Potential SQL injection detected: {} (source: addSearchCondition.value)", value);
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
        if (Objects.isNull(requestParam)) {
            throw new GXBusinessException("Request param must not be null");
        }
        final Object obj = requestParam.getObj(GXBuilderConstant.SEARCH_CONDITION_NAME);
        if (null == obj) {
            return requestParam;
        }

        if (Objects.nonNull(sourceData)) {
            sourceData.forEach((k, v) -> {
                if (CharSequenceUtil.isNotEmpty(k) && GXDBStringUtils.check(k)) {
                    String message = CharSequenceUtil.format("Potential SQL injection detected: {} (source: addSearchCondition.sourceData.key)", k);
                    LOG.error(message);
                    throw new GXSqlInjectionException(message);
                }

                if (v instanceof String && GXDBStringUtils.check((String) v)) {
                    String message = CharSequenceUtil.format("Potential SQL injection detected: {} (source: addSearchCondition.sourceData.value)", v);
                    LOG.error(message);
                    throw new GXSqlInjectionException(message);
                }
            });
        }

        final Dict data = Convert.convert(Dict.class, obj);
        if (Objects.nonNull(sourceData)) {
            data.putAll(sourceData);
        }
        if (returnRequestParam) {
            requestParam.put(GXBuilderConstant.SEARCH_CONDITION_NAME, data);
            return requestParam;
        }
        return data;
    }

    public static String getTableName(Class<?> clazz) {
        if (Objects.isNull(clazz)) {
            throw new GXBusinessException("Entity class must not be null");
        }
        TableInfo tableInfo = TableInfoHelper.getTableInfo(clazz);
        if (Objects.isNull(tableInfo)) {
            throw new GXBusinessException(CharSequenceUtil.format("Table metadata not found: {}", clazz.getName()));
        }
        return tableInfo.getTableName();
    }

    public static <R> GXPaginationResDto<R> convertPageToPaginationResDto(IPage<R> page, List<R> records) {
        if (Objects.isNull(page)) {
            throw new GXBusinessException("Page must not be null");
        }
        long pages = page.getPages();
        long currentPage = page.getCurrent();
        long pageSize = page.getSize();
        long totalCount = page.getTotal();
        return new GXPaginationResDto<>(records, totalCount, pages, pageSize, currentPage);
    }

    public static <R> GXPaginationResDto<R> convertPageToPaginationResDto(IPage<R> page) {
        if (Objects.isNull(page)) {
            throw new GXBusinessException("Page must not be null");
        }
        long pages = page.getPages();
        long currentPage = page.getCurrent();
        long pageSize = page.getSize();
        long totalCount = page.getTotal();
        return new GXPaginationResDto<>(page.getRecords(), totalCount, pages, pageSize, currentPage);
    }

    public static String generateJSONSearchExpression(String searchField, String searchExpression, Set<Object> searchValue) {
        if (CharSequenceUtil.isEmpty(searchField)) {
            throw new GXBusinessException("Search field must not be empty");
        }
        if (CollUtil.isEmpty(searchValue)) {
            throw new GXBusinessException("Search value must not be empty");
        }

        if (GXDBStringUtils.check(searchField)) {
            String message = CharSequenceUtil.format("Potential SQL injection detected: {} (source: generateJSONSearchExpression.searchField)", searchField);
            LOG.error(message);
            throw new GXSqlInjectionException(message);
        }

        String safeSearchField = GXDBStringUtils.escapeJsonPath(searchField);

        if (CharSequenceUtil.isEmpty(searchExpression)) {
            searchExpression = "$";
        } else {
            if (GXDBStringUtils.check(searchExpression)) {
                String message = CharSequenceUtil.format("Potential SQL injection detected: {} (source: generateJSONSearchExpression.searchExpression)", searchExpression);
                LOG.error(message);
                throw new GXSqlInjectionException(message);
            }
            searchExpression = CharSequenceUtil.format("$.{}", GXDBStringUtils.escapeJsonPath(searchExpression));
        }

        String expressionTemplate = GXBuilderConstant.JSON_SEARCH_EXPRESSION_TEMPLATE;
        String searchStr = searchValue.stream().map(o -> {
            if (Objects.isNull(o)) {
                throw new GXBusinessException("Search value item must not be null");
            }
            if (o instanceof Number) {
                return CharSequenceUtil.format("{}", o.toString());
            }
            String strValue = o.toString();
            if (GXDBStringUtils.check(strValue)) {
                String message = CharSequenceUtil.format("Potential SQL injection detected: {} (source: generateJSONSearchExpression.searchValue)", strValue);
                LOG.error(message);
                throw new GXSqlInjectionException(message);
            }
            return CharSequenceUtil.format("\"{}\"", GXDBStringUtils.escapeJson(strValue));
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
        if (CollUtil.isEmpty(condition)) {
            return updateWrapper;
        }
        Dict methodNameDict = Dict.create()
                .set("=", "eq")
                .set("!=", "ne")
                .set("in", "in")
                .set("not in", "notIn")
                .set(">=", "ge")
                .set(">", "gt")
                .set("<=", "le")
                .set("<", "lt");

        condition.stream().filter(Objects::nonNull).forEach(c -> {
            if (!GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass())) {
                if (c instanceof GXConditionRaw) {
                    updateWrapper.apply(c.toSegment().sql());
                    return;
                }
                String column = c.getFieldExpression();
                Object value = c.getFieldOriginalValue();

                if (GXConditionIsNULL.class.isAssignableFrom(c.getClass())) {
                    updateWrapper.isNull(CharSequenceUtil.toUnderlineCase(column));
                    return;
                }
                if (GXConditionIsNotNULL.class.isAssignableFrom(c.getClass())) {
                    updateWrapper.isNotNull(CharSequenceUtil.toUnderlineCase(column));
                    return;
                }

                if (Objects.isNull(value)) {
                    throw new GXBusinessException(CharSequenceUtil.format("Condition value must not be null: {}", column));
                }

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
            if (CharSequenceUtil.isNotEmpty(column) && GXDBStringUtils.check(column)) {
                String message = CharSequenceUtil.format("Potential SQL injection detected: {} (source: assemblySqlObjectCondition.column)", column);
                LOG.error(message);
                throw new GXSqlInjectionException(message);
            }

            String safeColumn = safeColumnName(column);

            if (Objects.isNull(val)) {
                sql.WHERE(CharSequenceUtil.format("{} IS NULL", safeColumn));
                return;
            }

            final String value = Convert.toStr(val);

            if (CharSequenceUtil.isNotEmpty(value) && GXDBStringUtils.check(value)) {
                String message = CharSequenceUtil.format("Potential SQL injection detected: {} (source: assemblySqlObjectCondition.value)", value);
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
                safeValue = GXDBStringUtils.escapeSql(value);
            }

            sql.WHERE(CharSequenceUtil.format(template, safeColumn, safeValue));
        });
    }

    public static void checkSQLInjection(String input, String source, boolean isUserInput) {
        if (CharSequenceUtil.isEmpty(input)) {
            LOG.debug("Input is empty, skip SQL injection check. source={}", source);
            return;
        }

        if (input.length() > MAX_INPUT_LENGTH) {
            String message = CharSequenceUtil.format("Input length exceeds max limit ({}): {} (source: {})", MAX_INPUT_LENGTH, input, source);
            LOG.error(message);
            throw new IllegalArgumentException(message);
        }

        if (!isUserInput && input.length() <= 2048 && LEGITIMATE_SUBQUERY_PATTERN.matcher(input).matches()) {
            LOG.debug("Input is a permitted subquery expression, skip strict SQL injection check. source={}", source);
            return;
        }

        if (ReUtil.contains(SQL_INJECTION_PATTERN, input)) {
            String message = CharSequenceUtil.format("Potential SQL injection detected by primary check: {} (source: {})", input, source);
            LOG.error(message);
            throw new GXSqlInjectionException(message);
        }

        try {
            if (GXDBStringUtils.check(input)) {
                String message = CharSequenceUtil.format("Potential SQL injection detected by fallback check: {} (source: {})", input, source);
                LOG.error(message);
                throw new GXSqlInjectionException(message);
            }
        } catch (GXSqlInjectionException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("SQL injection fallback check failed: {}", e.getMessage());
        }
    }

    public static String safeTableName(String tableName) {
        if (CharSequenceUtil.isEmpty(tableName)) {
            throw new GXBusinessException("Table name must not be empty");
        }

        checkSQLInjection(tableName, "tableName", false);

        if (CharSequenceUtil.containsAny(tableName, ";", "'", "\"", "\\", "/*", "--", "#")) {
            throw new GXSqlInjectionException("Table name contains unsupported characters: " + tableName);
        }
        if (!SAFE_QUALIFIED_IDENTIFIER_PATTERN.matcher(tableName).matches()) {
            throw new GXSqlInjectionException("Table name is not a safe identifier: " + tableName);
        }

        return tableName;
    }

    public static String safeTableAlias(String tableAlias) {
        if (CharSequenceUtil.isEmpty(tableAlias)) {
            return null;
        }

        checkSQLInjection(tableAlias, "tableAlias", false);

        if (CharSequenceUtil.containsAny(tableAlias, ";", "'", "\"", "\\", "/*", "--", "#")) {
            throw new GXSqlInjectionException("Table alias contains unsupported characters: " + tableAlias);
        }
        if (!SAFE_ALIAS_PATTERN.matcher(tableAlias).matches()) {
            throw new GXSqlInjectionException("Table alias is not a safe identifier: " + tableAlias);
        }

        return tableAlias;
    }

    public static String safeColumnName(String columnName) {
        if (CharSequenceUtil.isEmpty(columnName)) {
            throw new GXBusinessException("Column name must not be empty");
        }

        boolean isSqlFunction = isSqlFunctionCall(columnName);

        if (!isSqlFunction) {
            checkSQLInjection(columnName, "columnName", true);

            if (CharSequenceUtil.containsAny(columnName, ";", "'", "\"", "\\", "/*", "--", "#")) {
                throw new GXSqlInjectionException("Column name contains unsupported characters: " + columnName);
            }
            if (!SAFE_QUALIFIED_IDENTIFIER_PATTERN.matcher(columnName).matches()) {
                throw new GXSqlInjectionException("Column name is not a safe identifier: " + columnName);
            }
        } else {
            if (CharSequenceUtil.containsAny(columnName, ";", "--", "#", "/*")) {
                LOG.error("SQL function call contains suspicious characters: {}", columnName);
                throw new GXSqlInjectionException("SQL function call contains suspicious characters: " + columnName);
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
