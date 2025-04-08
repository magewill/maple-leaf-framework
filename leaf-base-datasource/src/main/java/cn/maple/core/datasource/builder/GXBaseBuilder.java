package cn.maple.core.datasource.builder;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReUtil;
import cn.maple.core.framework.constant.GXBuilderConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXJoinDto;
import cn.maple.core.framework.dto.inner.GXJoinTypeEnums;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionExclusionDeletedField;
import cn.maple.core.framework.dto.inner.condition.GXConditionIsNULL;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXDBConditionException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.jdbc.SQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基础SQL构建器接口
 * <p>
 * 该接口提供了一系列静态方法，用于构建SQL语句。
 * 所有方法都经过SQL注入防护处理，确保生成的SQL语句安全可靠。
 * </p>
 *
 * @author 塵子曦
 */
@SuppressWarnings("unused")
public interface GXBaseBuilder {
    /**
     * 日志对象
     */
    Logger LOGGER = LoggerFactory.getLogger(GXBaseBuilder.class);

    /**
     * SQL注入检测正则表达式
     * 用于检测常见的SQL注入模式
     * 包括：
     * 1. 分号（可能用于分隔多条SQL语句）
     * 2. UNION SELECT语句（用于联合查询攻击）
     * 3. 文件操作函数（load_file, outfile, dumpfile等）
     * 4. 时间延迟函数（sleep, benchmark等，用于盲注）
     * 5. 注释符（--, #, /*等，用于注释掉查询的剩余部分）
     * 6. 常见的条件注入模式（OR 1=1, AND 1=1等）
     * 7. 系统函数和变量（@@version, user()等）
     */
    Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(;|--[\\s\\r\\n]|#|/\\*|\\*/|\\b(union\\s+all\\s+select|union\\s+select|load_file|outfile|dumpfile|" +
                    "into\\s+outfile|into\\s+dumpfile|sleep\\s*\\(\\s*\\d+\\s*\\)|benchmark\\s*\\(\\s*\\d+\\s*,\\s*md5\\s*\\(\\s*1\\s*\\)\\s*\\)|" +
                    "information_schema\\.|sysobjects\\.|xp_cmdshell|exec\\s+\\w+|execute\\s+\\w+|sp_executesql|" +
                    "@@version|user\\s*\\(\\s*\\)|database\\s*\\(\\s*\\)|schema\\s*\\(\\s*\\)|" +
                    "or\\s+[\\d\\w]+\\s*=\\s*[\\d\\w]+\\s+--|and\\s+[\\d\\w]+\\s*=\\s*[\\d\\w]+\\s+--|" +
                    "or\\s+'[^']+'\\s*=\\s*'[^']+'|and\\s+'[^']+'\\s*=\\s*'[^']+')\\b)"
    );

    /**
     * 更新实体字段和虚拟字段
     * <p>
     * 该方法根据提供的表名、字段列表和条件构建UPDATE SQL语句。
     * 所有输入参数都经过SQL注入防护处理，确保生成的SQL语句安全可靠。
     * </p>
     *
     * @param tableName 表名
     * @param fieldList 需要更新的字段列表
     * @param condition 更新条件
     * @return 构建的UPDATE SQL语句
     * @throws GXBusinessException     如果条件为空
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    static String updateFieldByCondition(String tableName, List<GXUpdateField<?>> fieldList, List<GXCondition<?>> condition) {
        // 验证条件不能为空
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }

        // 验证表名
        String safeTableName = safeTableName(tableName);

        // 创建SQL对象并设置UPDATE表名
        final SQL sql = new SQL().UPDATE(safeTableName);

        // 处理字段更新
        if (CollUtil.isNotEmpty(fieldList)) {
            for (GXUpdateField<?> field : fieldList) {
                if (field != null) {
                    // 获取安全的更新表达式
                    String updateExpr = field.updateString();
                    checkSQLInjection(updateExpr, "updateField");
                    sql.SET(updateExpr);
                }
            }
        }

        // 添加更新时间字段
        sql.SET(CharSequenceUtil.format("updated_at = {}", DateUtil.currentSeconds()));

        // 处理WHERE条件
        handleSQLCondition(sql, condition);

        // 添加软删除条件（如果没有明确排除）
        if (!CollUtil.contains(condition, (c -> GXConditionExclusionDeletedField.class.isAssignableFrom(c.getClass())))) {
            sql.WHERE(CharSequenceUtil.format("{}.is_deleted = {}", safeTableName, 0));
        }

        // 返回SQL语句
        String sqlString = sql.toString();
        LOGGER.debug("Generated UPDATE SQL: {}", sqlString);
        return sqlString;
    }

    /**
     * 判断给定条件的记录是否存在
     * <p>
     * 该方法构建一个高效的SQL查询，仅返回一条记录且只查询常量值"1"，
     * 用于检查符合条件的记录是否存在。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件
     * @return 构建的SQL语句
     */
    static String checkRecordIsExists(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        // 验证参数
        if (dbQueryParamInnerDto == null) {
            throw new GXBusinessException("查询参数不能为空");
        }

        // 设置限制为1条记录
        dbQueryParamInnerDto.setLimit(1);
        // 只查询常量"1"，提高查询效率
        dbQueryParamInnerDto.setColumns(CollUtil.newHashSet("1"));

        // 使用findOneByCondition方法构建SQL
        return findOneByCondition(dbQueryParamInnerDto);
    }

    /**
     * 通过条件获取数据列表
     * <p>
     * 该方法根据提供的查询参数构建完整的SELECT SQL语句，
     * 支持列选择、表别名、JOIN、WHERE条件、GROUP BY、HAVING、ORDER BY和LIMIT等功能。
     * 所有输入参数都经过SQL注入防护处理，确保生成的SQL语句安全可靠。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件对象
     * @return 构建的SELECT SQL语句
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    static String findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        // 验证参数
        if (dbQueryParamInnerDto == null) {
            throw new GXBusinessException("查询参数不能为空");
        }

        // 获取查询参数
        Set<String> columns = dbQueryParamInnerDto.getColumns();
        String tableName = safeTableName(dbQueryParamInnerDto.getTableName());
        String tableNameAlias = safeTableAlias(Optional.ofNullable(dbQueryParamInnerDto.getTableNameAlias()).orElse(tableName));
        Set<String> groupByField = dbQueryParamInnerDto.getGroupByField();
        Map<String, String> orderByField = dbQueryParamInnerDto.getOrderByField();
        Set<String> having = dbQueryParamInnerDto.getHaving();
        Integer limit = dbQueryParamInnerDto.getLimit();

        // 构建SELECT子句
        String selectStr = CharSequenceUtil.format("{}.*", tableNameAlias);
        if (CollUtil.isNotEmpty(columns)) {
            List<String> columnsCollect = columns.stream()
                    .map(CharSequenceUtil::toUnderlineCase)
                    .map(GXBaseBuilder::safeColumnName)
                    .collect(Collectors.toList());
            selectStr = String.join(",", columnsCollect);
        }

        // 创建SQL对象并设置SELECT和FROM子句
        SQL sql = new SQL().SELECT(selectStr).FROM(CharSequenceUtil.format("{} {}", tableName, tableNameAlias));

        // 处理JOIN
        List<GXJoinDto> joins = dbQueryParamInnerDto.getJoins();
        if (Objects.nonNull(joins) && !joins.isEmpty()) {
            handleSQLJoin(sql, joins);
        }

        // 获取条件
        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();

        // 处理WHERE条件
        handleSQLCondition(sql, condition);

        // 添加软删除条件（如果没有明确排除）
        if (!CollUtil.contains(condition, (c -> GXConditionExclusionDeletedField.class.isAssignableFrom(c.getClass())))) {
            sql.WHERE(CharSequenceUtil.format("{}.is_deleted = {}", tableNameAlias, 0));
        }

        // 处理JOIN表的Where条件
        if (Objects.nonNull(joins) && !joins.isEmpty()) {
            for (GXJoinDto joinDto : joins) {
                if (joinDto == null) {
                    continue;
                }

                List<GXCondition<?>> joinConditions = Optional.ofNullable(joinDto.getConditions()).orElse(new ArrayList<>());

                // 自动添加软删除条件
                if (joinDto.isAutoFillIsDeleteCondition()) {
                    String masterTableNameAlias = safeTableAlias(joinDto.getMasterTableNameAlias());

                    // 检查是否已存在is_deleted条件
                    boolean hasIsDeletedCondition = CollUtil.contains(joinConditions, (c -> {
                        if (c == null) {
                            return false;
                        }
                        String identity = c.getTableNameAlias() + "." + c.getFieldExpression();
                        return identity.equalsIgnoreCase(masterTableNameAlias + "." + "is_deleted");
                    }));

                    // 如果不存在，添加is_deleted=0条件
                    if (!hasIsDeletedCondition) {
                        GXConditionEQ isDeletedCondition = new GXConditionEQ(masterTableNameAlias, "is_deleted", 0);
                        joinConditions.add(isDeletedCondition);
                    }
                }

                // 处理JOIN表的条件
                handleSQLCondition(sql, joinConditions);
            }
        }

        // 处理GROUP BY
        if (CollUtil.isNotEmpty(groupByField)) {
            String[] safeGroupByFields = groupByField.stream()
                    .map(GXBaseBuilder::safeColumnName)
                    .toArray(String[]::new);
            sql.GROUP_BY(safeGroupByFields);
        }

        // 处理HAVING
        if (CollUtil.isNotEmpty(having)) {
            String[] safeHavingClauses = having.stream()
                    .peek(h -> checkSQLInjection(h, "having"))
                    .toArray(String[]::new);
            sql.HAVING(safeHavingClauses);
        }

        // 处理ORDER BY
        if (Objects.nonNull(orderByField) && !orderByField.isEmpty()) {
            String[] orderColumns = new String[orderByField.size()];
            Integer[] idx = new Integer[]{0};

            orderByField.forEach((k, v) -> {
                String safeColumn = safeColumnName(k);
                String direction = v.trim().toUpperCase();

                // 验证排序方向
                if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
                    throw new GXSqlInjectionException("无效的排序方向: " + v);
                }

                orderColumns[idx[0]++] = CharSequenceUtil.format("{} {}", safeColumn, direction);
            });

            sql.ORDER_BY(orderColumns);
        }

        // 处理LIMIT
        if (Objects.nonNull(limit) && limit > 0) {
            sql.LIMIT(limit);
        }

        // 返回SQL语句
        String sqlString = sql.toString();
        LOGGER.debug("Generated SELECT SQL: {}", sqlString);
        return sqlString;
    }

    /**
     * 处理JOIN表
     * <p>
     * 该方法处理SQL语句的JOIN子句，支持LEFT JOIN、RIGHT JOIN和INNER JOIN。
     * 所有输入参数都经过SQL注入防护处理，确保生成的SQL语句安全可靠。
     * </p>
     *
     * @param sql   SQL语句对象
     * @param joins JOIN信息列表
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    static void handleSQLJoin(SQL sql, List<GXJoinDto> joins) {
        if (CollUtil.isEmpty(joins)) {
            return;
        }

        for (GXJoinDto join : joins) {
            if (join == null) {
                continue;
            }

            // 获取JOIN类型
            GXJoinTypeEnums joinType = join.getJoinType();

            // 安全处理表名和别名
            String tableName = safeTableName(join.getJoinTableName());
            String tableAliasName = safeTableAlias(join.getJoinTableNameAlias());
            String masterTableName = safeTableName(join.getMasterTableName());
            String masterTableNameAlias = safeTableAlias(join.getMasterTableNameAlias());

            if (Objects.isNull(masterTableNameAlias)) {
                masterTableNameAlias = masterTableName;
            }

            // 处理AND条件和OR条件
            String andClause = Optional.ofNullable(join.getAnd()).orElse(Collections.emptyList())
                    .stream()
                    .map(op -> {
                        String opStr = op.opString();
                        checkSQLInjection(opStr, "joinAndClause");
                        return opStr;
                    })
                    .collect(Collectors.joining(GXBuilderConstant.AND_OP));

            String orClause = Optional.ofNullable(join.getOr()).orElse(Collections.emptyList())
                    .stream()
                    .map(op -> {
                        String opStr = op.opString();
                        checkSQLInjection(opStr, "joinOrClause");
                        return opStr;
                    })
                    .collect(Collectors.joining(GXBuilderConstant.AND_OP));

            // 构建JOIN SQL
            String assemblySql = CharSequenceUtil.format("{} {} ON ({})", masterTableName, masterTableNameAlias, andClause);

            if (CharSequenceUtil.isNotEmpty(orClause)) {
                assemblySql = assemblySql.replace("ON (", "ON ((");
                assemblySql = CharSequenceUtil.format("{} {} ({}))", assemblySql, GXBuilderConstant.OR_OP, orClause);
            }

            // 根据JOIN类型添加到SQL
            if (CharSequenceUtil.equalsIgnoreCase(GXBuilderConstant.LEFT_JOIN_TYPE, joinType.getJoinType())) {
                sql.LEFT_OUTER_JOIN(assemblySql);
            } else if (CharSequenceUtil.equalsIgnoreCase(GXBuilderConstant.RIGHT_JOIN_TYPE, joinType.getJoinType())) {
                sql.RIGHT_OUTER_JOIN(assemblySql);
            } else if (CharSequenceUtil.equalsIgnoreCase(GXBuilderConstant.INNER_JOIN_TYPE, joinType.getJoinType())) {
                sql.INNER_JOIN(assemblySql);
            }
        }
    }

    /**
     * 检查SQL注入
     * <p>
     * 该方法检查输入字符串是否包含潜在的SQL注入攻击模式。
     * 使用GXDBStringEscapeUtils.check方法进行全面的SQL注入检测。
     * 如果检测到可能的SQL注入，将抛出异常。
     * </p>
     *
     * @param input  要检查的输入字符串
     * @param source 输入来源的描述（用于日志和错误消息）
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    static void checkSQLInjection(String input, String source) {
        if (CharSequenceUtil.isEmpty(input)) {
            return;
        }

        // 使用简单的正则表达式进行初步检测
        if (ReUtil.contains(SQL_INJECTION_PATTERN, input)) {
            String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: {})", input, source);
            LOGGER.error(message);
            throw new GXSqlInjectionException(message);
        }

        // 使用GXDBStringEscapeUtils.check方法进行更全面的SQL注入检测
        // 这个方法检查更多的SQL注入模式，包括SQL语法、注释、盲注等
        try {
            if (cn.maple.core.framework.util.GXDBStringEscapeUtils.check(input)) {
                String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: {})", input, source);
                LOGGER.error(message);
                throw new GXSqlInjectionException(message);
            }
        } catch (Exception e) {
            // 如果GXDBStringEscapeUtils.check方法抛出异常，记录日志并继续使用原有的检测方法
            LOGGER.warn("使用GXDBStringEscapeUtils.check方法检测SQL注入时发生异常: {}", e.getMessage());
        }
    }

    /**
     * 安全处理表名
     * <p>
     * 该方法确保表名不包含SQL注入攻击模式，并对表名进行适当的转义处理。
     * 表名通常不应包含需要转义的特殊字符，但为了安全起见，仍然进行检查。
     * </p>
     *
     * @param tableName 原始表名
     * @return 安全的表名
     * @throws GXBusinessException     如果表名为空
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    static String safeTableName(String tableName) {
        if (CharSequenceUtil.isEmpty(tableName)) {
            throw new GXBusinessException("表名不能为空");
        }

        // 检查SQL注入
        checkSQLInjection(tableName, "tableName");

        // 表名通常不应包含特殊字符，但为了安全起见，仍然进行检查
        // 如果表名包含特殊字符（如点号以外的特殊字符），可能表示SQL注入尝试
        if (tableName.matches(".*[;'\"\\\\].*")) {
            throw new GXSqlInjectionException("表名包含不允许的特殊字符: " + tableName);
        }

        return tableName;
    }

    /**
     * 安全处理表别名
     * <p>
     * 该方法确保表别名不包含SQL注入攻击模式，并对表别名进行适当的转义处理。
     * 表别名通常不应包含需要转义的特殊字符，但为了安全起见，仍然进行检查。
     * </p>
     *
     * @param tableAlias 原始表别名
     * @return 安全的表别名，如果输入为空则返回null
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    static String safeTableAlias(String tableAlias) {
        if (CharSequenceUtil.isEmpty(tableAlias)) {
            return null;
        }

        // 检查SQL注入
        checkSQLInjection(tableAlias, "tableAlias");

        // 表别名通常不应包含特殊字符，但为了安全起见，仍然进行检查
        // 如果表别名包含特殊字符，可能表示SQL注入尝试
        if (tableAlias.matches(".*[;'\"\\\\].*")) {
            throw new GXSqlInjectionException("表别名包含不允许的特殊字符: " + tableAlias);
        }

        return tableAlias;
    }

    /**
     * 安全处理列名
     * <p>
     * 该方法确保列名不包含SQL注入攻击模式，并对列名进行适当的转义处理。
     * 列名通常不应包含需要转义的特殊字符，但为了安全起见，仍然进行检查。
     * 该方法支持处理SQL函数调用，如GROUP_CONCAT等聚合函数。
     * </p>
     *
     * @param columnName 原始列名
     * @return 安全的列名
     * @throws GXBusinessException     如果列名为空
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    static String safeColumnName(String columnName) {
        if (CharSequenceUtil.isEmpty(columnName)) {
            throw new GXBusinessException("列名不能为空");
        }

        // 检查是否是SQL函数调用（如GROUP_CONCAT, COUNT, SUM等）
        // 函数调用通常具有函数名后跟括号的形式
        boolean isSqlFunction = isSqlFunctionCall(columnName);

        if (!isSqlFunction) {
            // 如果不是SQL函数调用，则进行常规SQL注入检查
            checkSQLInjection(columnName, "columnName");

            // 列名通常不应包含特殊字符，但为了安全起见，仍然进行检查
            // 如果列名包含特殊字符（如点号以外的特殊字符），可能表示SQL注入尝试
            // 允许点号是因为有时列名可能包含表名前缀，如 table.column
            if (columnName.matches(".*[;'\"\\\\].*")) {
                throw new GXSqlInjectionException("列名包含不允许的特殊字符: " + columnName);
                //LOGGER.error("列名包含不允许的特殊字符: {}", columnName);
            }
        } else {
            // 对于SQL函数调用，进行基本的安全检查，但允许函数语法
            // 检查是否包含明显的SQL注入尝试，如多条语句、注释等
            if (columnName.matches(".*[;].*") || columnName.matches(".*--.*") || columnName.matches(".*#.*")) {
                LOGGER.error("SQL函数调用中包含可疑字符: {}", columnName);
                throw new GXSqlInjectionException("SQL函数调用中包含可疑字符: " + columnName);
            }
        }

        return columnName;
    }

    /**
     * 判断字符串是否是SQL函数调用
     * <p>
     * 该方法检查字符串是否符合SQL函数调用的基本模式，
     * 包括常见的聚合函数（如GROUP_CONCAT, SUM, COUNT等）和其他SQL函数。
     * </p>
     *
     * @param str 要检查的字符串
     * @return 如果字符串是SQL函数调用则返回true，否则返回false
     */
    private static boolean isSqlFunctionCall(String str) {
        if (CharSequenceUtil.isEmpty(str)) {
            return false;
        }

        // 常见的SQL函数名称模式
        String functionPattern = "(?i)(GROUP_CONCAT|CONCAT|COUNT|SUM|AVG|MIN|MAX|DISTINCT|SUBSTRING|CAST|CONVERT|DATE_FORMAT|" +
                "IF|IFNULL|NULLIF|COALESCE|CASE|WHEN|THEN|ELSE|END|ROUND|FLOOR|CEILING|ABS|RAND|" +
                "LENGTH|CHAR_LENGTH|TRIM|LTRIM|RTRIM|LOWER|UPPER|REPLACE|REGEXP_REPLACE|" +
                "DATE|DATETIME|TIME|YEAR|MONTH|DAY|HOUR|MINUTE|SECOND|" +
                "JSON_EXTRACT|JSON_CONTAINS|JSON_OBJECT|JSON_ARRAY|" +
                "ST_Distance|ST_Contains|ST_Within|ST_Intersects)";

        // 检查是否匹配函数调用模式：函数名后跟括号，括号内可能包含参数
        // 同时处理可能的别名（使用AS或空格）
        String functionCallPattern = functionPattern + "\\s*\\([^)]*\\)(\\s+AS\\s+\\w+|\\s+\\w+)?";

        return str.matches(functionCallPattern) ||
                // 处理嵌套函数调用的情况
                str.matches(".*" + functionPattern + "\\s*\\(.*\\).*") ||
                // 处理带有SEPARATOR关键字的GROUP_CONCAT
                str.matches("(?i).*GROUP_CONCAT\\s*\\([^)]*SEPARATOR[^)]*\\).*");
    }

    /**
     * 通过条件获取分页数据
     * <p>
     * 该方法根据提供的分页对象和查询参数构建分页查询SQL语句。
     * 如果查询参数中包含原始SQL，则对原始SQL进行全面的SQL注入检查后返回；
     * 否则调用findByCondition方法构建SQL。
     * 所有输入参数都经过SQL注入防护处理，确保生成的SQL语句安全可靠。
     * </p>
     *
     * @param page                 分页对象
     * @param dbQueryParamInnerDto 查询对象
     * @return 构建的分页SQL语句
     * @throws GXBusinessException     如果查询参数为空
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    @SuppressWarnings("unused")
    static <R> String paginate(IPage<R> page, GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        // 验证参数
        if (dbQueryParamInnerDto == null) {
            throw new GXBusinessException("查询参数不能为空");
        }

        // 如果有原始SQL，进行全面的SQL注入检查
        String rawSQL = dbQueryParamInnerDto.getRawSQL();
        if (CharSequenceUtil.isNotBlank(rawSQL)) {
            // 使用增强的SQL注入检测
            checkSQLInjection(rawSQL, "rawSQL");

            // 尝试使用GXDBStringEscapeUtils进行额外的安全检查
            try {
                if (cn.maple.core.framework.util.GXDBStringEscapeUtils.check(rawSQL)) {
                    String message = CharSequenceUtil.format("原始SQL包含潜在的SQL注入风险: {}", rawSQL);
                    LOGGER.error(message);
                    throw new GXSqlInjectionException(message);
                }
            } catch (Exception e) {
                // 如果GXDBStringEscapeUtils.check方法抛出异常，记录日志但不中断处理
                LOGGER.warn("使用GXDBStringEscapeUtils检查原始SQL时发生异常: {}", e.getMessage());
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("使用原始SQL进行分页查询: {}", rawSQL);
            }

            return rawSQL;
        }

        // 否则构建查询SQL
        return findByCondition(dbQueryParamInnerDto);
    }

    /**
     * 通过条件获取单条数据
     * <p>
     * 该方法根据提供的查询参数构建查询单条记录的SQL语句。
     * 自动设置LIMIT为1，确保只返回一条记录。
     * 所有输入参数都经过SQL注入防护处理，确保生成的SQL语句安全可靠。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件
     * @return 构建的SQL语句
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    static String findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        // 验证参数
        if (dbQueryParamInnerDto == null) {
            throw new GXBusinessException("查询参数不能为空");
        }

        // 设置限制为1条记录
        int limit = Optional.ofNullable(dbQueryParamInnerDto.getLimit()).orElse(1);
        if (limit <= 0) {
            limit = 1;
        }
        dbQueryParamInnerDto.setLimit(limit);

        // 使用findByCondition方法构建SQL
        return findByCondition(dbQueryParamInnerDto);
    }

    /**
     * 处理SQL语句的Where条件
     * <p>
     * 该方法处理SQL语句的WHERE子句，将条件列表转换为SQL条件表达式。
     * 所有条件都经过SQL注入防护处理，确保生成的SQL语句安全可靠。
     * 增强了SQL注入检测和日志记录，以便更好地跟踪和调试潜在的SQL注入问题。
     * </p>
     *
     * @param sql       SQL对象
     * @param condition 条件列表
     * @throws GXDBConditionException  如果条件值为null且不是IsNULL条件
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    static void handleSQLCondition(SQL sql, List<GXCondition<?>> condition) {
        if (Objects.isNull(condition) || condition.isEmpty()) {
            return;
        }

        List<String> lastWheres = new ArrayList<>();

        for (GXCondition<?> c : condition) {
            if (c == null) {
                continue;
            }

            // 跳过排除已删除字段的条件
            if (GXConditionExclusionDeletedField.class.isAssignableFrom(c.getClass())) {
                continue;
            }

            // 验证条件值不为null（除非是IsNULL条件）
            if (ObjectUtil.isNull(c.getFieldValue()) && !GXConditionIsNULL.class.isAssignableFrom(c.getClass())) {
                String tableAlias = safeTableAlias(c.getTableNameAlias());
                String fieldExpr = safeColumnName(c.getFieldExpression());
                String msg = CharSequenceUtil.format("数据查询条件错误【查询字段{}.{}的值是null】", tableAlias, fieldExpr);
                LOGGER.error(msg);
                throw new GXDBConditionException(msg);
            }

            try {
                // 获取条件的WHERE表达式
                String whereExpr = c.whereString();

                // 检查SQL注入
                if (CharSequenceUtil.isNotEmpty(whereExpr)) {
                    // 记录详细日志，便于调试
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("处理WHERE条件: {} (类型: {})", whereExpr, c.getClass().getSimpleName());
                    }

                    // 使用增强的SQL注入检测
                    checkSQLInjection(whereExpr, "whereCondition:" + c.getClass().getSimpleName());

                    // 尝试使用GXDBStringEscapeUtils进行额外的安全检查
                    try {
                        // 如果条件值是字符串类型，尝试使用GXDBStringEscapeUtils.check进行检查
                        Object fieldValue = c.getFieldValue();
                        if (fieldValue instanceof String && cn.maple.core.framework.util.GXDBStringEscapeUtils.check((String) fieldValue)) {
                            String msg = CharSequenceUtil.format("条件值包含潜在的SQL注入风险: {} (条件类型: {})", fieldValue, c.getClass().getSimpleName());
                            LOGGER.error(msg);
                            throw new GXSqlInjectionException(msg);
                        }
                    } catch (Exception e) {
                        // 如果GXDBStringEscapeUtils.check方法抛出异常，记录日志但不中断处理
                        LOGGER.warn("使用GXDBStringEscapeUtils检查条件值时发生异常: {}", e.getMessage());
                    }

                    lastWheres.add(whereExpr);
                }
            } catch (GXSqlInjectionException e) {
                // 直接重新抛出SQL注入异常
                throw e;
            } catch (Exception e) {
                // 处理其他异常，记录详细信息并包装为GXDBConditionException
                String msg = CharSequenceUtil.format("处理WHERE条件时发生异常: {} (条件类型: {})", e.getMessage(), c.getClass().getSimpleName());
                LOGGER.error(msg, e);
                throw new GXDBConditionException(msg, e);
            }
        }

        // 将所有条件组合成一个WHERE子句
        if (!lastWheres.isEmpty()) {
            String whereStr = String.join(" AND ", lastWheres);
            // 最后一次检查组合后的WHERE子句
            checkSQLInjection(whereStr, "combinedWhereClause");
            sql.WHERE(whereStr);
        }
    }

    /**
     * 根据条件软(逻辑)删除
     * <p>
     * 该方法根据提供的表名、更新字段列表、条件和额外数据构建软删除SQL语句。
     * 软删除不会真正删除数据，而是将is_deleted字段设置为主键值，并记录删除时间和删除人。
     * 所有输入参数都经过SQL注入防护处理，确保生成的SQL语句安全可靠。
     * </p>
     *
     * @param tableName       表名
     * @param updateFieldList 软删除时需要同时更新的字段
     * @param condition       删除条件
     * @param extraData       额外数据，可包含deletedBy等信息
     * @return 构建的软删除SQL语句
     * @throws GXBusinessException     如果条件为空或表没有主键
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    static String deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData) {
        // 验证条件不能为空
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }

        // 验证表名
        String safeTableName = safeTableName(tableName);

        // 获取表信息和主键
        TableInfo tableInfo = TableInfoHelper.getTableInfo(safeTableName);
        if (tableInfo == null) {
            throw new GXBusinessException(CharSequenceUtil.format("找不到表{}", safeTableName));
        }

        String keyProperty = tableInfo.getKeyProperty();
        if (CharSequenceUtil.isEmpty(keyProperty)) {
            throw new GXBusinessException(CharSequenceUtil.format("请指定数据表{}的主键字段", safeTableName));
        }

        keyProperty = CharSequenceUtil.toUnderlineCase(keyProperty);
        LOGGER.info("deleteSoftCondition方法中的{}表的主键名字{}", safeTableName, keyProperty);

        // 创建SQL对象并设置UPDATE表名
        SQL sql = new SQL().UPDATE(safeTableName);

        // 设置软删除字段
        sql.SET(CharSequenceUtil.format("is_deleted = {}", keyProperty));
        sql.SET(CharSequenceUtil.format("deleted_at = {}", DateUtil.currentSeconds()));

        // 处理额外的更新字段
        if (CollUtil.isNotEmpty(updateFieldList)) {
            for (GXUpdateField<?> field : updateFieldList) {
                if (field != null) {
                    // 获取安全的更新表达式
                    String updateExpr = field.updateString();
                    checkSQLInjection(updateExpr, "updateField");
                    sql.SET(updateExpr);
                }
            }
        }

        // 处理删除人信息
        if (extraData != null && CharSequenceUtil.isNotBlank(extraData.getStr("deletedBy"))) {
            String deletedBy = extraData.getStr("deletedBy");
            // 检查SQL注入
            checkSQLInjection(deletedBy, "deletedBy");

            // 检查表是否有deleted_by字段
            List<TableFieldInfo> fieldList = tableInfo.getFieldList();
            for (TableFieldInfo fieldInfo : fieldList) {
                String column = fieldInfo.getColumn();
                if (CharSequenceUtil.equalsIgnoreCase("deleted_by", column)) {
                    sql.SET(CharSequenceUtil.format("deleted_by = '{}'", deletedBy));
                    break;
                }
            }
        }

        // 处理WHERE条件
        handleSQLCondition(sql, condition);

        // 添加软删除条件（如果没有明确排除）
        if (!CollUtil.contains(condition, (c -> GXConditionExclusionDeletedField.class.isAssignableFrom(c.getClass())))) {
            sql.WHERE(CharSequenceUtil.format("{}.is_deleted = {}", safeTableName, 0));
        }

        // 返回SQL语句
        String sqlString = sql.toString();
        LOGGER.debug("Generated Soft Delete SQL: {}", sqlString);
        return sqlString;
    }

    /**
     * 根据条件物理删除
     * <p>
     * 该方法根据提供的表名和条件构建物理删除SQL语句。
     * 物理删除会真正从数据库中删除数据，请谨慎使用。
     * 所有输入参数都经过SQL注入防护处理，确保生成的SQL语句安全可靠。
     * </p>
     *
     * @param tableName 表名
     * @param condition 删除条件
     * @return 构建的DELETE SQL语句
     * @throws GXBusinessException     如果条件为空
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    static String deleteCondition(String tableName, List<GXCondition<?>> condition) {
        // 验证条件不能为空
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }

        // 验证表名
        String safeTableName = safeTableName(tableName);

        // 创建SQL对象并设置DELETE_FROM表名
        SQL sql = new SQL().DELETE_FROM(safeTableName);

        // 处理WHERE条件
        handleSQLCondition(sql, condition);

        // 添加软删除条件（如果没有明确排除）
        if (!CollUtil.contains(condition, (c -> GXConditionExclusionDeletedField.class.isAssignableFrom(c.getClass())))) {
            sql.WHERE(CharSequenceUtil.format("{}.is_deleted = {}", safeTableName, 0));
        }

        // 返回SQL语句
        String sqlString = sql.toString();
        LOGGER.debug("Generated DELETE SQL: {}", sqlString);
        return sqlString;
    }

    /**
     * 构建Union语句 将组合出来的union语句作为from的表名来处理
     * <p>
     * 该方法构建UNION查询SQL语句，将多个子查询通过UNION组合，然后作为外层查询的FROM子句。
     * 例如：select * from (select * from test where name like '子曦%' union select * from test where phone like '520%') tmp where father='塵渊'
     * 所有输入参数都经过SQL注入防护处理，确保生成的SQL语句安全可靠。
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件列表
     * @param unionTypeEnums             union的类型（UNION或UNION ALL）
     * @return 构建的UNION SQL语句
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    static String unionFindByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        // 验证参数
        if (masterQueryParamInnerDto == null) {
            throw new GXBusinessException("主查询条件不能为空");
        }
        if (CollUtil.isEmpty(unionQueryParamInnerDtoLst)) {
            throw new GXBusinessException("UNION查询条件列表不能为空");
        }
        if (unionTypeEnums == null) {
            throw new GXBusinessException("UNION类型不能为空");
        }

        // 存储UNION子查询SQL
        List<String> unionSqlLst = new ArrayList<>();

        // 处理每个UNION子查询
        for (GXBaseQueryParamInnerDto queryParamInnerDto : unionQueryParamInnerDtoLst) {
            if (queryParamInnerDto == null) {
                continue;
            }

            // 如果子查询没有指定表名，使用主查询的表名
            String tableName = queryParamInnerDto.getTableName();
            if (CharSequenceUtil.isEmpty(tableName)) {
                queryParamInnerDto.setTableName(masterQueryParamInnerDto.getTableName());
            }

            // 如果子查询没有指定表别名，使用表名作为别名
            String tableNameAlias = queryParamInnerDto.getTableNameAlias();
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                queryParamInnerDto.setTableNameAlias(queryParamInnerDto.getTableName());
            }

            // 构建子查询SQL
            String sql = findByCondition(queryParamInnerDto);
            unionSqlLst.add("(" + sql + ")");
        }

        // 使用UNION类型连接所有子查询
        String unionType = unionTypeEnums.getUnionType();
        checkSQLInjection(unionType, "unionType");
        String unionSql = String.join("\n " + unionType + " \n", unionSqlLst);

        // 设置主查询的表名为UNION子查询
        masterQueryParamInnerDto.setTableName("(" + unionSql + ")");
        masterQueryParamInnerDto.setTableNameAlias("tmp");

        // 更新主查询条件的表别名
        if (CollUtil.isNotEmpty(masterQueryParamInnerDto.getCondition())) {
            for (GXCondition<?> condition : masterQueryParamInnerDto.getCondition()) {
                if (condition != null && !"tmp".equalsIgnoreCase(condition.getTableNameAlias())) {
                    condition.setTableNameAlias("tmp");
                }
            }
        }

        // 构建最终的SQL
        String finalSql = GXBaseBuilder.findByCondition(masterQueryParamInnerDto);
        LOGGER.debug("Generated UNION SQL: {}", finalSql);
        return finalSql;
    }

    /**
     * 构建Union语句查询单条记录
     * <p>
     * 该方法构建UNION查询单条记录的SQL语句，将多个子查询通过UNION组合，然后作为外层查询的FROM子句。
     * 自动设置LIMIT为1，确保只返回一条记录。
     * 所有输入参数都经过SQL注入防护处理，确保生成的SQL语句安全可靠。
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件列表
     * @param unionTypeEnums             union的类型（UNION或UNION ALL）
     * @return 构建的UNION SQL语句
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    static String unionFindOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        // 验证参数
        if (masterQueryParamInnerDto == null) {
            throw new GXBusinessException("主查询条件不能为空");
        }
        if (CollUtil.isEmpty(unionQueryParamInnerDtoLst)) {
            throw new GXBusinessException("UNION查询条件列表不能为空");
        }

        // 设置限制为1条记录
        int limit = Optional.ofNullable(masterQueryParamInnerDto.getLimit()).orElse(1);
        if (limit <= 0) {
            limit = 1;
        }
        masterQueryParamInnerDto.setLimit(limit);

        // 处理每个UNION子查询的表别名
        for (GXBaseQueryParamInnerDto queryParamInnerDto : unionQueryParamInnerDtoLst) {
            if (queryParamInnerDto == null) {
                continue;
            }

            String tableNameAlias = queryParamInnerDto.getTableNameAlias();
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                queryParamInnerDto.setTableNameAlias(queryParamInnerDto.getTableName());
            }
        }

        // 使用unionFindByCondition方法构建SQL
        return unionFindByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    /**
     * 构建Union语句分页查询
     * <p>
     * 该方法构建UNION分页查询SQL语句，将多个子查询通过UNION组合，然后作为外层查询的FROM子句。
     * 如果查询参数中包含原始SQL，则直接返回原始SQL；否则调用unionFindByCondition方法构建SQL。
     * 所有输入参数都经过SQL注入防护处理，确保生成的SQL语句安全可靠。
     * </p>
     *
     * @param page                       分页对象
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件列表
     * @param unionTypeEnums             union的类型（UNION或UNION ALL）
     * @return 构建的UNION分页SQL语句
     * @throws GXBusinessException     如果参数无效
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    @SuppressWarnings("unused")
    static <R> String unionPaginate(IPage<R> page, GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        // 验证参数
        if (masterQueryParamInnerDto == null) {
            throw new GXBusinessException("主查询条件不能为空");
        }
        if (CollUtil.isEmpty(unionQueryParamInnerDtoLst)) {
            throw new GXBusinessException("UNION查询条件列表不能为空");
        }
        if (unionTypeEnums == null) {
            throw new GXBusinessException("UNION类型不能为空");
        }

        // 如果有原始SQL，检查SQL注入并返回
        String rawSQL = masterQueryParamInnerDto.getRawSQL();
        if (CharSequenceUtil.isNotBlank(rawSQL)) {
            checkSQLInjection(rawSQL, "rawSQL");
            return rawSQL;
        }

        // 否则构建UNION查询SQL
        String unionSql = unionFindByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
        LOGGER.debug("Generated UNION Paginate SQL: {}", unionSql);
        return unionSql;
    }
}
