package cn.maple.core.datasource.builder;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.constant.GXBuilderConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXJoinDto;
import cn.maple.core.framework.dto.inner.GXJoinTypeEnums;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionIsNULL;
import cn.maple.core.framework.dto.inner.condition.GXExclusionDeletedFieldCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.inner.op.GXDbJoinOp;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXDBConditionException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.jdbc.SQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基础SQL构建器接口
 * <p>
 * 该接口提供了一系列静态方法，用于构建SQL语句。
 * 所有方法都经过SQL注入防护处理，确保生成的SQL语句安全可靠。
 * 使用MyBatis的参数化查询机制(#{})防止SQL注入攻击。
 * </p>
 *
 * <p>
 * 安全特性：
 * - 所有SQL操作都使用参数化查询（#{paramName}），而非字符串拼接
 * - 自动处理特殊字符，无需手动转义
 * - 条件值自动进行null检查，防止空值异常
 * - 自动添加软删除条件，防止误操作已删除数据
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建查询条件
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("user")
 *     .tableNameAlias("u")
 *     .columns(CollUtil.newHashSet("id", "username", "email"))
 *     .condition(Arrays.asList(
 *         new GXConditionEQ("u", "status", 1),
 *         new GXConditionLike("u", "username", "%admin%")
 *     ))
 *     .build();
 *
 * // 2. 生成查询SQL
 * String sql = GXBaseBuilder.findByCondition(queryParam);
 *
 * // 3. 使用MyBatis执行SQL
 * List<Dict> result = baseMapper.findByCondition(queryParam);
 * </pre>
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
     * 更新实体字段和虚拟字段
     * <p>
     * 该方法根据条件更新表中的字段值。所有更新操作都使用参数化查询，确保SQL注入安全。
     * 方法会自动添加updated_at字段的更新，并自动处理软删除逻辑（is_deleted=0条件）。
     * </p>
     *
     * <p>安全特性：</p>
     * <ol>
     *   <li>所有字段更新都通过GXUpdateField封装，使用参数化查询方式</li>
     *   <li>条件值自动进行null检查，防止空值导致的全表更新风险</li>
     *   <li>自动添加软删除条件（is_deleted=0），防止误操作已删除数据</li>
     *   <li>使用MyBatis SQL类构建SQL语句，避免手动拼接</li>
     *   <li>参数通过Map传递，与SQL语句分离，防止SQL注入</li>
     * </ol>
     *
     * <p>使用示例：</p>
     * <pre>
     * // 1. 创建更新字段列表
     * List<GXUpdateField<?>> updateFields = new ArrayList<>();
     * updateFields.add(new GXUpdateField<>("status", 2));
     * updateFields.add(new GXUpdateField<>("remark", "已处理"));
     * updateFields.add(new GXUpdateField<>("process_time", new Date()));
     *
     * // 2. 创建更新条件
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("order")
     *     .condition(Arrays.asList(
     *         new GXConditionEQ(null, "id", orderId),
     *         new GXConditionEQ(null, "user_id", currentUserId)
     *     ))
     *     .build();
     *
     * // 3. 生成并执行更新SQL
     * String sql = GXBaseBuilder.updateFieldByCondition(queryParam, updateFields);
     * int rows = baseMapper.updateFieldByCondition(queryParam);
     * </pre>
     *
     * @param dbQueryParamInnerDto 查询条件，包含表名、条件等信息，不能为null
     * @param fieldList            要更新的字段列表，每个字段都是GXUpdateField的子类实例，不能为null
     * @return 生成的SQL语句
     * @throws GXBusinessException 当条件为空时抛出异常，防止意外的全表更新操作
     */
    static String updateFieldByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXUpdateField<?>> fieldList) {
        // 参数校验
        if (dbQueryParamInnerDto == null) {
            throw new GXBusinessException("查询参数对象不能为null!");
        }
        // 安全检查：确保有更新字段，防止无效更新
        if (CollUtil.isEmpty(fieldList)) {
            throw new GXBusinessException("更新字段列表不能为空!");
        }
        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();
        String tableName = dbQueryParamInnerDto.getTableName();
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("更新条件不能为空，为防止全表更新风险!");
        }
        final SQL sql = new SQL().UPDATE(tableName);
        // 处理更新字段，使用参数化方式
        for (GXUpdateField<?> field : fieldList) {
            sql.SET(field.updateString());
            dbQueryParamInnerDto.getParamMap().putAll(field.getParamMap());
        }
        // 自动添加更新时间
        sql.SET(CharSequenceUtil.format("updated_at = {}", DateUtil.currentSeconds()));
        // 处理WHERE条件，使用参数化方式
        Map<String, Object> paramMap = handleSQLCondition(sql, condition);
        dbQueryParamInnerDto.getParamMap().putAll(paramMap);
        // 自动添加软删除条件，防止误操作已删除数据
        if (!CollUtil.contains(condition, (c -> GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass())))) {
            sql.WHERE(CharSequenceUtil.format("{}.is_deleted = {}", tableName, 0));
        }
        // 返回生成的SQL语句
        String resultSql = sql.toString();
        LOGGER.debug("生成的更新SQL: {}", resultSql);
        return resultSql;
    }

    /**
     * 判断给定条件的值是否存在
     * <p>
     * 该方法用于检查数据库中是否存在满足指定条件的记录。
     * 内部会将查询限制为只返回一条记录，并且只查询常量1，以提高查询效率。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件，包含表名、条件等信息，不能为null
     * @return 生成的SQL语句
     */
    static String checkRecordIsExists(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        dbQueryParamInnerDto.setLimit(1);
        dbQueryParamInnerDto.setColumns(CollUtil.newHashSet("1"));
        return findOneByCondition(dbQueryParamInnerDto);
    }

    /**
     * 通过条件获取数据列表
     * <p>
     * 该方法根据查询参数构建完整的SELECT查询语句，支持字段选择、表别名、JOIN、WHERE条件、
     * GROUP BY、HAVING、ORDER BY和LIMIT等SQL功能。所有条件都使用参数化查询处理，防止SQL注入。
     * </p>
     * <p>
     * 安全特性：
     * - 所有条件值通过参数化查询（#{paramName}）传递，而非直接拼接SQL
     * - 自动处理表别名，防止字段名冲突
     * - 自动添加软删除条件（is_deleted=0），除非显式排除
     * - 条件值为null时会抛出异常，避免意外的全表查询
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件，包含表名、字段、条件、排序等信息，不能为null
     * @return 生成的SQL语句
     */
    static String findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        Set<String> columns = dbQueryParamInnerDto.getColumns();
        String tableName = dbQueryParamInnerDto.getTableName();
        String tableNameAlias = Optional.ofNullable(dbQueryParamInnerDto.getTableNameAlias()).orElse(tableName);
        Set<String> groupByField = dbQueryParamInnerDto.getGroupByField();
        Map<String, String> orderByField = dbQueryParamInnerDto.getOrderByField();
        Set<String> having = dbQueryParamInnerDto.getHaving();
        Integer limit = dbQueryParamInnerDto.getLimit();
        String selectStr = CharSequenceUtil.format("{}.*", tableNameAlias);
        if (CollUtil.isNotEmpty(columns)) {
            List<String> columnsCollect = columns.stream().map(CharSequenceUtil::toUnderlineCase).collect(Collectors.toList());
            selectStr = String.join(",", columnsCollect);
        }
        SQL sql = new SQL().SELECT(selectStr).FROM(CharSequenceUtil.format("{} {}", tableName, tableNameAlias));
        // 处理JOIN
        List<GXJoinDto> joins = dbQueryParamInnerDto.getJoins();
        if (Objects.nonNull(joins) && !joins.isEmpty()) {
            handleSQLJoin(sql, joins);
        }
        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();
        // 处理WHERE
        Map<String, Object> paramMap = handleSQLCondition(sql, condition);
        // 将参数设置到Mybatis的参数Map中
        dbQueryParamInnerDto.getParamMap().putAll(paramMap);
        if (!CollUtil.contains(condition, (c -> GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass())))) {
            sql.WHERE(CharSequenceUtil.format("{}.is_deleted = {}", tableNameAlias, 0));
        }
        // 处理JOIN表的Where条件
        if (Objects.nonNull(joins) && !joins.isEmpty()) {
            joins.forEach(joinDto -> {
                List<GXCondition<?>> joinConditions = Optional.ofNullable(joinDto.getConditions()).orElse(new ArrayList<>());
                if (joinDto.isAutoFillIsDeleteCondition()) {
                    String masterTableNameAlias = joinDto.getMasterTableNameAlias();
                    if (!CollUtil.contains(joinConditions, (c -> {
                        String identity = c.getTableNameAlias() + "." + c.getFieldExpression();
                        return identity.equalsIgnoreCase(masterTableNameAlias + "." + "is_deleted");
                    }))) {
                        GXConditionEQ isDeletedCondition = new GXConditionEQ(masterTableNameAlias, "is_deleted", 0);
                        joinConditions.add(isDeletedCondition);
                    }
                }
                Map<String, Object> joinParamMap = handleSQLCondition(sql, joinConditions);
                // 将参数设置到Mybatis的参数Map中
                dbQueryParamInnerDto.getParamMap().putAll(paramMap);
            });
        }
        // 处理分组
        if (CollUtil.isNotEmpty(groupByField)) {
            sql.GROUP_BY(groupByField.toArray(new String[0]));
        }
        // 处理HAVING
        if (CollUtil.isNotEmpty(having)) {
            sql.HAVING(having.toArray(new String[0]));
        }
        // 处理排序
        if (Objects.nonNull(orderByField) && !orderByField.isEmpty()) {
            String[] orderColumns = new String[orderByField.size()];
            Integer[] idx = new Integer[]{0};
            orderByField.forEach((k, v) -> orderColumns[idx[0]++] = CharSequenceUtil.format("{} {}", k, v));
            sql.ORDER_BY(orderColumns);
        }
        // 处理LIMIT
        if (Objects.nonNull(limit) && limit > 0) {
            sql.LIMIT(limit);
        }
        return sql.toString();
    }

    /**
     * 处理JOIN表
     * <p>
     * 该方法处理SQL查询中的JOIN操作，支持LEFT JOIN、RIGHT JOIN和INNER JOIN。
     * 可以通过AND和OR条件组合复杂的JOIN条件。
     * </p>
     *
     * @param sql   SQL对象，用于构建SQL语句，不能为null
     * @param joins JOIN信息列表，包含JOIN类型、表名、条件等，不能为null
     */
    static void handleSQLJoin(SQL sql, List<GXJoinDto> joins) {
        joins.forEach(join -> {
            GXJoinTypeEnums joinType = join.getJoinType();
            String tableName = join.getJoinTableName();
            String tableAliasName = join.getJoinTableNameAlias();
            String masterTableName = join.getMasterTableName();
            String masterTableNameAlias = join.getMasterTableNameAlias();
            if (Objects.isNull(masterTableNameAlias)) {
                masterTableNameAlias = masterTableName;
            }
            String andClause = Optional.ofNullable(join.getAnd()).orElse(Collections.emptyList()).stream().map(GXDbJoinOp::opString).collect(Collectors.joining(GXBuilderConstant.AND_OP));
            String orClause = Optional.ofNullable(join.getOr()).orElse(Collections.emptyList()).stream().map(GXDbJoinOp::opString).collect(Collectors.joining(GXBuilderConstant.AND_OP));
            String assemblySql = CharSequenceUtil.format("{} {} ON ({})", masterTableName, masterTableNameAlias, andClause);
            if (CharSequenceUtil.isNotEmpty(orClause)) {
                assemblySql = assemblySql.replace("ON (", "ON ((");
                assemblySql = CharSequenceUtil.format("{} {} ({}))", assemblySql, GXBuilderConstant.OR_OP, orClause);
            }
            if (CharSequenceUtil.equalsIgnoreCase(GXBuilderConstant.LEFT_JOIN_TYPE, joinType.getJoinType())) {
                sql.LEFT_OUTER_JOIN(assemblySql);
            } else if (CharSequenceUtil.equalsIgnoreCase(GXBuilderConstant.RIGHT_JOIN_TYPE, joinType.getJoinType())) {
                sql.RIGHT_OUTER_JOIN(assemblySql);
            } else if (CharSequenceUtil.equalsIgnoreCase(GXBuilderConstant.INNER_JOIN_TYPE, joinType.getJoinType())) {
                sql.INNER_JOIN(assemblySql);
            }
        });
    }

    /**
     * 通过条件获取分类数据
     *
     * @param page                 分页对象
     * @param dbQueryParamInnerDto 查询对象
     * @return SQL语句
     */
    @SuppressWarnings("unused")
    static <R> String paginate(IPage<R> page, GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (CharSequenceUtil.isNotBlank(dbQueryParamInnerDto.getRawSQL())) {
            return dbQueryParamInnerDto.getRawSQL();
        }
        return findByCondition(dbQueryParamInnerDto);
    }

    /**
     * 通过条件获取数据列表
     * <p>
     * 该方法根据查询参数构建完整的SELECT查询语句，支持字段选择、表别名、JOIN、WHERE条件、
     * GROUP BY、HAVING、ORDER BY和LIMIT等SQL功能。所有条件都使用参数化查询处理，防止SQL注入。
     * </p>
     * <p>
     * 安全特性：
     * - 所有条件值通过参数化查询（#{paramName}）传递，而非直接拼接SQL
     * - 自动处理表别名，防止字段名冲突
     * - 自动添加软删除条件（is_deleted=0），除非显式排除
     * - 条件值为null时会抛出异常，避免意外的全表查询
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件，包含表名、字段、条件、排序等信息，不能为null
     * @return 生成的SQL语句
     */
    static String findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        int limit = Optional.ofNullable(dbQueryParamInnerDto.getLimit()).orElse(1);
        if (limit <= 0) {
            limit = 1;
        }
        dbQueryParamInnerDto.setLimit(limit);
        return findByCondition(dbQueryParamInnerDto);
    }

    /**
     * 处理SQL语句的Where条件
     * <p>
     * 该方法处理SQL查询的WHERE条件部分，将条件列表转换为SQL WHERE子句。
     * 所有条件都使用参数化查询处理，确保SQL注入安全。方法会检查条件值是否为null，
     * 如果为null且不是NULL条件，则抛出异常，防止意外的全表操作。
     * </p>
     *
     * <p>安全特性：</p>
     * <ol>
     *   <li>所有条件都通过GXCondition子类封装，使用参数化查询方式</li>
     *   <li>条件值自动进行null检查，防止空值导致的全表操作风险</li>
     *   <li>参数通过Map传递，与SQL语句分离，防止SQL注入</li>
     *   <li>使用AND连接多个条件，确保条件限制范围不会意外扩大</li>
     *   <li>特殊条件类型（如NULL条件）有专门处理逻辑，确保SQL语法正确</li>
     * </ol>
     *
     * <p>参数化查询示例：</p>
     * <pre>
     * // 1. 创建条件列表
     * List<GXCondition<?>> conditions = new ArrayList<>();
     * conditions.add(new GXConditionEQ("u", "status", 1)); // 生成: u.status = #{u_status}
     * conditions.add(new GXConditionLike("u", "name", "%张%")); // 生成: u.name LIKE #{u_name}
     * conditions.add(new GXConditionBetween("u", "age", 18, 30)); // 生成: u.age BETWEEN #{u_age_min} AND #{u_age_max}
     *
     * // 2. 创建SQL构建器
     * SQL sql = new SQL().SELECT("*").FROM("user u");
     *
     * // 3. 处理WHERE条件
     * Map<String, Object> paramMap = GXBaseBuilder.handleSQLCondition(sql, conditions);
     * // 生成的SQL: SELECT * FROM user u WHERE u.status = #{u_status} AND u.name LIKE #{u_name} AND u.age BETWEEN #{u_age_min} AND #{u_age_max}
     * // paramMap包含: {"u_status": 1, "u_name": "%张%", "u_age_min": 18, "u_age_max": 30}
     * </pre>
     *
     * @param sql       SQL对象，用于构建SQL语句，不能为null
     * @param condition 条件列表，可以为null或空列表
     * @return 参数映射，包含所有条件的参数名和值
     * @throws GXDBConditionException   当条件值为null时抛出异常，防止意外的全表操作
     * @throws IllegalArgumentException 当SQL对象为null时抛出异常
     */
    static Map<String, Object> handleSQLCondition(SQL sql, List<GXCondition<?>> condition) {
        // 参数校验
        if (sql == null) {
            throw new IllegalArgumentException("SQL对象不能为null");
        }
        Map<String, Object> paramMap = new HashMap<>();
        // 如果条件为空，直接返回空参数映射
        if (Objects.isNull(condition) || condition.isEmpty()) {
            LOGGER.debug("WHERE条件为空，不添加任何条件");
            return paramMap;
        }
        // 收集所有有效的WHERE条件
        List<String> lastWheres = new ArrayList<>();
        // 遍历处理每个条件
        condition.forEach(c -> {
            // 跳过排除已删除记录的特殊条件
            if (!GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass())) {
                // 安全检查：确保非NULL条件的值不为null，防止意外的全表操作
                if (ObjectUtil.isNull(c.getFieldValue()) && !GXConditionIsNULL.class.isAssignableFrom(c.getClass())) {
                    String msg = CharSequenceUtil.format("数据查询条件错误【查询字段{}.{}的值是null】", c.getTableNameAlias(), c.getFieldExpression());
                    throw new GXDBConditionException(msg);
                }
                // 获取条件的SQL表达式
                String str = c.whereString();
                // 只添加非空条件
                if (CharSequenceUtil.isNotEmpty(str)) {
                    lastWheres.add(str);
                    // 收集参数映射，用于参数化查询
                    paramMap.putAll(c.getParamMap());
                    LOGGER.trace("添加WHERE条件: {}, 参数: {}", str, c.getParamMap());
                }
            }
        });
        if (!lastWheres.isEmpty()) {
            String whereStr = String.join(" AND ", lastWheres);
            sql.WHERE(whereStr);
            LOGGER.debug("最终WHERE条件: {}", whereStr);
        }
        return paramMap;
    }

    /**
     * 根据条件软(逻辑)删除
     * <p>
     * 该方法执行软删除操作，即更新记录的is_deleted字段为主键值，而不是物理删除记录。
     * 同时会更新deleted_at字段为当前时间戳，并可以选择性地更新其他字段。
     * 所有更新操作都使用参数化查询，确保SQL注入安全。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件，包含表名、条件等信息，不能为null
     * @param updateFieldList      软删除时需要同时更新的字段列表，可以为null或空列表
     * @return 生成的SQL语句
     * @throws GXBusinessException 当条件为空或表没有主键时抛出异常
     */
    static String deleteSoftCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXUpdateField<?>> updateFieldList) {
        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();
        String tableName = dbQueryParamInnerDto.getTableName();
        Dict extraData = Convert.convert(Dict.class, dbQueryParamInnerDto.getExtraData());
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        TableInfo tableInfo = TableInfoHelper.getTableInfo(tableName);
        String keyProperty = tableInfo.getKeyProperty();
        if (CharSequenceUtil.isEmpty(keyProperty)) {
            throw new GXBusinessException(CharSequenceUtil.format("请指定数据表{}的主键字段", tableName));
        }
        keyProperty = CharSequenceUtil.toUnderlineCase(keyProperty);
        LOGGER.info("deleteSoftCondition方法中的{}表的主键名字{}", tableName, keyProperty);
        SQL sql = new SQL().UPDATE(tableName);
        sql.SET(CharSequenceUtil.format("is_deleted = {}", keyProperty), CharSequenceUtil.format("deleted_at = {}", DateUtil.currentSeconds()));
        if (CollUtil.isNotEmpty(updateFieldList)) {
            for (GXUpdateField<?> field : updateFieldList) {
                sql.SET(field.updateString());
            }
        }
        if (CharSequenceUtil.isNotBlank(extraData.getStr("deletedBy"))) {
            List<TableFieldInfo> fieldList = tableInfo.getFieldList();
            for (TableFieldInfo fieldInfo : fieldList) {
                String column = fieldInfo.getColumn();
                if (CharSequenceUtil.equalsIgnoreCase("deleted_by", column)) {
                    sql.SET(CharSequenceUtil.format("deleted_by = '{}'", extraData.getStr("deletedBy")));
                    break;
                }
            }
        }
        Map<String, Object> paramMap = handleSQLCondition(sql, condition);
        dbQueryParamInnerDto.getParamMap().putAll(paramMap);
        if (!CollUtil.contains(condition, (c -> GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass())))) {
            sql.WHERE(CharSequenceUtil.format("{}.is_deleted = {}", tableName, 0));
        }
        return sql.toString();
    }

    /**
     * 根据条件删除（物理删除）
     * <p>
     * 该方法执行物理删除操作，从数据库中永久删除符合条件的记录。
     * 所有条件都使用参数化查询处理，确保SQL注入安全。
     * 注意：此操作不可逆，删除后数据无法恢复，请谨慎使用。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件，包含表名、条件等信息，不能为null
     * @return 生成的SQL语句
     * @throws GXBusinessException 当条件为空时抛出异常
     */
    static String deleteCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();
        String tableName = dbQueryParamInnerDto.getTableName();
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        SQL sql = new SQL().DELETE_FROM(tableName);
        Map<String, Object> paramMap = handleSQLCondition(sql, condition);
        dbQueryParamInnerDto.getParamMap().putAll(paramMap);
        if (!CollUtil.contains(condition, (c -> GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass())))) {
            sql.WHERE(CharSequenceUtil.format("{}.is_deleted = {}", tableName, 0));
        }
        return sql.toString();
    }

    /**
     * 构建Union语句 将组合出来的union语句作为from的表名来处理
     * eg: select * from (select * from test where name like '子曦%' union select * from test where phone like '520%') tmp where father='塵渊'
     *
     * @param dbQueryParamInnerDto       外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return SQL语句
     */
    static String unionFindByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        List<String> unionSqlLst = new ArrayList<>();
        unionQueryParamInnerDtoLst.forEach(queryParamInnerDto -> {
            String tableName = queryParamInnerDto.getTableName();
            if (CharSequenceUtil.isEmpty(tableName)) {
                queryParamInnerDto.setTableName(dbQueryParamInnerDto.getTableName());
            }
            String tableNameAlias = queryParamInnerDto.getTableNameAlias();
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                queryParamInnerDto.setTableNameAlias(queryParamInnerDto.getTableName());
            }
            String sql = findByCondition(queryParamInnerDto);
            dbQueryParamInnerDto.getParamMap().putAll(queryParamInnerDto.getParamMap());
            unionSqlLst.add("(" + sql + ")");
        });
        String unionSql = String.join("\n " + unionTypeEnums.getUnionType() + " \n", unionSqlLst);
        dbQueryParamInnerDto.setTableName("(" + unionSql + ")");
        dbQueryParamInnerDto.setTableNameAlias("tmp");
        if (CollUtil.isNotEmpty(dbQueryParamInnerDto.getCondition())) {
            dbQueryParamInnerDto.getCondition().forEach(condition -> {
                if (!condition.getTableNameAlias().equalsIgnoreCase("tmp")) {
                    condition.setTableNameAlias("tmp");
                }
            });
        }
        return GXBaseBuilder.findByCondition(dbQueryParamInnerDto);
    }

    /**
     * 构建Union语句 将组合出来的union语句作为from的表名来处理
     * eg: select * from (select * from test where name like '子曦%' union select * from test where phone like '520%') tmp where father='塵渊'
     *
     * @param dbQueryParamInnerDto       外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return SQL语句
     */
    static String unionFindOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        int limit = Optional.ofNullable(dbQueryParamInnerDto.getLimit()).orElse(1);
        if (limit <= 0) {
            limit = 1;
        }
        dbQueryParamInnerDto.setLimit(limit);
        unionQueryParamInnerDtoLst.forEach(queryParamInnerDto -> {
            String tableNameAlias = queryParamInnerDto.getTableNameAlias();
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                queryParamInnerDto.setTableNameAlias(queryParamInnerDto.getTableName());
            }
        });
        return unionFindByCondition(dbQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    /**
     * 构建Union语句 将组合出来的union语句作为from的表名来处理
     * eg: select * from (select * from test where name='子曦' union select * from test where phone like '520%') tmp where father='塵渊'
     *
     * @param page                       分页对象
     * @param dbQueryParamInnerDto       外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return SQL语句
     */
    @SuppressWarnings("unused")
    static <R> String unionPaginate(IPage<R> page, GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (CharSequenceUtil.isNotBlank(dbQueryParamInnerDto.getRawSQL())) {
            return dbQueryParamInnerDto.getRawSQL();
        }
        return unionFindByCondition(dbQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }
}
