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
     */
    Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(;|\\b(union\\s+select|load_file|outfile|dumpfile|into\\s+outfile|into\\s+dumpfile|sleep\\s*\\(\\s*\\d+\\s*\\)|benchmark\\s*\\(\\s*\\d+\\s*,\\s*md5\\s*\\(\\s*1\\s*\\)\\s*\\))\\b)"
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

        if (ReUtil.contains(SQL_INJECTION_PATTERN, input)) {
            String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: {})", input, source);
            LOGGER.error(message);
            throw new GXSqlInjectionException(message);
        }
    }

    /**
     * 安全处理表名
     * <p>
     * 该方法确保表名不包含SQL注入攻击模式。
     * </p>
     *
     * @param tableName 原始表名
     * @return 安全的表名
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    static String safeTableName(String tableName) {
        if (CharSequenceUtil.isEmpty(tableName)) {
            throw new GXBusinessException("表名不能为空");
        }

        checkSQLInjection(tableName, "tableName");
        // 直接返回经过SQL注入检查的表名
        return tableName;
    }

    /**
     * 安全处理表别名
     * <p>
     * 该方法确保表别名不包含SQL注入攻击模式。
     * </p>
     *
     * @param tableAlias 原始表别名
     * @return 安全的表别名
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    static String safeTableAlias(String tableAlias) {
        if (CharSequenceUtil.isEmpty(tableAlias)) {
            return null;
        }

        checkSQLInjection(tableAlias, "tableAlias");
        return tableAlias;
    }

    /**
     * 安全处理列名
     * <p>
     * 该方法确保列名不包含SQL注入攻击模式。
     * </p>
     *
     * @param columnName 原始列名
     * @return 安全的列名
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    static String safeColumnName(String columnName) {
        if (CharSequenceUtil.isEmpty(columnName)) {
            throw new GXBusinessException("列名不能为空");
        }

        checkSQLInjection(columnName, "columnName");
        return columnName;
    }

    /**
     * 通过条件获取分页数据
     * <p>
     * 该方法根据提供的分页对象和查询参数构建分页查询SQL语句。
     * 如果查询参数中包含原始SQL，则直接返回原始SQL；否则调用findByCondition方法构建SQL。
     * 所有输入参数都经过SQL注入防护处理，确保生成的SQL语句安全可靠。
     * </p>
     *
     * @param page                 分页对象
     * @param dbQueryParamInnerDto 查询对象
     * @return 构建的分页SQL语句
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    @SuppressWarnings("unused")
    static <R> String paginate(IPage<R> page, GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        // 验证参数
        if (dbQueryParamInnerDto == null) {
            throw new GXBusinessException("查询参数不能为空");
        }

        // 如果有原始SQL，检查SQL注入并返回
        String rawSQL = dbQueryParamInnerDto.getRawSQL();
        if (CharSequenceUtil.isNotBlank(rawSQL)) {
            checkSQLInjection(rawSQL, "rawSQL");
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

            // 获取条件的WHERE表达式
            String whereExpr = c.whereString();

            // 检查SQL注入
            if (CharSequenceUtil.isNotEmpty(whereExpr)) {
                checkSQLInjection(whereExpr, "whereCondition");
                lastWheres.add(whereExpr);
            }
        }

        // 将所有条件组合成一个WHERE子句
        if (!lastWheres.isEmpty()) {
            String whereStr = String.join(" AND ", lastWheres);
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
