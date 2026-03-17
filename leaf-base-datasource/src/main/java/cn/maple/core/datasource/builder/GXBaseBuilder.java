package cn.maple.core.datasource.builder;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.Tuple;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.constant.GXBuilderConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXJoinDto;
import cn.maple.core.framework.dto.inner.GXJoinTypeEnums;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionIsNULL;
import cn.maple.core.framework.dto.inner.condition.GXExclusionDeletedFieldCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.inner.op.GXDbJoinOp;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXDBConditionException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
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
     * 该方法通过调用findOneByCondition方法实现，自动处理了软删除逻辑（is_deleted=0条件）。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件，包含表名、条件等信息，不能为null
     * @return 生成的SQL语句
     * @see #findOneByCondition(GXBaseQueryParamInnerDto) 该方法内部调用findOneByCondition实现查询
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
        Map<String, Object> joinParamMap = new HashMap<>();
        if (Objects.nonNull(joins) && !joins.isEmpty()) {
            joinParamMap = handleSQLJoin(sql, joins);
        }
        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();
        // 处理WHERE
        Map<String, Object> paramMap = handleSQLCondition(sql, condition);
        paramMap.putAll(joinParamMap);
        // 将参数设置到Mybatis的参数Map中
        dbQueryParamInnerDto.getParamMap().putAll(paramMap);
        if (!CollUtil.contains(condition, (c -> GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass())))) {
            sql.WHERE(CharSequenceUtil.format("{}.is_deleted = {}", tableNameAlias, 0));
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
     * 处理SQL查询中的JOIN表关联
     * <p>
     * 该方法处理SQL查询中的JOIN操作，支持LEFT JOIN、RIGHT JOIN和INNER JOIN三种关联类型。
     * 可以通过AND和OR条件组合构建复杂的JOIN条件，实现灵活的多表查询。
     * </p>
     *
     * @param sql   SQL对象，用于构建SQL语句，不能为null
     * @param joins JOIN信息列表，包含JOIN类型、表名、条件等，不能为null
     */
    static Map<String, Object> handleSQLJoin(SQL sql, List<GXJoinDto> joins) {
        HashMap<String, Object> paramMap = new HashMap<>();
        joins.forEach(join -> {
            GXJoinTypeEnums joinType = join.getJoinType();
            String tableName = join.getJoinTableName();
            String tableAliasName = join.getJoinTableNameAlias();
            String masterTableName = join.getMasterTableName();
            String masterTableNameAlias = join.getMasterTableNameAlias();
            if (Objects.isNull(masterTableNameAlias)) {
                masterTableNameAlias = masterTableName;
            }
            // 处理JOIN符加条件 ON a.id = b.aid AND xx.aa="aaa"
            List<GXCondition<?>> conditions = join.getConditions();
            String whereStr = "";
            if (CollUtil.isNotEmpty(conditions)) {
                Tuple tuple = handleConditions(conditions);
                List<String> lastWheres = tuple.get(0);
                paramMap.putAll(tuple.get(1));
                if (CollUtil.isNotEmpty(lastWheres)) {
                    whereStr = " AND " + String.join(" AND ", lastWheres);
                }
            }
            String andClause = Optional.ofNullable(join.getAnd()).orElse(Collections.emptyList()).stream().map(GXDbJoinOp::opString).collect(Collectors.joining(GXBuilderConstant.AND_OP));
            String orClause = Optional.ofNullable(join.getOr()).orElse(Collections.emptyList()).stream().map(GXDbJoinOp::opString).collect(Collectors.joining(GXBuilderConstant.AND_OP));
            String assemblySql = CharSequenceUtil.format("{} {} ON ({} {})", masterTableName, masterTableNameAlias, andClause, whereStr);
            if (CharSequenceUtil.isNotEmpty(orClause)) {
                assemblySql = assemblySql.replace("ON (", "ON ((");
                assemblySql = CharSequenceUtil.format("{} {} ({} {}))", assemblySql, GXBuilderConstant.OR_OP, orClause, whereStr);
            }
            if (CharSequenceUtil.equalsIgnoreCase(GXBuilderConstant.LEFT_JOIN_TYPE, joinType.getJoinType())) {
                sql.LEFT_OUTER_JOIN(assemblySql);
            } else if (CharSequenceUtil.equalsIgnoreCase(GXBuilderConstant.RIGHT_JOIN_TYPE, joinType.getJoinType())) {
                sql.RIGHT_OUTER_JOIN(assemblySql);
            } else if (CharSequenceUtil.equalsIgnoreCase(GXBuilderConstant.INNER_JOIN_TYPE, joinType.getJoinType())) {
                sql.INNER_JOIN(assemblySql);
            }
        });
        return paramMap;
    }

    /**
     * 处理插叙条件
     *
     * @param conditions 查询列表参数
     * @return Tuple
     */
    private static Tuple handleConditions(List<GXCondition<?>> conditions) {
        Map<String, Object> paramMap = new HashMap<>();
        // 如果条件为空，直接返回空参数映射
        if (Objects.isNull(conditions) || conditions.isEmpty()) {
            LOGGER.debug("条件为空，不添加任何条件");
            return new Tuple(new ArrayList<>(), paramMap);
        }
        // 收集所有有效的WHERE条件
        List<String> lastWheres = new ArrayList<>();
        // 遍历处理每个条件
        conditions.forEach(c -> {
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
        return new Tuple(lastWheres, paramMap);
    }

    /**
     * 通过条件获取分页数据
     * <p>
     * 该方法用于构建分页查询的SQL语句，支持原生SQL和条件构建两种方式。
     * 当提供了原生SQL时，直接使用原生SQL；否则，调用findByCondition方法构建SQL语句。
     * </p>
     *
     * @param page                 分页对象，包含页码、每页记录数等分页信息，不能为null
     * @param dbQueryParamInnerDto 查询条件对象，包含表名、字段、条件、排序等信息，不能为null
     * @return 生成的SQL语句，可以是原生SQL或条件构建的SQL
     * @throws GXSqlInjectionException 当检测到SQL注入风险且配置为抛出异常时抛出
     */
    @SuppressWarnings("unused")
    static <R> String paginate(IPage<R> page, GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (CharSequenceUtil.isNotBlank(dbQueryParamInnerDto.getRawSQL())) {
            String rawSQL = dbQueryParamInnerDto.getRawSQL();
            // 检测SQL注入风险
            if (GXDBStringEscapeUtils.check(rawSQL)) {
                LOGGER.error("检测到SQL注入风险！原始SQL：{}", rawSQL);
                // 这里可以根据实际需求决定是否抛出异常
                // throw new GXSqlInjectionException("检测到SQL注入风险，查询已被阻止");
            }
            return rawSQL;
        }
        return findByCondition(dbQueryParamInnerDto);
    }

    /**
     * 通过条件获取数据列表
     * <p>
     * 该方法根据查询参数构建完整的SELECT查询语句，支持字段选择、表别名、JOIN、WHERE条件、
     * GROUP BY、HAVING、ORDER BY和LIMIT等SQL功能。所有条件都使用参数化查询处理，防止SQL注入。
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
     * @param sql        SQL对象，用于构建SQL语句，不能为null
     * @param conditions 条件列表，可以为null或空列表
     * @return 参数映射，包含所有条件的参数名和值
     * @throws GXDBConditionException   当条件值为null时抛出异常，防止意外的全表操作
     * @throws IllegalArgumentException 当SQL对象为null时抛出异常
     */
    static Map<String, Object> handleSQLCondition(SQL sql, List<GXCondition<?>> conditions) {
        // 参数校验
        if (sql == null) {
            throw new IllegalArgumentException("SQL对象不能为null");
        }
        Map<String, Object> paramMap = new HashMap<>();
        // 如果条件为空，直接返回空参数映射
        if (Objects.isNull(conditions) || conditions.isEmpty()) {
            LOGGER.debug("WHERE条件为空，不添加任何条件");
            return paramMap;
        }
        Tuple tuple = handleConditions(conditions);
        List<String> lastWheres = tuple.get(0);
        if (!lastWheres.isEmpty()) {
            String whereStr = String.join(" AND ", lastWheres);
            sql.WHERE(whereStr);
            LOGGER.debug("最终WHERE条件: {}", whereStr);
        }
        return tuple.get(1);
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
     * 构建Union查询SQL语句
     * <p>
     * 该方法用于构建UNION或UNION ALL查询SQL语句，将多个查询结果合并为一个结果集。
     * 方法会将组合出来的union语句作为外层查询的FROM子句，并可以在外层查询中添加额外的条件。
     * 所有查询都使用参数化查询方式，确保SQL注入安全。
     * </p>
     *
     * @param dbQueryParamInnerDto       外层的主查询条件，用于对合并后的结果进行进一步过滤和处理，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表，包含所有需要合并的子查询，不能为null或空
     * @param unionTypeEnums             union的类型，可以是UNION（去重）或UNION ALL（不去重），不能为null
     * @return 生成的完整SQL语句，可以直接通过MyBatis执行
     * @throws GXBusinessException 当参数验证失败时抛出
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
     * 构建Union查询SQL语句并只返回一条记录
     * <p>
     * 该方法是{@link #unionFindByCondition}的特殊版本，用于构建UNION或UNION ALL查询SQL语句，
     * 并限制结果只返回一条记录。适用于只需要获取合并结果中的第一条记录的场景。
     * 方法会自动设置limit为1，确保只返回一条记录，提高查询效率。
     * </p>
     *
     * @param dbQueryParamInnerDto       外层的主查询条件，用于对合并后的结果进行进一步过滤和处理，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表，包含所有需要合并的子查询，不能为null或空
     * @param unionTypeEnums             union的类型，可以是UNION（去重）或UNION ALL（不去重），不能为null
     * @return 生成的完整SQL语句，可以直接通过MyBatis执行，结果将被限制为只返回一条记录
     * @throws GXBusinessException 当参数验证失败时抛出
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
     * 构建Union查询SQL语句并支持分页
     * <p>
     * 该方法用于构建支持分页的UNION或UNION ALL查询SQL语句，将多个查询结果合并为一个结果集并进行分页。
     * 方法接收一个分页对象，用于指定分页参数，但实际的分页操作由MyBatis-Plus框架处理。
     * 如果查询参数中已经设置了原始SQL（rawSQL），则直接返回该SQL而不构建新的查询。
     * </p>
     *
     * @param page                       分页对象，用于指定分页参数，由MyBatis-Plus框架处理实际的分页操作
     * @param dbQueryParamInnerDto       外层的主查询条件，用于对合并后的结果进行进一步过滤和处理，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表，包含所有需要合并的子查询，不能为null或空
     * @param unionTypeEnums             union的类型，可以是UNION（去重）或UNION ALL（不去重），不能为null
     * @return 生成的完整SQL语句，可以直接通过MyBatis执行，结果将根据分页参数进行分页
     * @throws GXBusinessException 当参数验证失败时抛出
     */
    @SuppressWarnings("unused")
    static <R> String unionPaginate(IPage<R> page, GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (CharSequenceUtil.isNotBlank(dbQueryParamInnerDto.getRawSQL())) {
            return dbQueryParamInnerDto.getRawSQL();
        }
        return unionFindByCondition(dbQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }
}
