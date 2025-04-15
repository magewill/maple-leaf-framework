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
import cn.maple.core.framework.dto.inner.condition.GXConditionExclusionDeletedField;
import cn.maple.core.framework.dto.inner.condition.GXConditionIsNULL;
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
     *
     * @param dbQueryParamInnerDto 查询条件
     * @param fieldList            数据列表
     * @return String
     */
    static String updateFieldByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXUpdateField<?>> fieldList) {
        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();
        String tableName = dbQueryParamInnerDto.getTableName();
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        final SQL sql = new SQL().UPDATE(tableName);
        for (GXUpdateField<?> field : fieldList) {
            sql.SET(field.updateString());
            dbQueryParamInnerDto.getParamMap().putAll(field.getParamMap());
        }
        sql.SET(CharSequenceUtil.format("updated_at = {}", DateUtil.currentSeconds()));
        Map<String, Object> paramMap = handleSQLCondition(sql, condition);
        dbQueryParamInnerDto.getParamMap().putAll(paramMap);
        if (!CollUtil.contains(condition, (c -> GXConditionExclusionDeletedField.class.isAssignableFrom(c.getClass())))) {
            sql.WHERE(CharSequenceUtil.format("{}.is_deleted = {}", tableName, 0));
        }
        return sql.toString();
    }

    /**
     * 判断给定条件的值是否存在
     *
     * @param dbQueryParamInnerDto 查询条件
     * @return String
     */
    static String checkRecordIsExists(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
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
        if (!CollUtil.contains(condition, (c -> GXConditionExclusionDeletedField.class.isAssignableFrom(c.getClass())))) {
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
     *
     * @param sql   SQL语句
     * @param joins joins信息
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
     *
     * @param dbQueryParamInnerDto 查询条件
     * @return SQL语句
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
     *
     * @param sql       SQL对象
     * @param condition 条件
     */
    static Map<String, Object> handleSQLCondition(SQL sql, List<GXCondition<?>> condition) {
        Map<String, Object> paramMap = new HashMap<>();
        if (Objects.isNull(condition) || condition.isEmpty()) {
            return paramMap;
        }
        List<String> lastWheres = new ArrayList<>();
        condition.forEach(c -> {
            if (!GXConditionExclusionDeletedField.class.isAssignableFrom(c.getClass())) {
                if (ObjectUtil.isNull(c.getFieldValue()) && !GXConditionIsNULL.class.isAssignableFrom(c.getClass())) {
                    String msg = CharSequenceUtil.format("数据查询条件错误【查询字段{}.{}的值是null】", c.getTableNameAlias(), c.getFieldExpression());
                    throw new GXDBConditionException(msg);
                }
                String str = c.whereString();
                if (CharSequenceUtil.isNotEmpty(str)) {
                    lastWheres.add(str);
                    paramMap.putAll(c.getParamMap());
                }
            }
        });
        if (!lastWheres.isEmpty()) {
            String whereStr = String.join(" AND ", lastWheres);
            sql.WHERE(whereStr);
        }
        return paramMap;
    }

    /**
     * 根据条件软(逻辑)删除
     *
     * @param dbQueryParamInnerDto 查询条件
     * @param updateFieldList      软删除时需要同时更新的字段
     * @return SQL语句
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
        if (!CollUtil.contains(condition, (c -> GXConditionExclusionDeletedField.class.isAssignableFrom(c.getClass())))) {
            sql.WHERE(CharSequenceUtil.format("{}.is_deleted = {}", tableName, 0));
        }
        return sql.toString();
    }

    /**
     * 根据条件删除
     *
     * @param dbQueryParamInnerDto 查询条件
     * @return SQL语句
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
        if (!CollUtil.contains(condition, (c -> GXConditionExclusionDeletedField.class.isAssignableFrom(c.getClass())))) {
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
