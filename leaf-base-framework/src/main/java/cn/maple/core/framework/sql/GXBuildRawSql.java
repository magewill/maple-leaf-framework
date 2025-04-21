package cn.maple.core.framework.sql;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.maple.core.framework.constant.GXBuilderConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXJoinDto;
import cn.maple.core.framework.dto.inner.GXJoinTypeEnums;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.condition.GXExclusionDeletedFieldCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.inner.op.GXDbJoinOp;
import cn.maple.core.framework.exception.GXBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 构造原始SQL语句
 *
 * @author magleton
 */
@SuppressWarnings("all")
public interface GXBuildRawSql {
    Logger LOGGER = LoggerFactory.getLogger(GXBuildRawSql.class);

    /**
     * 更新实体字段和虚拟字段
     *
     * @param tableName             表名
     * @param fieldList             数据列表
     * @param condition             条件
     * @param columnToUnderlineCase 是否将查询字段转换成下划线
     * @return String
     */
    static StringBuilder updateFieldByCondition(String tableName, List<GXUpdateField<?>> fieldList, List<GXCondition<?>> condition) {
        return updateFieldByCondition(tableName, fieldList, condition, true);
    }

    /**
     * 更新实体字段和虚拟字段
     *
     * @param tableName             表名
     * @param fieldList             数据列表
     * @param condition             条件
     * @param columnToUnderlineCase 是否将查询字段转换成下划线
     * @return String
     */
    static StringBuilder updateFieldByCondition(String tableName, List<GXUpdateField<?>> fieldList, List<GXCondition<?>> condition, boolean columnToUnderlineCase) {
        final StringBuilder sql = new StringBuilder("UPDATE ").append(tableName);
        for (GXUpdateField<?> field : fieldList) {
            sql.append(field.updateString()).append(" ");
        }
        sql.append(CharSequenceUtil.format("updated_at = {}", DateUtil.currentSeconds()));
        handleSQLCondition(sql, condition, columnToUnderlineCase);
        if (!CollUtil.contains(condition, (c -> GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass())))) {
            sql.append(" WHERE ").append(CharSequenceUtil.format("{}.is_deleted = {}", tableName, 0));
        }
        return sql;
    }

    /**
     * 判断给定条件的值是否存在
     *
     * @param dbQueryParamInnerDto 查询条件
     * @return String
     */
    static StringBuilder checkRecordIsExists(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        dbQueryParamInnerDto.setLimit(1);
        dbQueryParamInnerDto.setColumns(CollUtil.newHashSet("1"));
        return findOneByCondition(dbQueryParamInnerDto);
    }

    /**
     * 通过条件获取数据列表
     *
     * @param dbQueryParamInnerDto 查询条件
     * @return SQL语句
     */
    static StringBuilder findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        int limit = Optional.ofNullable(dbQueryParamInnerDto.getLimit()).orElse(1);
        if (limit <= 0) {
            limit = 1;
        }
        dbQueryParamInnerDto.setLimit(limit);
        return findByCondition(dbQueryParamInnerDto);
    }

    /**
     * 获取统计的SQL语句
     *
     * @param dbQueryParamInnerDto 查询条件
     * @return 拼接好的SQL语句
     */
    static StringBuilder countByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return countByCondition(dbQueryParamInnerDto, true);
    }

    /**
     * 获取统计的SQL语句
     *
     * @param dbQueryParamInnerDto  查询条件
     * @param columnToUnderlineCase 是否将查询字段转换成下划线
     * @return 拼接好的SQL语句
     */
    static StringBuilder countByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, boolean columnToUnderlineCase) {
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM (");
        StringBuilder sql = findByCondition(dbQueryParamInnerDto, columnToUnderlineCase, false);
        countSql.append(sql).append(") AS TOTAL LIMIT 0 , 1");
        return countSql;
    }

    /**
     * 构造查询sql语句
     *
     * @param dbQueryParamInnerDto 查询条件
     * @return 拼接好的SQL语句
     */
    static StringBuilder findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return findByCondition(dbQueryParamInnerDto, true);
    }

    /**
     * 构造查询sql语句
     *
     * @param dbQueryParamInnerDto  查询条件
     * @param columnToUnderlineCase 是否将查询字段转换成下划线
     * @return 拼接好的SQL语句
     */
    static StringBuilder findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, boolean columnToUnderlineCase) {
        return findByCondition(dbQueryParamInnerDto, columnToUnderlineCase, true);
    }

    /**
     * 构造查询sql语句
     *
     * @param dbQueryParamInnerDto  查询条件
     * @param columnToUnderlineCase 是否将查询字段转换成下划线
     * @param appendLimit           生成的最终sql语句是否添加limit
     * @return 拼接好的SQL语句
     */
    static StringBuilder findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, boolean columnToUnderlineCase, boolean appendLimit) {
        Set<String> columns = dbQueryParamInnerDto.getColumns();
        String tableName = dbQueryParamInnerDto.getTableName();
        String tableNameAlias = Optional.ofNullable(dbQueryParamInnerDto.getTableNameAlias()).orElse(tableName);
        Set<String> groupByField = dbQueryParamInnerDto.getGroupByField();
        Map<String, String> orderByField = dbQueryParamInnerDto.getOrderByField();
        Set<String> having = dbQueryParamInnerDto.getHaving();
        Integer limit = dbQueryParamInnerDto.getLimit();
        List<GXCondition<?>> conditions = dbQueryParamInnerDto.getCondition();
        String selectStr = CharSequenceUtil.format("{}.*", tableNameAlias);
        if (CollUtil.isNotEmpty(columns)) {
            List<String> columnsCollect = columns.stream().map(column -> {
                return columnToUnderlineCase ? CharSequenceUtil.toUnderlineCase(column) : column;
            }).collect(Collectors.toList());
            selectStr = String.join(",", columnsCollect);
        }
        StringBuilder sql = new StringBuilder();
        // 处理JOIN
        List<GXJoinDto> joins = dbQueryParamInnerDto.getJoins();
        sql.append("SELECT ").append(selectStr).append(" FROM ").append(CharSequenceUtil.format("{} {}", tableName, tableNameAlias));
        if (CollUtil.isNotEmpty(joins)) {
            GXBuildRawSql.handleSQLJoin(sql, joins);
        }
        // 处理WHERE
        if (CollUtil.isNotEmpty(conditions)) {
            handleSQLCondition(sql, conditions, columnToUnderlineCase);
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
                handleSQLCondition(sql, joinConditions, columnToUnderlineCase);
            });
        }
        // 处理分组
        if (CollUtil.isNotEmpty(groupByField)) {
            sql.append(" GROUP BY ").append(ArrayUtil.join(groupByField.toArray(new String[0]), ","));
        }
        // 处理HAVING
        if (CollUtil.isNotEmpty(having)) {
            sql.append(" HAVING ").append(ArrayUtil.join(having.toArray(new String[0]), ","));
        }
        // 处理排序
        if (Objects.nonNull(orderByField) && !orderByField.isEmpty()) {
            String[] orderColumns = new String[orderByField.size()];
            Integer[] idx = new Integer[]{0};
            orderByField.forEach((k, v) -> orderColumns[idx[0]++] = CharSequenceUtil.format("{} {}", k, v));
            sql.append(" ORDER BY ").append(ArrayUtil.join(orderColumns, ","));
        }
        // 处理Limit分页
        if (appendLimit) {
            handleLimit(sql, dbQueryParamInnerDto.getPage(), dbQueryParamInnerDto.getPageSize(), dbQueryParamInnerDto.getLimit());
        }
        return sql;
    }

    /**
     * 处理limit分页
     *
     * @param sql      SQL语句
     * @param page     当前页
     * @param limit    自己指定的limit
     * @param pageSize 每页大小
     */
    static void handleLimit(StringBuilder sql, Integer page, Integer pageSize, Integer limit) {
        int currentPage = 0;
        if (Objects.nonNull(limit) && limit > 0) {
            pageSize = limit;
        } else {
            page = page == null ? 1 : page;
            pageSize = pageSize == null ? 10 : pageSize;
            currentPage = Math.max(page - 1, 0);
        }
        sql.append(" LIMIT ").append(currentPage * pageSize).append(" , ").append(pageSize);
    }

    /**
     * 处理JOIN表
     *
     * @param sql   SQL语句
     * @param joins joins信息
     */
    static void handleSQLJoin(StringBuilder sql, List<GXJoinDto> joins) {
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
                sql.append(" LEFT OUTER JOIN ").append(assemblySql);
            } else if (CharSequenceUtil.equalsIgnoreCase(GXBuilderConstant.RIGHT_JOIN_TYPE, joinType.getJoinType())) {
                sql.append(" RIGHT OUTER JOIN ").append(assemblySql);
            } else if (CharSequenceUtil.equalsIgnoreCase(GXBuilderConstant.INNER_JOIN_TYPE, joinType.getJoinType())) {
                sql.append(" INNER JOIN ").append(assemblySql);
            }
        });
    }

    /**
     * 处理SQL语句的Where条件
     *
     * @param sql                   SQL对象
     * @param condition             条件
     * @param columnToUnderlineCase 是否将查询字段转换成下划线
     */
    static void handleSQLCondition(StringBuilder sql, List<GXCondition<?>> condition, boolean columnToUnderlineCase) {
        if (Objects.isNull(condition) || condition.isEmpty()) {
            return;
        }
        List<String> lastWheres = new ArrayList<>();
        condition.forEach(c -> {
            if (!GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass())) {
                String str = c.whereString();
                if (!columnToUnderlineCase) {
                    str = CharSequenceUtil.replace(str, c.getFieldExpression(), CharSequenceUtil.toCamelCase(c.getFieldExpression()));
                }
                if (CharSequenceUtil.isNotEmpty(str)) {
                    lastWheres.add(str);
                }
            }
        });
        if (!lastWheres.isEmpty()) {
            String whereStr = String.join(" AND ", lastWheres);
            sql.append(" WHERE ").append(whereStr);
        }
    }


    /**
     * 通过条件获取分类数据
     *
     * @param dbQueryParamInnerDto 查询对象
     * @return SQL语句
     */
    @SuppressWarnings("unused")
    static StringBuilder paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (CharSequenceUtil.isNotBlank(dbQueryParamInnerDto.getRawSQL())) {
            return new StringBuilder(dbQueryParamInnerDto.getRawSQL());
        }
        return findByCondition(dbQueryParamInnerDto);
    }

    /**
     * 根据条件软(逻辑)删除
     *
     * @param tableName             表名
     * @param updateFieldList       软删除时需要同时更新的字段
     * @param condition             删除条件
     * @param extraData             额外数据
     * @param columnToUnderlineCase 是否将查询字段转换成下划线
     * @return SQL语句
     */
    static StringBuilder deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, boolean columnToUnderlineCase, Dict extraData) {
        return deleteSoftCondition(tableName, updateFieldList, condition, "id", columnToUnderlineCase, extraData);
    }

    /**
     * 根据条件软(逻辑)删除
     *
     * @param tableName             表名
     * @param updateFieldList       软删除时需要同时更新的字段
     * @param condition             删除条件
     * @param keyProperty           数据库主键ID
     * @param columnToUnderlineCase 是否将查询字段转换成下划线
     * @param extraData             额外数据
     * @return SQL语句
     */
    static StringBuilder deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, String keyProperty, boolean columnToUnderlineCase, Dict extraData) {
        if (CharSequenceUtil.isEmpty(keyProperty)) {
            keyProperty = "id";
        }
        if (CharSequenceUtil.isEmpty(keyProperty)) {
            throw new GXBusinessException(CharSequenceUtil.format("请指定数据表{}的主键字段", tableName));
        }
        keyProperty = CharSequenceUtil.toUnderlineCase(keyProperty);
        LOGGER.info("deleteSoftCondition方法中的{}表的主键名字{}", tableName, keyProperty);
        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName);
        Set<String> columns = CollUtil.newHashSet();
        columns.add(CharSequenceUtil.format("is_deleted = {}", keyProperty));
        columns.add(CharSequenceUtil.format("deleted_at = {}", DateUtil.currentSeconds()));
        if (CollUtil.isNotEmpty(updateFieldList)) {
            for (GXUpdateField<?> field : updateFieldList) {
                columns.add(field.updateString());
            }
        }
        if (CharSequenceUtil.isNotBlank(extraData.getStr("deletedBy"))) {
            columns.add(CharSequenceUtil.format("deleted_by = '{}'", extraData.getStr("deletedBy")));
            columns.add(CharSequenceUtil.format("deleted_at = {}", DateUtil.currentSeconds()));
        }
        sql.append(" SET ").append(CollUtil.join(columns, ","));
        handleSQLCondition(sql, condition, columnToUnderlineCase);
        Set<Object> wheres = CollUtil.newHashSet();
        if (!CollUtil.contains(condition, (c -> GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass())))) {
            wheres.add(CharSequenceUtil.format("{}.is_deleted = {}", tableName, 0));
        }
        sql.append(" AND ").append(CollUtil.join(wheres, " AND "));
        return sql;
    }

    /**
     * 根据条件删除
     *
     * @param tableName             表名
     * @param condition             删除条件
     * @param columnToUnderlineCase 是否将查询字段转换成下划线
     * @return SQL语句
     */
    static StringBuilder deleteCondition(String tableName, List<GXCondition<?>> condition, boolean columnToUnderlineCase) {
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(tableName);
        handleSQLCondition(sql, condition, columnToUnderlineCase);
        Set<String> wheres = CollUtil.newHashSet();
        if (!CollUtil.contains(condition, (c -> GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass())))) {
            wheres.add(CharSequenceUtil.format("{}.is_deleted = {}", tableName, 0));
        }
        sql.append(" AND ").append(CollUtil.join(wheres, " AND "));
        return sql;
    }

    /**
     * 构建Union语句 将组合出来的union语句作为from的表名来处理
     * eg: select * from (select * from test where name like '子曦%' union select * from test where phone like '520%') tmp where father='塵渊'
     *
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return SQL语句
     */
    static StringBuilder unionFindByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        List<String> unionSqlLst = new ArrayList<>();
        unionQueryParamInnerDtoLst.forEach(queryParamInnerDto -> {
            String tableName = queryParamInnerDto.getTableName();
            if (CharSequenceUtil.isEmpty(tableName)) {
                queryParamInnerDto.setTableName(masterQueryParamInnerDto.getTableName());
            }
            String tableNameAlias = queryParamInnerDto.getTableNameAlias();
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                queryParamInnerDto.setTableNameAlias(queryParamInnerDto.getTableName());
            }
            StringBuilder sql = findByCondition(queryParamInnerDto);
            unionSqlLst.add("(" + sql + ")");
        });
        String unionSql = String.join("\n " + unionTypeEnums.getUnionType() + " \n", unionSqlLst);
        masterQueryParamInnerDto.setTableName("(" + unionSql + ")");
        masterQueryParamInnerDto.setTableNameAlias("tmp");
        if (CollUtil.isNotEmpty(masterQueryParamInnerDto.getCondition())) {
            masterQueryParamInnerDto.getCondition().forEach(condition -> {
                if (!condition.getTableNameAlias().equalsIgnoreCase("tmp")) {
                    condition.setTableNameAlias("tmp");
                }
            });
        }
        return GXBuildRawSql.findByCondition(masterQueryParamInnerDto);
    }

    /**
     * 构建Union语句 将组合出来的union语句作为from的表名来处理
     * eg: select * from (select * from test where name like '子曦%' union select * from test where phone like '520%') tmp where father='塵渊'
     *
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return SQL语句
     */
    static StringBuilder unionFindOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        int limit = Optional.ofNullable(masterQueryParamInnerDto.getLimit()).orElse(1);
        if (limit <= 0) {
            limit = 1;
        }
        masterQueryParamInnerDto.setLimit(limit);
        unionQueryParamInnerDtoLst.forEach(queryParamInnerDto -> {
            String tableNameAlias = queryParamInnerDto.getTableNameAlias();
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                queryParamInnerDto.setTableNameAlias(queryParamInnerDto.getTableName());
            }
        });
        return unionFindByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    /**
     * 构建Union语句 将组合出来的union语句作为from的表名来处理
     * eg: select * from (select * from test where name='子曦' union select * from test where phone like '520%') tmp where father='塵渊'
     *
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return SQL语句
     */
    @SuppressWarnings("unused")
    static StringBuilder unionPaginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (CharSequenceUtil.isNotBlank(masterQueryParamInnerDto.getRawSQL())) {
            return new StringBuilder(masterQueryParamInnerDto.getRawSQL());
        }
        return unionFindByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }
}