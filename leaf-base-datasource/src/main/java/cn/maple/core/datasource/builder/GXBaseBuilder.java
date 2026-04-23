package cn.maple.core.datasource.builder;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.Tuple;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.properties.GXDataSourceProperties;
import cn.maple.core.framework.constant.GXBuilderConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXJoinDto;
import cn.maple.core.framework.dto.inner.GXJoinTypeEnums;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.*;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.inner.op.GXDbJoinOp;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXDBConditionException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.jdbc.SQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public interface GXBaseBuilder {
    Logger LOGGER = LoggerFactory.getLogger(GXBaseBuilder.class);
    Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    Pattern SAFE_IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_\\.]*$");
    Pattern HAVING_TOKEN_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_\\.]*");
    Pattern DANGEROUS_SQL_TOKEN_PATTERN = Pattern.compile("(?i)\\b(update|delete|insert|alter|drop|truncate|create|grant|revoke|call|exec|merge)\\b");
    Set<String> HAVING_KEYWORD_WHITELIST = Set.of(
            "AND", "OR", "NOT", "NULL", "IS", "LIKE", "IN", "BETWEEN", "AS", "DISTINCT",
            "CASE", "WHEN", "THEN", "ELSE", "END", "SUM", "COUNT", "AVG", "MIN", "MAX", "FILTER", "OVER"
    );
    Set<String> HAVING_FUNCTION_WHITELIST = Set.of(
            "SUM", "COUNT", "AVG", "MIN", "MAX", "COALESCE", "NULLIF", "ROUND", "ABS", "CEIL", "FLOOR",
            "JSON_VALUE", "JSON_EXTRACT", "DATE_TRUNC", "DATE_FORMAT", "TO_CHAR", "CAST"
    );

    static String updateFieldByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXUpdateField<?>> fieldList) {
        if (dbQueryParamInnerDto == null) {
            throw new GXBusinessException("查询参数对象不能为null!");
        }
        if (CollUtil.isEmpty(fieldList)) {
            throw new GXBusinessException("更新字段列表不能为空!");
        }
        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();
        String tableName = dbQueryParamInnerDto.getTableName();
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("更新条件不能为空，为防止全表更新风险!");
        }
        final SQL sql = new SQL().UPDATE(tableName);
        for (GXUpdateField<?> field : fieldList) {
            sql.SET(field.updateString());
            dbQueryParamInnerDto.getParamMap().putAll(field.getParamMap());
        }
        if (hasColumn(tableName, "updated_at")) {
            sql.SET(CharSequenceUtil.format("updated_at = {}", DateUtil.currentSeconds()));
        }
        Map<String, Object> paramMap = handleSQLCondition(sql, condition);
        dbQueryParamInnerDto.getParamMap().putAll(paramMap);
        String logicNotDeletedCondition = buildLogicNotDeletedCondition(tableName, tableName, condition);
        if (CharSequenceUtil.isNotBlank(logicNotDeletedCondition)) {
            sql.WHERE(logicNotDeletedCondition);
        }
        String resultSql = sql.toString();
        LOGGER.debug("生成的更新SQL: {}", resultSql);
        return resultSql;
    }

    static String checkRecordIsExists(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        dbQueryParamInnerDto.setColumns(CollUtil.newHashSet("1"));
        String innerSql = findByCondition(dbQueryParamInnerDto);
        return CharSequenceUtil.format("SELECT CASE WHEN EXISTS ({}) THEN 1 ELSE 0 END", innerSql);
    }

    static String findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        Set<String> columns = dbQueryParamInnerDto.getColumns();
        String tableName = dbQueryParamInnerDto.getTableName();
        String tableNameAlias = Optional.ofNullable(dbQueryParamInnerDto.getTableNameAlias()).orElse(tableName);
        Set<String> groupByField = dbQueryParamInnerDto.getGroupByField();
        Map<String, String> orderByField = dbQueryParamInnerDto.getOrderByField();
        Set<String> having = dbQueryParamInnerDto.getHaving();
        Set<String> allowedColumns = buildAllowedColumns(tableName, tableNameAlias, dbQueryParamInnerDto.getJoins());
        String selectStr = CharSequenceUtil.format("{}.*", tableNameAlias);
        if (CollUtil.isNotEmpty(columns)) {
            List<String> columnsCollect = columns.stream()
                    .map(column -> sanitizeSelectColumn(column, allowedColumns))
                    .collect(Collectors.toList());
            selectStr = String.join(",", columnsCollect);
        }
        SQL sql = new SQL().SELECT(selectStr).FROM(CharSequenceUtil.format("{} {}", tableName, tableNameAlias));
        List<GXJoinDto> joins = dbQueryParamInnerDto.getJoins();
        Map<String, Object> joinParamMap = new HashMap<>();
        if (Objects.nonNull(joins) && !joins.isEmpty()) {
            joinParamMap = handleSQLJoin(sql, joins);
        }
        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();
        Map<String, Object> paramMap = handleSQLCondition(sql, condition);
        paramMap.putAll(joinParamMap);
        dbQueryParamInnerDto.getParamMap().putAll(paramMap);
        String logicNotDeletedCondition = buildLogicNotDeletedCondition(tableName, tableNameAlias, condition);
        if (CharSequenceUtil.isNotBlank(logicNotDeletedCondition)) {
            sql.WHERE(logicNotDeletedCondition);
        }
        if (CollUtil.isNotEmpty(groupByField)) {
            String[] groupByColumns = groupByField.stream()
                    .map(column -> sanitizeStructuralColumn(column, allowedColumns, "GROUP BY", true))
                    .toArray(String[]::new);
            sql.GROUP_BY(groupByColumns);
        }
        if (CollUtil.isNotEmpty(having)) {
            String[] havingColumns = having.stream()
                    .map(clause -> sanitizeHavingClause(clause, allowedColumns))
                    .toArray(String[]::new);
            sql.HAVING(havingColumns);
        }
        if (Objects.nonNull(orderByField) && !orderByField.isEmpty()) {
            String[] orderColumns = new String[orderByField.size()];
            Integer[] idx = new Integer[]{0};
            orderByField.forEach((k, v) -> orderColumns[idx[0]++] = sanitizeOrderBy(k, v, allowedColumns));
            sql.ORDER_BY(orderColumns);
        }
        return sql.toString();
    }

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
            String andClause = Optional.ofNullable(join.getAnd())
                    .orElse(Collections.emptyList())
                    .stream()
                    .map(GXDbJoinOp::opString)
                    .collect(Collectors.joining(GXBuilderConstant.AND_OP));
            String orClause = Optional.ofNullable(join.getOr())
                    .orElse(Collections.emptyList())
                    .stream()
                    .map(GXDbJoinOp::opString)
                    .collect(Collectors.joining(GXBuilderConstant.OR_OP));
            String joinTableWithAlias = CharSequenceUtil.isBlank(tableAliasName)
                    ? tableName
                    : CharSequenceUtil.format("{} {}", tableName, tableAliasName);
            String normalizedWhere = whereStr;
            if (CharSequenceUtil.isNotBlank(normalizedWhere) && normalizedWhere.startsWith(" AND ")) {
                normalizedWhere = normalizedWhere.substring(5);
            }
            String baseClause = CharSequenceUtil.isNotBlank(normalizedWhere)
                    ? CharSequenceUtil.format("{}{}{}", andClause, CharSequenceUtil.isNotBlank(andClause) ? GXBuilderConstant.AND_OP : "", normalizedWhere)
                    : andClause;
            if (CharSequenceUtil.isBlank(baseClause)) {
                baseClause = "1 = 1";
            }
            if (join.isAutoFillIsDeleteCondition()) {
                String joinLogicNotDeletedCondition = buildLogicNotDeletedCondition(tableName, Optional.ofNullable(tableAliasName).orElse(tableName), Collections.emptyList());
                if (CharSequenceUtil.isNotBlank(joinLogicNotDeletedCondition)) {
                    baseClause = CharSequenceUtil.format("({}) {} ({})", baseClause, GXBuilderConstant.AND_OP, joinLogicNotDeletedCondition);
                }
            }
            String onClause = CharSequenceUtil.isNotBlank(orClause)
                    ? CharSequenceUtil.format("({}) {} ({})", baseClause, GXBuilderConstant.OR_OP, orClause)
                    : baseClause;
            String assemblySql = CharSequenceUtil.format("{} ON ({})", joinTableWithAlias, onClause);
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

    private static Tuple handleConditions(List<GXCondition<?>> conditions) {
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
                GXConditionSegment segment = renderCondition(c);
                if (Objects.nonNull(segment) && CharSequenceUtil.isNotEmpty(segment.sql())) {
                    lastWheres.add(segment.sql());
                    paramMap.putAll(segment.params());
                    LOGGER.trace("添加WHERE条件: {}, 参数: {}", segment.sql(), segment.params());
                }
            }
        });
        return new Tuple(lastWheres, paramMap);
    }

    static <R> String paginate(IPage<R> page, GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (CharSequenceUtil.isNotBlank(dbQueryParamInnerDto.getRawSQL())) {
            return validateRawSqlStrict(dbQueryParamInnerDto.getRawSQL());
        }
        return findByCondition(dbQueryParamInnerDto);
    }

    static String findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return findByCondition(dbQueryParamInnerDto);
    }

    static Map<String, Object> handleSQLCondition(SQL sql, List<GXCondition<?>> conditions) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL object must not be null");
        }
        Map<String, Object> paramMap = new HashMap<>();
        if (Objects.isNull(conditions) || conditions.isEmpty()) {
            LOGGER.debug("WHERE conditions are empty, returning empty param map.");
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

    static String deleteSoftCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXUpdateField<?>> updateFieldList) {
        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();
        String tableName = dbQueryParamInnerDto.getTableName();
        Dict extraData = Optional.ofNullable(Convert.convert(Dict.class, dbQueryParamInnerDto.getExtraData())).orElse(Dict.create());
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        TableInfo tableInfo = getTableInfoSafely(tableName);
        if (Objects.isNull(tableInfo)) {
            throw new GXBusinessException(CharSequenceUtil.format("Cannot resolve TableInfo for table [{}]", tableName));
        }
        String logicDeletedSetSql = buildLogicDeletedSetSql(tableInfo);
        if (CharSequenceUtil.isBlank(logicDeletedSetSql)) {
            throw new GXBusinessException("No available logical-delete column configured (TableLogic/is_deleted)");
        }
        SQL sql = new SQL().UPDATE(tableName);
        sql.SET(logicDeletedSetSql);
        if (hasColumn(tableName, "deleted_at")) {
            sql.SET(CharSequenceUtil.format("deleted_at = {}", DateUtil.currentSeconds()));
        }
        if (CollUtil.isNotEmpty(updateFieldList)) {
            for (GXUpdateField<?> field : updateFieldList) {
                sql.SET(field.updateString());
            }
        }
        if (CharSequenceUtil.isNotBlank(extraData.getStr("deletedBy")) && hasColumn(tableName, "deleted_by")) {
            String deletedByParamName = CharSequenceUtil.format("deleted_by_{}", UUID.randomUUID().toString().replace("-", ""));
            sql.SET(CharSequenceUtil.format("deleted_by = #{{dbQueryParamInnerDto.paramMap.{}}}", deletedByParamName));
            dbQueryParamInnerDto.getParamMap().put(deletedByParamName, extraData.getStr("deletedBy"));
        }
        Map<String, Object> paramMap = handleSQLCondition(sql, condition);
        dbQueryParamInnerDto.getParamMap().putAll(paramMap);
        String logicNotDeletedCondition = buildLogicNotDeletedCondition(tableName, tableName, condition);
        if (CharSequenceUtil.isNotBlank(logicNotDeletedCondition)) {
            sql.WHERE(logicNotDeletedCondition);
        }
        return sql.toString();
    }

    static String deleteCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();
        String tableName = dbQueryParamInnerDto.getTableName();
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        SQL sql = new SQL().DELETE_FROM(tableName);
        Map<String, Object> paramMap = handleSQLCondition(sql, condition);
        dbQueryParamInnerDto.getParamMap().putAll(paramMap);
        String logicNotDeletedCondition = buildLogicNotDeletedCondition(tableName, tableName, condition);
        if (CharSequenceUtil.isNotBlank(logicNotDeletedCondition)) {
            sql.WHERE(logicNotDeletedCondition);
        }
        return sql.toString();
    }

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
                if (!"tmp".equalsIgnoreCase(Optional.ofNullable(condition.getTableNameAlias()).orElse(""))) {
                    condition.setTableNameAlias("tmp");
                }
            });
        }
        return GXBaseBuilder.findByCondition(dbQueryParamInnerDto);
    }

    static String unionFindOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        unionQueryParamInnerDtoLst.forEach(queryParamInnerDto -> {
            String tableNameAlias = queryParamInnerDto.getTableNameAlias();
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                queryParamInnerDto.setTableNameAlias(queryParamInnerDto.getTableName());
            }
        });
        return unionFindByCondition(dbQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    static <R> String unionPaginate(IPage<R> page, GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (CharSequenceUtil.isNotBlank(dbQueryParamInnerDto.getRawSQL())) {
            return validateRawSqlStrict(dbQueryParamInnerDto.getRawSQL());
        }
        return unionFindByCondition(dbQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    private static String buildLogicNotDeletedCondition(String tableName, String tableAliasName, List<GXCondition<?>> conditions) {
        if (CollUtil.contains(conditions, c -> GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass()))) {
            return null;
        }
        if (CharSequenceUtil.isBlank(tableName) || tableName.trim().startsWith("(")) {
            return null;
        }
        String alias = CharSequenceUtil.isBlank(tableAliasName) ? tableName : tableAliasName;
        TableInfo tableInfo = getTableInfoSafely(tableName);
        String logicColumn = null;
        String logicNotDeletedValue = null;
        Optional<TableFieldInfo> logicFieldInfo = getLogicDeleteFieldInfo(tableInfo);
        if (logicFieldInfo.isPresent()) {
            logicColumn = logicFieldInfo.get().getColumn();
            logicNotDeletedValue = getLogicNotDeleteValue(tableInfo);
        }
        if (CharSequenceUtil.isBlank(logicColumn) && hasColumn(tableName, "is_deleted")) {
            logicColumn = "is_deleted";
            logicNotDeletedValue = "0";
        }
        if (CharSequenceUtil.isBlank(logicColumn)) {
            return null;
        }
        String literal = toSqlLiteral(logicNotDeletedValue);
        if (literal == null) {
            return CharSequenceUtil.format("{}.{} IS NULL", alias, logicColumn);
        }
        return CharSequenceUtil.format("{}.{} = {}", alias, logicColumn, literal);
    }

    private static String buildLogicDeletedSetSql(TableInfo tableInfo) {
        if (Objects.isNull(tableInfo)) {
            return null;
        }
        Optional<TableFieldInfo> logicFieldInfo = getLogicDeleteFieldInfo(tableInfo);
        String logicColumn = logicFieldInfo.map(TableFieldInfo::getColumn).orElse(null);
        String logicDeletedValue = getLogicDeleteValue(tableInfo);
        if (CharSequenceUtil.isBlank(logicColumn)) {
            if (tableInfo.getFieldList().stream().anyMatch(f -> CharSequenceUtil.equalsIgnoreCase("is_deleted", f.getColumn()))) {
                logicColumn = "is_deleted";
                logicDeletedValue = "1";
            } else {
                return null;
            }
        }
        String literal = toSqlLiteral(logicDeletedValue);
        return CharSequenceUtil.format("{} = {}", logicColumn, Optional.ofNullable(literal).orElse("NULL"));
    }

    private static Optional<TableFieldInfo> getLogicDeleteFieldInfo(TableInfo tableInfo) {
        if (Objects.isNull(tableInfo)) {
            return Optional.empty();
        }
        try {
            Method method = tableInfo.getClass().getMethod("getLogicDeleteFieldInfo");
            Object result = method.invoke(tableInfo);
            if (result instanceof TableFieldInfo info) {
                return Optional.of(info);
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private static String getLogicDeleteValue(TableInfo tableInfo) {
        if (Objects.isNull(tableInfo)) {
            return null;
        }
        try {
            Method method = tableInfo.getClass().getMethod("getLogicDeleteValue");
            Object result = method.invoke(tableInfo);
            return Objects.toString(result, null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getLogicNotDeleteValue(TableInfo tableInfo) {
        if (Objects.isNull(tableInfo)) {
            return null;
        }
        try {
            Method method = tableInfo.getClass().getMethod("getLogicNotDeleteValue");
            Object result = method.invoke(tableInfo);
            return Objects.toString(result, null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String toSqlLiteral(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        if (value.startsWith("'") && value.endsWith("'")) {
            return value;
        }
        if (NUMERIC_PATTERN.matcher(value).matches()) {
            return value;
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return value.toLowerCase(Locale.ROOT);
        }
        if (value.contains("(") && value.contains(")")) {
            return value;
        }
        return "'" + value.replace("'", "''") + "'";
    }

    private static boolean hasColumn(String tableName, String columnName) {
        TableInfo tableInfo = getTableInfoSafely(tableName);
        if (Objects.isNull(tableInfo) || CharSequenceUtil.isBlank(columnName)) {
            return false;
        }
        String keyColumn = tableInfo.getKeyColumn();
        if (CharSequenceUtil.isNotBlank(keyColumn) && CharSequenceUtil.equalsIgnoreCase(keyColumn, columnName)) {
            return true;
        }
        return tableInfo.getFieldList().stream().anyMatch(fieldInfo -> CharSequenceUtil.equalsIgnoreCase(fieldInfo.getColumn(), columnName));
    }

    private static TableInfo getTableInfoSafely(String tableName) {
        if (CharSequenceUtil.isBlank(tableName) || tableName.trim().startsWith("(")) {
            return null;
        }
        try {
            return TableInfoHelper.getTableInfo(tableName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static GXConditionSegment renderCondition(GXCondition<?> condition) {
        if (condition instanceof GXConditionJsonEQ jsonEq) {
            return renderJsonEqCondition(jsonEq);
        }
        return condition.toSegment();
    }

    private static GXConditionSegment renderJsonEqCondition(GXConditionJsonEQ condition) {
        String dbType = resolveDbTypeFromContext().toLowerCase(Locale.ROOT);
        String field = qualifyConditionField(condition.getTableNameAlias(), condition.getFieldExpression());
        String pathParam = CharSequenceUtil.format("#{{dbQueryParamInnerDto.paramMap.{}}}", condition.getParamName() + "_path");
        String valueParam = CharSequenceUtil.format("#{{dbQueryParamInnerDto.paramMap.{}}}", condition.getParamName());
        String normalizedPathExpr = CharSequenceUtil.format("replace({}, '$.', '')", pathParam);
        String sql = renderJsonEqByDialect(dbType, field, pathParam, valueParam, normalizedPathExpr);
        Map<String, Object> params = new HashMap<>();
        params.put(condition.getParamName(), condition.getValue());
        params.put(condition.getParamName() + "_path", condition.getJsonPath());
        return new GXConditionSegment(sql, params);
    }

    private static String renderJsonEqByDialect(String dbType, String field, String pathParam, String valueParam, String normalizedPathExpr) {
        if (CollUtil.contains(Arrays.asList("mysql", "mariadb", "h2", "sqlite"), dbType)) {
            return CharSequenceUtil.format("JSON_EXTRACT({}, {}) = {}", field, pathParam, valueParam);
        }
        if (CollUtil.contains(Arrays.asList("postgres", "postgresql", "postgre_sql"), dbType)) {
            return CharSequenceUtil.format("CAST({} AS jsonb) #>> string_to_array({}, '.') = CAST({} AS text)", field, normalizedPathExpr, valueParam);
        }
        if (CollUtil.contains(Arrays.asList("sqlserver", "sql_server", "mssql", "sql-server"), dbType)) {
            return CharSequenceUtil.format("JSON_VALUE({}, {}) = CAST({} AS NVARCHAR)", field, pathParam, valueParam);
        }
        return CharSequenceUtil.format("JSON_VALUE({}, {}) = CAST({} AS VARCHAR)", field, pathParam, valueParam);
    }

    private static String qualifyConditionField(String tableAlias, String fieldExpression) {
        if (SAFE_IDENTIFIER_PATTERN.matcher(fieldExpression).matches()) {
            if (CharSequenceUtil.isBlank(tableAlias) || fieldExpression.contains(".")) {
                return fieldExpression;
            }
            return CharSequenceUtil.format("{}.{}", tableAlias, fieldExpression);
        }
        throw new GXDBConditionException(CharSequenceUtil.format("非法字段表达式: {}", fieldExpression));
    }

    private static Set<String> buildAllowedColumns(String tableName, String tableAlias, List<GXJoinDto> joins) {
        Set<String> allowedColumns = new HashSet<>();
        ensureWhitelistCoverage(allowedColumns, tableName, tableAlias, "MAIN");
        if (CollUtil.isNotEmpty(joins)) {
            for (GXJoinDto join : joins) {
                ensureWhitelistCoverage(allowedColumns, join.getJoinTableName(), join.getJoinTableNameAlias(), "JOIN");
            }
        }
        return allowedColumns;
    }

    private static void ensureWhitelistCoverage(Set<String> allowedColumns, String tableName, String tableAlias, String scope) {
        if (CharSequenceUtil.isBlank(tableName) || tableName.trim().startsWith("(")) {
            return;
        }
        boolean loaded = collectAllowedColumns(allowedColumns, tableName, tableAlias);
        if (!loaded) {
            throw new GXDBConditionException(CharSequenceUtil.format("{} table [{}] has no TableInfo metadata, cannot build whitelist", scope, tableName));
        }
    }

    private static boolean collectAllowedColumns(Set<String> allowedColumns, String tableName, String tableAlias) {
        TableInfo tableInfo = getTableInfoSafely(tableName);
        if (Objects.isNull(tableInfo)) {
            return false;
        }
        String alias = CharSequenceUtil.isBlank(tableAlias) ? tableName : tableAlias;
        List<String> columns = new ArrayList<>();
        if (CharSequenceUtil.isNotBlank(tableInfo.getKeyColumn())) {
            columns.add(CharSequenceUtil.toUnderlineCase(tableInfo.getKeyColumn()));
        }
        tableInfo.getFieldList().forEach(fieldInfo -> {
            if (CharSequenceUtil.isNotBlank(fieldInfo.getColumn())) {
                columns.add(CharSequenceUtil.toUnderlineCase(fieldInfo.getColumn()));
            }
            if (CharSequenceUtil.isNotBlank(fieldInfo.getProperty())) {
                columns.add(CharSequenceUtil.toUnderlineCase(fieldInfo.getProperty()));
            }
        });
        for (String column : columns) {
            allowedColumns.add(column.toLowerCase(Locale.ROOT));
            allowedColumns.add(CharSequenceUtil.format("{}.{}", alias, column).toLowerCase(Locale.ROOT));
        }
        return true;
    }

    private static String sanitizeSelectColumn(String column, Set<String> allowedColumns) {
        String trimmed = CharSequenceUtil.trim(column);
        if ("1".equals(trimmed)) {
            return "1";
        }
        return sanitizeStructuralColumn(trimmed, allowedColumns, "SELECT", false);
    }

    private static String sanitizeStructuralColumn(String column, Set<String> allowedColumns, String clauseName, boolean whitelistRequired) {
        String trimmed = CharSequenceUtil.trim(column);
        if (CharSequenceUtil.isBlank(trimmed) || !SAFE_IDENTIFIER_PATTERN.matcher(trimmed).matches()) {
            throw new GXDBConditionException(CharSequenceUtil.format("{} field is invalid: {}", clauseName, column));
        }
        if (GXDBStringEscapeUtils.check(trimmed)) {
            throw new GXSqlInjectionException(CharSequenceUtil.format("{} field has SQL injection risk: {}", clauseName, column));
        }
        if (whitelistRequired && allowedColumns.isEmpty()) {
            throw new GXDBConditionException(CharSequenceUtil.format("{} column whitelist is unavailable", clauseName));
        }
        if (!allowedColumns.isEmpty() && !allowedColumns.contains(trimmed.toLowerCase(Locale.ROOT))) {
            throw new GXDBConditionException(CharSequenceUtil.format("{} field is not in whitelist: {}", clauseName, column));
        }
        return trimmed;
    }

    private static String sanitizeOrderBy(String column, String direction, Set<String> allowedColumns) {
        String safeColumn = sanitizeStructuralColumn(column, allowedColumns, "ORDER BY", true);
        String safeDirection = Optional.ofNullable(direction).map(CharSequenceUtil::trim).orElse("").toUpperCase(Locale.ROOT);
        if (!"ASC".equals(safeDirection) && !"DESC".equals(safeDirection)) {
            throw new GXDBConditionException(CharSequenceUtil.format("ORDER BY direction is invalid: {}", direction));
        }
        return CharSequenceUtil.format("{} {}", safeColumn, safeDirection);
    }

    private static String sanitizeHavingClause(String clause, Set<String> allowedColumns) {
        String trimmed = CharSequenceUtil.trim(clause);
        if (CharSequenceUtil.isBlank(trimmed)) {
            throw new GXDBConditionException("HAVING clause must not be blank");
        }
        if (allowedColumns.isEmpty()) {
            throw new GXDBConditionException("HAVING column whitelist is unavailable");
        }
        if (GXDBStringEscapeUtils.check(trimmed) || trimmed.contains(";") || trimmed.contains("--") || trimmed.contains("/*")) {
            throw new GXSqlInjectionException(CharSequenceUtil.format("HAVING clause has SQL injection risk: {}", clause));
        }
        if (DANGEROUS_SQL_TOKEN_PATTERN.matcher(trimmed).find()) {
            throw new GXSqlInjectionException(CharSequenceUtil.format("HAVING clause contains dangerous tokens: {}", clause));
        }

        java.util.regex.Matcher matcher = HAVING_TOKEN_PATTERN.matcher(trimmed);
        while (matcher.find()) {
            String token = matcher.group();
            String upper = token.toUpperCase(Locale.ROOT);
            if (HAVING_KEYWORD_WHITELIST.contains(upper)) {
                continue;
            }
            if (allowedColumns.contains(token.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (isFunctionToken(trimmed, matcher.end(), token)) {
                continue;
            }
            throw new GXDBConditionException(CharSequenceUtil.format("HAVING token is not in whitelist: {}", token));
        }
        return trimmed;
    }

    private static boolean isFunctionToken(String clause, int tokenEndIndex, String token) {
        if (!SAFE_IDENTIFIER_PATTERN.matcher(token).matches()) {
            return false;
        }
        int index = tokenEndIndex;
        while (index < clause.length() && Character.isWhitespace(clause.charAt(index))) {
            index++;
        }
        if (index >= clause.length() || clause.charAt(index) != '(') {
            return false;
        }
        String upper = token.toUpperCase(Locale.ROOT);
        if (HAVING_FUNCTION_WHITELIST.contains(upper)) {
            return true;
        }
        return !DANGEROUS_SQL_TOKEN_PATTERN.matcher(token).find();
    }

    private static String validateRawSqlStrict(String rawSQL) {
        if (CharSequenceUtil.isBlank(rawSQL)) {
            throw new GXSqlInjectionException("Raw SQL must not be blank");
        }
        String normalized = rawSQL.trim();
        if (GXDBStringEscapeUtils.check(normalized)) {
            throw new GXSqlInjectionException("SQL injection risk detected, raw SQL is blocked");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains(";") || lower.contains("--") || lower.contains("/*") || lower.contains("*/")) {
            throw new GXSqlInjectionException("Raw SQL contains illegal control symbols");
        }
        if (!(lower.startsWith("select") || lower.startsWith("with"))) {
            throw new GXSqlInjectionException("Raw SQL only allows SELECT/WITH queries");
        }
        if (DANGEROUS_SQL_TOKEN_PATTERN.matcher(normalized).find()) {
            throw new GXSqlInjectionException("Raw SQL contains dangerous keywords");
        }
        return normalized;
    }

    private static String resolveDbTypeFromContext() {
        try {
            GXDataSourceProperties properties = GXSpringContextUtils.getBean(GXDataSourceProperties.class);
            if (Objects.nonNull(properties) && CharSequenceUtil.isNotBlank(properties.getDbType())) {
                return properties.getDbType();
            }
        } catch (Exception ignored) {
        }
        return "mysql";
    }
}
