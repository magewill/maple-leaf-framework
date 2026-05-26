package cn.maple.core.datasource.builder;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.Tuple;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.util.GXQueryParamUtils;
import cn.maple.core.framework.constant.GXBuilderConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXJoinDto;
import cn.maple.core.framework.dto.inner.GXJoinTypeEnums;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.*;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.inner.op.GXDbJoinOp;
import cn.maple.core.framework.dto.inner.op.GXDbJoinValue;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXDBConditionException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import org.apache.ibatis.jdbc.SQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public interface GXBaseBuilder {
    Logger LOGGER = LoggerFactory.getLogger(GXBaseBuilder.class);
    String AUTO_UNDERLINE_FIELD_ENABLED_KEY = "maple.framework.mybatis.sql.auto-underline-field-enabled";
    Pattern SAFE_IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*$");
    Pattern SAFE_ALIAS_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    Pattern QUALIFIED_WILDCARD_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*\\.\\*$");

    static String updateFieldByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXUpdateField<?>> fieldList) {
        if (dbQueryParamInnerDto == null) {
            throw new GXBusinessException("Query parameter object must not be null");
        }
        if (CollUtil.isEmpty(fieldList)) {
            throw new GXBusinessException("Update field list must not be empty");
        }
        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();
        String tableName = GXSqlTableMetadataSupport.validateMutableTableName(dbQueryParamInnerDto.getTableName());
        GXSqlBuildContext context = new GXSqlBuildContext();
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("Update conditions must not be empty to prevent full-table updates");
        }
        final SQL sql = new SQL().UPDATE(tableName);
        for (GXUpdateField<?> field : fieldList) {
            sql.SET(field.updateString());
            dbQueryParamInnerDto.getParamMap().putAll(field.getParamMap());
        }
        if (GXSqlTableMetadataSupport.hasColumn(context, tableName, "updated_at")) {
            String updatedAtParamName = CharSequenceUtil.format("updated_at_{}", UUID.randomUUID().toString().substring(0, 8));
            sql.SET(CharSequenceUtil.format("updated_at = #{dbQueryParamInnerDto.paramMap.{}}", updatedAtParamName));
            dbQueryParamInnerDto.getParamMap().put(updatedAtParamName, DateUtil.currentSeconds());
        }
        Map<String, Object> paramMap = handleSQLCondition(sql, condition, null, null);
        dbQueryParamInnerDto.getParamMap().putAll(paramMap);
        String logicNotDeletedCondition = GXSqlLogicDeleteSupport.buildLogicNotDeletedCondition(context, tableName, tableName, condition);
        if (CharSequenceUtil.isNotBlank(logicNotDeletedCondition)) {
            sql.WHERE(logicNotDeletedCondition);
        }
        String resultSql = sql.toString();
        LOGGER.debug("Generated update SQL: {}", resultSql);
        return resultSql;
    }

    static String checkRecordIsExists(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (dbQueryParamInnerDto == null) {
            throw new GXBusinessException("Query parameter object must not be null");
        }
        GXBaseQueryParamInnerDto existsQuery = copyQueryParam(dbQueryParamInnerDto);
        existsQuery.getParamMap().clear();
        existsQuery.setColumns(CollUtil.newLinkedHashSet("1"));
        String innerSql = findByCondition(existsQuery, null, newParamNamespace("exists"));
        dbQueryParamInnerDto.getParamMap().putAll(existsQuery.getParamMap());
        return GXSqlDialectSupport.buildExistsQuery(innerSql);
    }

    static String findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (dbQueryParamInnerDto == null) {
            throw new GXBusinessException("Query parameter object must not be null");
        }
        return findByCondition(dbQueryParamInnerDto, null, null);
    }

    private static String findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, String conditionAliasOverride, String paramNamespace) {
        String tableName = GXSqlTableMetadataSupport.validateQueryableTableExpression(dbQueryParamInnerDto.getTableName(), "MAIN");
        String tableNameAlias = GXSqlTableMetadataSupport.resolveTableAlias(tableName, dbQueryParamInnerDto.getTableNameAlias(), "MAIN");
        List<GXJoinDto> joins = dbQueryParamInnerDto.getJoins();
        Set<String> selectColumns = dbQueryParamInnerDto.getColumns();
        Set<String> groupByField = dbQueryParamInnerDto.getGroupByField();
        Map<String, String> orderByField = dbQueryParamInnerDto.getOrderByField();
        Set<String> having = dbQueryParamInnerDto.getHaving();
        List<GXCondition<?>> conditions = dbQueryParamInnerDto.getCondition();
        GXSqlFieldRenderSupport.SqlFieldRenderMode fieldRenderMode = GXSqlFieldRenderSupport.resolveSqlFieldRenderMode();
        GXSqlBuildContext context = new GXSqlBuildContext();
        Set<String> allowedColumns = GXSqlTableMetadataSupport.buildAllowedColumns(context, tableName, tableNameAlias, joins);
        Set<String> columns = CollUtil.newLinkedHashSet();
        if (CollUtil.isNotEmpty(selectColumns)) {
            columns.addAll(selectColumns);
        }
        String selectStr;
        if (CollUtil.isNotEmpty(columns)) {
            List<String> sanitizedSelectColumns = new ArrayList<>();
            for (String column : columns) {
                String normalizedColumn = GXSqlFieldRenderSupport.normalizeRequestedSelectColumn(column);
                String sanitizedColumn = GXSqlFieldRenderSupport.sanitizeSelectColumn(normalizedColumn, allowedColumns);
                String renderedColumn = GXSqlFieldRenderSupport.renderSelectColumn(column, sanitizedColumn, allowedColumns, fieldRenderMode);
                sanitizedSelectColumns.add(renderedColumn);
                GXSqlFieldRenderSupport.registerSelectOutputColumn(allowedColumns, renderedColumn, tableNameAlias);
            }
            selectStr = String.join(",", sanitizedSelectColumns);
        } else {
            selectStr = CharSequenceUtil.format("{}.*", tableNameAlias);
        }
        SQL sql = new SQL()
                .SELECT(selectStr)
                .FROM(CharSequenceUtil.format("{} {}", tableName, tableNameAlias));
        Map<String, Object> mergedParamMap = new HashMap<>();
        if (CollUtil.isNotEmpty(joins)) {
            mergedParamMap.putAll(handleSQLJoin(sql, joins, context, paramNamespace));
        }
        mergedParamMap.putAll(handleSQLCondition(sql, conditions, conditionAliasOverride, paramNamespace));
        String logicNotDeletedCondition = GXSqlLogicDeleteSupport.buildLogicNotDeletedCondition(context, tableName, tableNameAlias, conditions);
        if (CharSequenceUtil.isNotBlank(logicNotDeletedCondition)) {
            sql.WHERE(logicNotDeletedCondition);
        }
        dbQueryParamInnerDto.getParamMap().putAll(mergedParamMap);
        if (CollUtil.isNotEmpty(groupByField)) {
            String[] groupByColumns = groupByField.stream()
                    .map(column -> GXSqlFieldRenderSupport.sanitizeGroupBy(column, allowedColumns, tableName, tableNameAlias, fieldRenderMode))
                    .toArray(String[]::new);
            sql.GROUP_BY(groupByColumns);
        }
        if (CollUtil.isNotEmpty(having)) {
            String[] havingClauses = having.stream()
                    .map(clause -> GXSqlFieldRenderSupport.sanitizeHavingClause(clause, allowedColumns, fieldRenderMode))
                    .toArray(String[]::new);
            sql.HAVING(havingClauses);
        }
        if (CollUtil.isNotEmpty(orderByField)) {
            String[] orderColumns = orderByField.entrySet().stream()
                    .map(entry -> GXSqlFieldRenderSupport.sanitizeOrderBy(entry.getKey(), entry.getValue(), allowedColumns, tableName, tableNameAlias, fieldRenderMode))
                    .toArray(String[]::new);
            sql.ORDER_BY(orderColumns);
        }
        return sql.toString();
    }

    static Map<String, Object> handleSQLJoin(SQL sql, List<GXJoinDto> joins) {
        return handleSQLJoin(sql, joins, new GXSqlBuildContext(), null);
    }

    private static Map<String, Object> handleSQLJoin(SQL sql, List<GXJoinDto> joins, GXSqlBuildContext context, String paramNamespace) {
        HashMap<String, Object> paramMap = new HashMap<>();
        joins.forEach(join -> {
            GXJoinTypeEnums joinType = join.getJoinType();
            String tableName = CharSequenceUtil.isBlank(join.getJoinTableName())
                    ? ""
                    : GXSqlTableMetadataSupport.validateQueryableTableExpression(join.getJoinTableName(), "JOIN");
            String tableAliasName = GXSqlTableMetadataSupport.resolveOptionalTableAlias(tableName, join.getJoinTableNameAlias(), "JOIN");
            String masterTableName = GXSqlTableMetadataSupport.validateQueryableTableExpression(join.getMasterTableName(), "JOIN_MASTER");
            String masterTableNameAlias = GXSqlTableMetadataSupport.resolveOptionalTableAlias(masterTableName, join.getMasterTableNameAlias(), "JOIN_MASTER");
            List<GXDbJoinOp> andOps = Optional.ofNullable(join.getAnd()).orElse(Collections.emptyList());
            List<GXDbJoinOp> orOps = Optional.ofNullable(join.getOr()).orElse(Collections.emptyList());
            List<GXCondition<?>> conditions = join.getConditions();
            String whereStr = "";
            if (CollUtil.isNotEmpty(conditions)) {
                Tuple tuple = handleConditions(conditions, null, paramNamespace);
                List<String> lastWheres = tuple.get(0);
                paramMap.putAll(tuple.get(1));
                if (CollUtil.isNotEmpty(lastWheres)) {
                    whereStr = " AND " + String.join(" AND ", lastWheres);
                }
            }
            Tuple andTuple = renderJoinOps(andOps, GXBuilderConstant.AND_OP, masterTableNameAlias, tableAliasName);
            String andClause = andTuple.get(0);
            paramMap.putAll(andTuple.get(1));
            Tuple orTuple = renderJoinOps(orOps, GXBuilderConstant.OR_OP, masterTableNameAlias, tableAliasName);
            String orClause = orTuple.get(0);
            paramMap.putAll(orTuple.get(1));
            String joinTableWithAlias = CharSequenceUtil.isBlank(masterTableNameAlias)
                    ? masterTableName
                    : CharSequenceUtil.format("{} {}", masterTableName, masterTableNameAlias);
            String normalizedWhere = whereStr;
            if (CharSequenceUtil.isNotBlank(normalizedWhere) && normalizedWhere.startsWith(" AND ")) {
                normalizedWhere = normalizedWhere.substring(5);
            }
            String baseClause = CharSequenceUtil.isNotBlank(normalizedWhere)
                    ? CharSequenceUtil.format("{}{}{}", andClause, CharSequenceUtil.isNotBlank(andClause) ? GXBuilderConstant.AND_OP : "", normalizedWhere)
                    : andClause;
            if (CharSequenceUtil.isBlank(baseClause) && CharSequenceUtil.isBlank(orClause) && !join.isAutoFillIsDeleteCondition()) {
                throw new GXDBConditionException(CharSequenceUtil.format("JOIN [{}] must include at least one ON condition", tableName));
            }
            String onClause = CharSequenceUtil.isNotBlank(orClause)
                    ? (CharSequenceUtil.isNotBlank(baseClause)
                    ? CharSequenceUtil.format("({}) {} ({})", baseClause, GXBuilderConstant.OR_OP, orClause)
                    : orClause)
                    : baseClause;
            if (join.isAutoFillIsDeleteCondition()) {
                String joinLogicNotDeletedCondition = GXSqlLogicDeleteSupport.buildLogicNotDeletedCondition(context, tableName, Optional.ofNullable(tableAliasName).orElse(tableName), Collections.emptyList());
                if (CharSequenceUtil.isNotBlank(joinLogicNotDeletedCondition)) {
                    onClause = CharSequenceUtil.isBlank(onClause)
                            ? joinLogicNotDeletedCondition
                            : CharSequenceUtil.format("({}) {} ({})", onClause, GXBuilderConstant.AND_OP, joinLogicNotDeletedCondition);
                }
            }
            if (CharSequenceUtil.isBlank(onClause)) {
                throw new GXDBConditionException(CharSequenceUtil.format("JOIN [{}] must include at least one ON condition", tableName));
            }
            String assemblySql = CharSequenceUtil.format("{} ON ({})", joinTableWithAlias, onClause);
            if (Objects.isNull(joinType)) {
                throw new GXDBConditionException(CharSequenceUtil.format("JOIN [{}] type must not be null", tableName));
            }
            if (CharSequenceUtil.equalsIgnoreCase(GXBuilderConstant.LEFT_JOIN_TYPE, joinType.getJoinType())) {
                sql.LEFT_OUTER_JOIN(assemblySql);
            } else if (CharSequenceUtil.equalsIgnoreCase(GXBuilderConstant.RIGHT_JOIN_TYPE, joinType.getJoinType())) {
                sql.RIGHT_OUTER_JOIN(assemblySql);
            } else if (CharSequenceUtil.equalsIgnoreCase(GXBuilderConstant.INNER_JOIN_TYPE, joinType.getJoinType())) {
                sql.INNER_JOIN(assemblySql);
            } else {
                throw new GXDBConditionException(CharSequenceUtil.format("JOIN [{}] type is not supported: {}", tableName, joinType));
            }
        });
        return paramMap;
    }

    private static Tuple handleConditions(List<GXCondition<?>> conditions, String conditionAliasOverride, String paramNamespace) {
        Map<String, Object> paramMap = new HashMap<>();
        if (Objects.isNull(conditions) || conditions.isEmpty()) {
            LOGGER.debug("No conditions provided, skipping condition rendering.");
            return new Tuple(new ArrayList<>(), paramMap);
        }
        List<String> lastWheres = new ArrayList<>();
        conditions.forEach(c -> {
            if (!GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass())) {
                if (Objects.isNull(c.getValue())
                        && !GXConditionIsNULL.class.isAssignableFrom(c.getClass())
                        && !GXConditionIsNotNULL.class.isAssignableFrom(c.getClass())) {
                    String msg = CharSequenceUtil.format("Condition value must not be null: {}.{}", c.getTableNameAlias(), c.getFieldExpression());
                    throw new GXDBConditionException(msg);
                }
                GXConditionSegment segment = renderCondition(c, conditionAliasOverride, paramNamespace);
                if (Objects.nonNull(segment) && CharSequenceUtil.isNotEmpty(segment.sql())) {
                    lastWheres.add(segment.sql());
                    paramMap.putAll(segment.params());
                    LOGGER.trace("Appending WHERE conditions: {}, parameters: {}", segment.sql(), segment.params());
                }
            }
        });
        return new Tuple(lastWheres, paramMap);
    }

    private static Tuple renderJoinOps(List<GXDbJoinOp> ops, String delimiter, String defaultMasterAlias, String defaultJoinAlias) {
        Map<String, Object> paramMap = new HashMap<>();
        if (CollUtil.isEmpty(ops)) {
            return new Tuple("", paramMap);
        }
        List<String> clauses = new ArrayList<>();
        for (GXDbJoinOp op : ops) {
            String paramName = CharSequenceUtil.format("join_{}", UUID.randomUUID().toString().replace("-", ""));
            GXConditionSegment segment = renderJoinOp(op, defaultMasterAlias, defaultJoinAlias, paramName);
            if (Objects.nonNull(segment) && CharSequenceUtil.isNotBlank(segment.sql())) {
                clauses.add(segment.sql());
                paramMap.putAll(segment.params());
            }
        }
        return new Tuple(String.join(delimiter, clauses), paramMap);
    }

    static <R> String paginate(IPage<R> page, GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        Objects.requireNonNull(page, "page must not be null");
        if (CharSequenceUtil.isNotBlank(dbQueryParamInnerDto.getRawSQL())) {
            return GXSqlDialectSupport.validateRawSqlStrict(dbQueryParamInnerDto.getRawSQL());
        }
        return findByCondition(dbQueryParamInnerDto);
    }

    static String findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        String sql = findByCondition(dbQueryParamInnerDto);
        return GXSqlDialectSupport.applySingleRowLimit(sql);
    }

    private static Map<String, Object> handleSQLCondition(SQL sql, List<GXCondition<?>> conditions, String conditionAliasOverride, String paramNamespace) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL object must not be null");
        }
        Map<String, Object> paramMap = new HashMap<>();
        if (Objects.isNull(conditions) || conditions.isEmpty()) {
            LOGGER.debug("WHERE conditions are empty, returning empty param map.");
            return paramMap;
        }
        Tuple tuple = handleConditions(conditions, conditionAliasOverride, paramNamespace);
        List<String> lastWheres = tuple.get(0);
        if (!lastWheres.isEmpty()) {
            String whereStr = String.join(" AND ", lastWheres);
            sql.WHERE(whereStr);
            LOGGER.debug("Final WHERE condition: {}", whereStr);
        }
        return tuple.get(1);
    }

    static String deleteSoftCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXUpdateField<?>> updateFieldList) {
        if (dbQueryParamInnerDto == null) {
            throw new GXBusinessException("Query parameter object must not be null");
        }
        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();
        String tableName = GXSqlTableMetadataSupport.validateMutableTableName(dbQueryParamInnerDto.getTableName());
        GXSqlBuildContext context = new GXSqlBuildContext();
        Dict extraData = Optional.ofNullable(Convert.convert(Dict.class, dbQueryParamInnerDto.getExtraData())).orElse(Dict.create());
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("Conditions cannot be empty!");
        }
        TableInfo tableInfo = GXSqlTableMetadataSupport.getTableInfoSafely(context, tableName);
        if (Objects.isNull(tableInfo)) {
            throw new GXBusinessException(CharSequenceUtil.format("Cannot resolve TableInfo for table [{}]", tableName));
        }
        String logicDeletedSetSql = GXSqlLogicDeleteSupport.buildLogicDeletedSetSql(tableInfo);
        if (CharSequenceUtil.isBlank(logicDeletedSetSql)) {
            throw new GXBusinessException("No available logical-delete column configured (TableLogic/is_deleted)");
        }
        SQL sql = new SQL().UPDATE(tableName);
        sql.SET(logicDeletedSetSql);
        if (CollUtil.isNotEmpty(updateFieldList)) {
            for (GXUpdateField<?> field : updateFieldList) {
                sql.SET(field.updateString());
                dbQueryParamInnerDto.getParamMap().putAll(field.getParamMap());
            }
        }
        if (CharSequenceUtil.isNotBlank(extraData.getStr("deletedBy")) && GXSqlTableMetadataSupport.hasColumn(context, tableName, "deleted_by")) {
            String deletedByParamName = CharSequenceUtil.format("deleted_by_{}", UUID.randomUUID().toString().substring(0, 8));
            sql.SET(CharSequenceUtil.format("deleted_by = #{dbQueryParamInnerDto.paramMap.{}}", deletedByParamName));
            dbQueryParamInnerDto.getParamMap().put(deletedByParamName, extraData.getStr("deletedBy"));
        }
        Map<String, Object> paramMap = handleSQLCondition(sql, condition, null, null);
        dbQueryParamInnerDto.getParamMap().putAll(paramMap);
        String logicNotDeletedCondition = GXSqlLogicDeleteSupport.buildLogicNotDeletedCondition(context, tableName, tableName, condition);
        if (CharSequenceUtil.isNotBlank(logicNotDeletedCondition)) {
            sql.WHERE(logicNotDeletedCondition);
        }
        return sql.toString();
    }

    static String deleteCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (dbQueryParamInnerDto == null) {
            throw new GXBusinessException("Query parameter object must not be null");
        }
        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();
        String tableName = GXSqlTableMetadataSupport.validateMutableTableName(dbQueryParamInnerDto.getTableName());
        GXSqlBuildContext context = new GXSqlBuildContext();
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("Conditions must not be empty");
        }
        SQL sql = new SQL().DELETE_FROM(tableName);
        Map<String, Object> paramMap = handleSQLCondition(sql, condition, null, null);
        dbQueryParamInnerDto.getParamMap().putAll(paramMap);
        String logicNotDeletedCondition = GXSqlLogicDeleteSupport.buildLogicNotDeletedCondition(context, tableName, tableName, condition);
        if (CharSequenceUtil.isNotBlank(logicNotDeletedCondition)) {
            sql.WHERE(logicNotDeletedCondition);
        }
        return sql.toString();
    }

    static String unionFindByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (dbQueryParamInnerDto == null) {
            throw new GXBusinessException("Query parameter object must not be null");
        }
        if (CollUtil.isEmpty(unionQueryParamInnerDtoLst)) {
            throw new GXDBConditionException("UNION query list must not be empty");
        }
        if (Objects.isNull(unionTypeEnums)) {
            throw new GXDBConditionException("UNION type must not be null");
        }
        List<String> unionSqlLst = new ArrayList<>();
        unionQueryParamInnerDtoLst.forEach(queryParamInnerDto -> {
            GXBaseQueryParamInnerDto branchQuery = copyQueryParam(queryParamInnerDto);
            branchQuery.getParamMap().clear();
            if (CharSequenceUtil.isEmpty(branchQuery.getTableName())) {
                branchQuery.setTableName(dbQueryParamInnerDto.getTableName());
            }
            String sql = findByCondition(branchQuery, null, newParamNamespace("union"));
            dbQueryParamInnerDto.getParamMap().putAll(branchQuery.getParamMap());
            unionSqlLst.add("(" + sql + ")");
        });
        String unionSql = String.join("\n " + unionTypeEnums.getUnionType() + " \n", unionSqlLst);
        GXBaseQueryParamInnerDto outerQuery = copyQueryParam(dbQueryParamInnerDto);
        outerQuery.setTableName("(" + unionSql + ")");
        outerQuery.setTableNameAlias("tmp");
        String outerSql = findByCondition(outerQuery, "tmp", null);
        dbQueryParamInnerDto.getParamMap().putAll(outerQuery.getParamMap());
        return outerSql;
    }

    static String unionFindOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        return GXSqlDialectSupport.applySingleRowLimit(unionFindByCondition(dbQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums));
    }

    static <R> String unionPaginate(IPage<R> page, GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        Objects.requireNonNull(page, "page must not be null");
        if (CharSequenceUtil.isNotBlank(dbQueryParamInnerDto.getRawSQL())) {
            return GXSqlDialectSupport.validateRawSqlStrict(dbQueryParamInnerDto.getRawSQL());
        }
        return unionFindByCondition(dbQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    private static GXBaseQueryParamInnerDto copyQueryParam(GXBaseQueryParamInnerDto source) {
        return GXQueryParamUtils.copy(source);
    }

    private static GXConditionSegment renderJoinOp(GXDbJoinOp op, String defaultMasterAlias, String defaultJoinAlias, String paramName) {
        if (op instanceof GXDbJoinValue) {
            return renderJoinValueOp(op, defaultJoinAlias, paramName);
        }
        String masterAlias = CharSequenceUtil.isBlank(op.getMasterTableNameAlias()) ? defaultMasterAlias : op.getMasterTableNameAlias();
        String joinAlias = CharSequenceUtil.isBlank(op.getJoinTableNameAlias()) ? defaultJoinAlias : op.getJoinTableNameAlias();
        String masterField = qualifyJoinField(masterAlias, Objects.toString(op.getMasterFieldName(), ""));
        String joinField = qualifyJoinField(joinAlias, Objects.toString(op.getJoinFieldName(), ""));
        return new GXConditionSegment(masterField + readJoinOpOperator(op) + joinField, Collections.emptyMap());
    }

    private static GXConditionSegment renderJoinValueOp(GXDbJoinOp op, String defaultJoinAlias, String paramName) {
        try {
            GXDbJoinValue joinValue = (GXDbJoinValue) op;
            String declaredAlias = joinValue.getTableNameAlias();
            String fieldName = Objects.toString(joinValue.getFieldName(), "");
            Object fieldValue = joinValue.getFieldValue();
            String effectiveAlias = CharSequenceUtil.isBlank(declaredAlias) ? defaultJoinAlias : declaredAlias;
            if (CharSequenceUtil.isBlank(effectiveAlias) || !SAFE_ALIAS_PATTERN.matcher(effectiveAlias).matches()) {
                throw new GXDBConditionException(CharSequenceUtil.format("JOIN value table alias is invalid: {}", effectiveAlias));
            }
            String normalizedField = stripJoinFieldQualifier(fieldName);
            if (!SAFE_ALIAS_PATTERN.matcher(normalizedField).matches()) {
                throw new GXDBConditionException(CharSequenceUtil.format("JOIN field is invalid: {}", fieldName));
            }
            String sql = CharSequenceUtil.format("{}.{}{}#{dbQueryParamInnerDto.paramMap.{}}",
                    effectiveAlias, normalizedField, readJoinOpOperator(op), paramName);
            Map<String, Object> params = new HashMap<>();
            params.put(paramName, fieldValue);
            return new GXConditionSegment(sql, params);
        } catch (GXDBConditionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GXDBConditionException(CharSequenceUtil.format("Unable to render JOIN value condition [{}]", op.getClass().getName()));
        }
    }

    private static String readJoinOpOperator(GXDbJoinOp op) {
        try {
            Method method = op.getClass().getDeclaredMethod("getOp");
            method.setAccessible(true);
            return Objects.toString(method.invoke(op), "");
        } catch (Exception ex) {
            throw new GXDBConditionException(CharSequenceUtil.format("Unable to resolve JOIN operator [{}]", op.getClass().getName()));
        }
    }

    private static String qualifyJoinField(String tableAlias, String fieldName) {
        String normalizedField = stripJoinFieldQualifier(fieldName);
        if (CharSequenceUtil.isNotBlank(tableAlias) && !SAFE_ALIAS_PATTERN.matcher(tableAlias).matches()) {
            throw new GXDBConditionException(CharSequenceUtil.format("JOIN table alias is invalid: {}", tableAlias));
        }
        if (!SAFE_ALIAS_PATTERN.matcher(normalizedField).matches()) {
            throw new GXDBConditionException(CharSequenceUtil.format("JOIN field is invalid: {}", fieldName));
        }
        return CharSequenceUtil.isBlank(tableAlias)
                ? normalizedField
                : CharSequenceUtil.format("{}.{}", tableAlias, normalizedField);
    }

    private static String stripJoinFieldQualifier(String fieldName) {
        String trimmed = CharSequenceUtil.trim(fieldName);
        int dotIndex = trimmed.lastIndexOf('.');
        return dotIndex >= 0 ? trimmed.substring(dotIndex + 1) : trimmed;
    }

    private static GXConditionSegment renderCondition(GXCondition<?> condition, String tableAliasOverride, String paramNamespace) {
        if (condition instanceof GXConditionJsonEQ jsonEq) {
            return namespaceConditionSegment(renderJsonEqCondition(jsonEq, tableAliasOverride), paramNamespace);
        }
        GXConditionSegment segment = condition.toSegment();
        if (CharSequenceUtil.isBlank(tableAliasOverride)
                || CharSequenceUtil.isBlank(condition.getFieldExpression())
                || condition instanceof GXConditionRaw) {
            return namespaceConditionSegment(segment, paramNamespace);
        }
        return namespaceConditionSegment(new GXConditionSegment(rewriteConditionAlias(segment.sql(), condition, tableAliasOverride), segment.params()), paramNamespace);
    }

    private static GXConditionSegment renderJsonEqCondition(GXConditionJsonEQ condition, String tableAliasOverride) {
        String dbType = GXSqlDialectSupport.resolveDbTypeFromContext().toLowerCase(Locale.ROOT);
        String field = qualifyConditionField(CharSequenceUtil.isBlank(tableAliasOverride) ? condition.getTableNameAlias() : tableAliasOverride,
                condition.getFieldExpression());
        String paramName = condition.getParamName();
        String pathParamName = paramName + "_path";
        String pathParam = CharSequenceUtil.format("#{dbQueryParamInnerDto.paramMap.{}}", pathParamName);
        String valueParam = CharSequenceUtil.format("#{dbQueryParamInnerDto.paramMap.{}}", paramName);
        String sql = GXSqlDialectSupport.renderJsonEqByDialect(dbType, field, pathParam, valueParam);
        Map<String, Object> params = new HashMap<>();
        params.put(paramName, condition.getValue());
        params.put(pathParamName, condition.getJsonPath());
        return new GXConditionSegment(sql, params);
    }

    private static String rewriteConditionAlias(String sql, GXCondition<?> condition, String tableAliasOverride) {
        String fieldExpression = condition.getFieldExpression();
        String originalAlias = condition.getTableNameAlias();
        String currentPrefix = CharSequenceUtil.isBlank(originalAlias)
                ? fieldExpression
                : CharSequenceUtil.format("{}.{}", originalAlias, fieldExpression);
        String overridePrefix = CharSequenceUtil.format("{}.{}", tableAliasOverride, fieldExpression);
        if (sql.startsWith(currentPrefix)) {
            return overridePrefix + sql.substring(currentPrefix.length());
        }
        return sql;
    }

    private static String qualifyConditionField(String tableAlias, String fieldExpression) {
        if (SAFE_IDENTIFIER_PATTERN.matcher(fieldExpression).matches()) {
            if (CharSequenceUtil.isBlank(tableAlias) || fieldExpression.contains(".")) {
                return fieldExpression;
            }
            return CharSequenceUtil.format("{}.{}", tableAlias, fieldExpression);
        }
        throw new GXDBConditionException(CharSequenceUtil.format("Invalid field expression: {}", fieldExpression));
    }

    private static String newParamNamespace(String prefix) {
        return CharSequenceUtil.format("{}_{}_", prefix, UUID.randomUUID().toString().replace("-", ""));
    }

    private static GXConditionSegment namespaceConditionSegment(GXConditionSegment segment, String paramNamespace) {
        if (segment == null || CharSequenceUtil.isBlank(paramNamespace) || segment.params().isEmpty()) {
            return segment;
        }
        String sql = segment.sql();
        Map<String, Object> params = new HashMap<>();
        List<Map.Entry<String, Object>> sortedEntries = new ArrayList<>(segment.params().entrySet());
        sortedEntries.sort(Comparator.comparingInt((Map.Entry<String, Object> entry) -> entry.getKey().length()).reversed());
        for (Map.Entry<String, Object> entry : sortedEntries) {
            String oldName = entry.getKey();
            String newName = paramNamespace + oldName;
            sql = sql.replace(paramExpression(oldName), paramExpression(newName));
            params.put(newName, entry.getValue());
        }
        return new GXConditionSegment(sql, params);
    }

    private static String paramExpression(String paramName) {
        return "#{dbQueryParamInnerDto.paramMap." + paramName + "}";
    }

}
