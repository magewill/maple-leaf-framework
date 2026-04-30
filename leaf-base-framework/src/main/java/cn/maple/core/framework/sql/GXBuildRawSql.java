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

@SuppressWarnings("all")
public interface GXBuildRawSql {
    Logger LOGGER = LoggerFactory.getLogger(GXBuildRawSql.class);

    static StringBuilder updateFieldByCondition(String tableName, List<GXUpdateField<?>> fieldList, List<GXCondition<?>> condition) {
        return updateFieldByCondition(tableName, fieldList, condition, true);
    }

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

    static StringBuilder checkRecordIsExists(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        dbQueryParamInnerDto.setLimit(1);
        dbQueryParamInnerDto.setColumns(CollUtil.newHashSet("1"));
        return findOneByCondition(dbQueryParamInnerDto);
    }

    static StringBuilder findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        int limit = Optional.ofNullable(dbQueryParamInnerDto.getLimit()).orElse(1);
        if (limit <= 0) {
            limit = 1;
        }
        dbQueryParamInnerDto.setLimit(limit);
        return findByCondition(dbQueryParamInnerDto);
    }

    static StringBuilder countByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return countByCondition(dbQueryParamInnerDto, true);
    }

    static StringBuilder countByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, boolean columnToUnderlineCase) {
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM (");
        StringBuilder sql = findByCondition(dbQueryParamInnerDto, columnToUnderlineCase, false);
        countSql.append(sql).append(") AS TOTAL LIMIT 0 , 1");
        return countSql;
    }

    static StringBuilder findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return findByCondition(dbQueryParamInnerDto, true);
    }

    static StringBuilder findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, boolean columnToUnderlineCase) {
        return findByCondition(dbQueryParamInnerDto, columnToUnderlineCase, true);
    }

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
        List<GXJoinDto> joins = dbQueryParamInnerDto.getJoins();
        sql.append("SELECT ").append(selectStr).append(" FROM ").append(CharSequenceUtil.format("{} {}", tableName, tableNameAlias));
        if (CollUtil.isNotEmpty(joins)) {
            GXBuildRawSql.handleSQLJoin(sql, joins);
        }
        if (CollUtil.isNotEmpty(conditions)) {
            handleSQLCondition(sql, conditions, columnToUnderlineCase);
        }
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
        if (CollUtil.isNotEmpty(groupByField)) {
            sql.append(" GROUP BY ").append(ArrayUtil.join(groupByField.toArray(new String[0]), ","));
        }
        if (CollUtil.isNotEmpty(having)) {
            sql.append(" HAVING ").append(ArrayUtil.join(having.toArray(new String[0]), ","));
        }
        if (Objects.nonNull(orderByField) && !orderByField.isEmpty()) {
            String[] orderColumns = new String[orderByField.size()];
            Integer[] idx = new Integer[]{0};
            orderByField.forEach((k, v) -> orderColumns[idx[0]++] = CharSequenceUtil.format("{} {}", k, v));
            sql.append(" ORDER BY ").append(ArrayUtil.join(orderColumns, ","));
        }
        if (appendLimit) {
            handleLimit(sql, dbQueryParamInnerDto.getPage(), dbQueryParamInnerDto.getPageSize(), dbQueryParamInnerDto.getLimit());
        }
        return sql;
    }

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

    @SuppressWarnings("unused")
    static StringBuilder paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (CharSequenceUtil.isNotBlank(dbQueryParamInnerDto.getRawSQL())) {
            return new StringBuilder(dbQueryParamInnerDto.getRawSQL());
        }
        return findByCondition(dbQueryParamInnerDto);
    }

    static StringBuilder deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, boolean columnToUnderlineCase, Dict extraData) {
        return deleteSoftCondition(tableName, updateFieldList, condition, "id", columnToUnderlineCase, extraData);
    }

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

    @SuppressWarnings("unused")
    static StringBuilder unionPaginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (CharSequenceUtil.isNotBlank(masterQueryParamInnerDto.getRawSQL())) {
            return new StringBuilder(masterQueryParamInnerDto.getRawSQL());
        }
        return unionFindByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }
}