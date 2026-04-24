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

import java.util.*;
import java.util.regex.Matcher;
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
    Set<String> MYSQL_LIKE_DIALECTS = Set.of("mysql", "mariadb", "h2", "sqlite");
    Set<String> POSTGRES_DIALECTS = Set.of("postgres", "postgresql", "postgre_sql");
    Set<String> SQLSERVER_DIALECTS = Set.of("sqlserver", "sql_server", "mssql", "sql-server");
    Set<String> ORACLE_DIALECTS = Set.of("oracle");
    Pattern SQL_FUNCTION_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*\\s*\\(.*\\)$", Pattern.DOTALL);

    Set<String> SQL_FUNCTION_KEYWORDS = Set.of(
            "ifnull", "isnull", "coalesce", "nullif",
            "sum", "count", "avg", "min", "max",
            "upper", "lower", "trim", "length", "concat", "substring", "replace",
            "cast", "convert", "round", "floor", "ceil", "ceiling", "abs",
            "date", "year", "month", "day", "now", "unix_timestamp", "curdate",
            "group_concat", "json_extract", "json_value",
            "case", "when", "then", "else", "end",
            "as", "asc", "desc", "and", "or", "not", "in", "is", "null",
            "distinct", "over", "partition", "by", "order",
            "varchar", "nvarchar", "int", "bigint", "decimal", "char", "text"
    );

    Pattern IDENTIFIER_IN_EXPR_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)?");

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
        String tableName = dbQueryParamInnerDto.getTableName();
        String tableNameAlias = Optional.ofNullable(dbQueryParamInnerDto.getTableNameAlias()).orElse(tableName);
        List<GXJoinDto> joins = dbQueryParamInnerDto.getJoins();
        Set<String> selectColumns = dbQueryParamInnerDto.getColumns();
        Set<String> groupByField = dbQueryParamInnerDto.getGroupByField();
        Map<String, String> orderByField = dbQueryParamInnerDto.getOrderByField();
        Set<String> having = dbQueryParamInnerDto.getHaving();
        List<GXCondition<?>> conditions = dbQueryParamInnerDto.getCondition();
        Set<String> allowedColumns = buildAllowedColumns(tableName, tableNameAlias, joins);
        Set<String> columns = CollUtil.newHashSet();
        if (CollUtil.isNotEmpty(selectColumns)) {
            columns = selectColumns.stream().map(CharSequenceUtil::toUnderlineCase).collect(Collectors.toSet());
        }
        String selectStr;
        if (CollUtil.isNotEmpty(columns)) {
            selectStr = columns.stream()
                    .map(column -> sanitizeSelectColumn(column, allowedColumns))
                    .collect(Collectors.joining(","));
        } else {
            selectStr = CharSequenceUtil.format("{}.*", tableNameAlias);
        }
        SQL sql = new SQL()
                .SELECT(selectStr)
                .FROM(CharSequenceUtil.format("{} {}", tableName, tableNameAlias));
        Map<String, Object> mergedParamMap = new HashMap<>();
        if (CollUtil.isNotEmpty(joins)) {
            mergedParamMap.putAll(handleSQLJoin(sql, joins));
        }
        mergedParamMap.putAll(handleSQLCondition(sql, conditions));
        String logicNotDeletedCondition = buildLogicNotDeletedCondition(tableName, tableNameAlias, conditions);
        if (CharSequenceUtil.isNotBlank(logicNotDeletedCondition)) {
            sql.WHERE(logicNotDeletedCondition);
        }
        dbQueryParamInnerDto.getParamMap().putAll(mergedParamMap);
        if (CollUtil.isNotEmpty(groupByField)) {
            String[] groupByColumns = groupByField.stream()
                    .map(column -> sanitizeStructuralColumn(column, allowedColumns, "GROUP BY", true))
                    .toArray(String[]::new);
            sql.GROUP_BY(groupByColumns);
        }
        if (CollUtil.isNotEmpty(having)) {
            String[] havingClauses = having.stream()
                    .map(clause -> sanitizeHavingClause(clause, allowedColumns))
                    .toArray(String[]::new);
            sql.HAVING(havingClauses);
        }
        if (CollUtil.isNotEmpty(orderByField)) {
            String[] orderColumns = orderByField.entrySet().stream()
                    .map(entry -> sanitizeOrderBy(entry.getKey(), entry.getValue(), allowedColumns))
                    .toArray(String[]::new);
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
            if (CharSequenceUtil.isBlank(masterTableNameAlias)) {
                masterTableNameAlias = masterTableName;
            }
            if (CharSequenceUtil.isBlank(tableAliasName)) {
                tableAliasName = tableName;
            }
            List<GXDbJoinOp> andOps = Optional.ofNullable(join.getAnd()).orElse(Collections.emptyList());
            for (GXDbJoinOp op : andOps) {
                if (CharSequenceUtil.isBlank(op.getMasterTableNameAlias())) {
                    op.setMasterTableNameAlias(masterTableNameAlias);
                }
                if (CharSequenceUtil.isBlank(op.getJoinTableNameAlias())) {
                    op.setJoinTableNameAlias(tableAliasName);
                }
            }
            List<GXDbJoinOp> orOps = Optional.ofNullable(join.getOr()).orElse(Collections.emptyList());
            for (GXDbJoinOp op : orOps) {
                if (CharSequenceUtil.isBlank(op.getMasterTableNameAlias())) {
                    op.setMasterTableNameAlias(masterTableNameAlias);
                }
                if (CharSequenceUtil.isBlank(op.getJoinTableNameAlias())) {
                    op.setJoinTableNameAlias(tableAliasName);
                }
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
            String andClause = andOps
                    .stream()
                    .map(GXDbJoinOp::opString)
                    .collect(Collectors.joining(GXBuilderConstant.AND_OP));
            String orClause = orOps
                    .stream()
                    .map(GXDbJoinOp::opString)
                    .collect(Collectors.joining(GXBuilderConstant.OR_OP));
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
        if (CollUtil.isNotEmpty(updateFieldList)) {
            for (GXUpdateField<?> field : updateFieldList) {
                sql.SET(field.updateString());
            }
        }
        if (CharSequenceUtil.isNotBlank(extraData.getStr("deletedBy")) && hasColumn(tableName, "deleted_by")) {
            String deletedByParamName = CharSequenceUtil.format("deleted_by_{}", UUID.randomUUID().toString().substring(0, 8));
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
        String trimmedName = CharSequenceUtil.trimToEmpty(tableName);
        if (trimmedName.isEmpty() || trimmedName.startsWith("(")) {
            return null;
        }
        String alias = CharSequenceUtil.isBlank(tableAliasName) ? tableName : tableAliasName;
        String logicColumn = null;
        String logicNotDeletedValue = null;
        TableInfo tableInfo = getTableInfoSafely(tableName);
        if (tableInfo != null) {
            Optional<TableFieldInfo> logicFieldInfo = getLogicDeleteFieldInfo(tableInfo);
            if (logicFieldInfo.isPresent()) {
                logicColumn = logicFieldInfo.get().getColumn();
                logicNotDeletedValue = getLogicNotDeleteValue(tableInfo);
            }
        }
        if (CharSequenceUtil.isBlank(logicColumn) && hasColumn(tableName, "is_deleted")) {
            logicColumn = "is_deleted";
            logicNotDeletedValue = "0";
            LOGGER.warn("Table [{}] has no @TableLogic configuration, fallback to logical-delete condition [{}.{} = {}].",
                    tableName, alias, logicColumn, logicNotDeletedValue);
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
        String logicColumn = null;
        String logicDeletedValue = null;
        Optional<TableFieldInfo> logicFieldInfo = getLogicDeleteFieldInfo(tableInfo);
        if (logicFieldInfo.isPresent()) {
            logicColumn = logicFieldInfo.get().getColumn();
            logicDeletedValue = getLogicDeleteValue(tableInfo);
        }
        if (CharSequenceUtil.isBlank(logicColumn)) {
            boolean hasIsDeleted = tableInfo.getFieldList().stream()
                    .anyMatch(f -> CharSequenceUtil.equalsIgnoreCase("is_deleted", f.getColumn()));
            if (!hasIsDeleted) {
                return null;
            }
            String keyColumn = tableInfo.getKeyColumn();
            if (CharSequenceUtil.isBlank(keyColumn)) {
                LOGGER.warn("Table [{}] fallback logical-delete field [is_deleted] requires primary key column, but got null.", tableInfo.getTableName());
                logicColumn = "is_deleted";
                logicDeletedValue = "1";
            } else {
                if (!GXBaseBuilder.SAFE_IDENTIFIER_PATTERN.matcher(keyColumn).matches()) {
                    LOGGER.warn("Table [{}] primary key column [{}] is not a safe identifier, fallback to '1'.", tableInfo.getTableName(), keyColumn);
                    logicColumn = "is_deleted";
                    logicDeletedValue = "1";
                } else {
                    logicColumn = "is_deleted";
                    logicDeletedValue = keyColumn;
                }
            }
        }
        String deletedValueSql;
        deletedValueSql = logicDeletedValue;
        String logicDeletedSetSql = CharSequenceUtil.format("{} = {}", logicColumn, deletedValueSql);
        boolean hasDeletedAt = tableInfo.getFieldList().stream()
                .anyMatch(f -> CharSequenceUtil.equalsIgnoreCase("deleted_at", f.getColumn()));
        if (hasDeletedAt) {
            long currentTimestamp = System.currentTimeMillis() / 1000L;
            logicDeletedSetSql = CharSequenceUtil.format("{}, deleted_at = {}", logicDeletedSetSql, currentTimestamp);
        }
        return logicDeletedSetSql;
    }

    private static Optional<TableFieldInfo> getLogicDeleteFieldInfo(TableInfo tableInfo) {
        if (Objects.isNull(tableInfo)) {
            return Optional.empty();
        }
        TableFieldInfo fieldInfo = tableInfo.getLogicDeleteFieldInfo();
        return Optional.ofNullable(fieldInfo);
    }

    private static String getLogicDeleteValue(TableInfo tableInfo) {
        if (Objects.isNull(tableInfo)) {
            return null;
        }
        TableFieldInfo logicDeleteFieldInfo = tableInfo.getLogicDeleteFieldInfo();
        if (Objects.isNull(logicDeleteFieldInfo)) {
            return null;
        }
        return logicDeleteFieldInfo.getLogicDeleteValue();
    }

    private static String getLogicNotDeleteValue(TableInfo tableInfo) {
        if (Objects.isNull(tableInfo)) {
            return null;
        }
        TableFieldInfo logicDeleteFieldInfo = tableInfo.getLogicDeleteFieldInfo();
        if (Objects.isNull(logicDeleteFieldInfo)) {
            return null;
        }
        return logicDeleteFieldInfo.getLogicNotDeleteValue();
    }

    private static String toSqlLiteral(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
            return value;
        }
        if (NUMERIC_PATTERN.matcher(value).matches()) {
            return value;
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return value.toLowerCase(Locale.ROOT);
        }
        if (SQL_FUNCTION_PATTERN.matcher(value).matches()) {
            return value;
        }
        return "'" + value.replace("'", "''") + "'";
    }

    private static boolean hasColumn(String tableName, String columnName) {
        if (CharSequenceUtil.isBlank(tableName) || CharSequenceUtil.isBlank(columnName)) {
            return false;
        }
        TableInfo tableInfo = getTableInfoSafely(tableName);
        if (Objects.isNull(tableInfo)) {
            return false;
        }
        String keyColumn = tableInfo.getKeyColumn();
        if (CharSequenceUtil.isNotBlank(keyColumn) && CharSequenceUtil.equalsIgnoreCase(keyColumn, columnName)) {
            return true;
        }
        return tableInfo.getFieldList().stream()
                .anyMatch(fieldInfo -> CharSequenceUtil.equalsIgnoreCase(fieldInfo.getColumn(), columnName));
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
        String paramName = condition.getParamName();
        String pathParamName = paramName + "_path";
        String pathParam = CharSequenceUtil.format("#{{dbQueryParamInnerDto.paramMap.{}}}", pathParamName);
        String valueParam = CharSequenceUtil.format("#{{dbQueryParamInnerDto.paramMap.{}}}", paramName);
        String sql = renderJsonEqByDialect(dbType, field, pathParam, valueParam);
        Map<String, Object> params = new HashMap<>();
        params.put(paramName, condition.getValue());
        params.put(pathParamName, condition.getJsonPath());
        return new GXConditionSegment(sql, params);
    }

    private static String renderJsonEqByDialect(String dbType, String field, String pathParam, String valueParam) {
        if (MYSQL_LIKE_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("JSON_EXTRACT({}, {}) = {}", field, pathParam, valueParam);
        }
        if (POSTGRES_DIALECTS.contains(dbType)) {
            String normalizedPathExpr = CharSequenceUtil.format("string_to_array(replace({}, '$.', ''), '.')", pathParam);
            return CharSequenceUtil.format("CAST({} AS jsonb) #>> {} = CAST({} AS text)", field, normalizedPathExpr, valueParam);
        }
        if (SQLSERVER_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("JSON_VALUE({}, {}) = CAST({} AS NVARCHAR(MAX))", field, pathParam, valueParam);
        }
        if (ORACLE_DIALECTS.contains(dbType)) {
            return CharSequenceUtil.format("JSON_VALUE({}, {}) = CAST({} AS VARCHAR2(4000))", field, pathParam, valueParam);
        }
        LOGGER.warn("Unrecognized dbType [{}] for JSON_EQ condition, falling back to standard JSON_VALUE syntax.", dbType);
        return CharSequenceUtil.format("JSON_VALUE({}, {}) = CAST({} AS VARCHAR(4000))", field, pathParam, valueParam);
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
                String joinTableName = join.getJoinTableName();
                String joinTableAlias = join.getJoinTableNameAlias();
                if (CharSequenceUtil.isNotBlank(joinTableName)) {
                    ensureWhitelistCoverage(allowedColumns, joinTableName, joinTableAlias, "JOIN");
                } else {
                    LOGGER.warn("JOIN table name is blank, skipping whitelist coverage for alias [{}]", joinTableAlias);
                }
                String masterTableName = join.getMasterTableName();
                String masterTableAlias = join.getMasterTableNameAlias();
                if (CharSequenceUtil.isNotBlank(masterTableName)
                        && !CharSequenceUtil.equalsIgnoreCase(masterTableName, tableName)) {
                    ensureWhitelistCoverage(allowedColumns, masterTableName, masterTableAlias, "JOIN_MASTER");
                }
            }
        }
        if (allowedColumns.isEmpty()) {
            LOGGER.warn("No allowed columns were resolved for table [{}], whitelist will be empty.", tableName);
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
        String keyColumn = tableInfo.getKeyColumn();
        if (CharSequenceUtil.isNotBlank(keyColumn)) {
            allowedColumns.add(keyColumn.toLowerCase(Locale.ROOT));
        }
        tableInfo.getFieldList().forEach(fieldInfo -> {
            String column = fieldInfo.getColumn();
            if (CharSequenceUtil.isNotBlank(column)) {
                allowedColumns.add(column.toLowerCase(Locale.ROOT));
            }
        });

        return true;
    }

    private static String sanitizeSelectColumn(String column, Set<String> allowedColumns) {
        String trimmed = CharSequenceUtil.trim(column);
        if ("1".equals(trimmed)) {
            return "1";
        }
        return sanitizeStructuralColumn(trimmed, allowedColumns, "SELECT", false);
    }

    /**
     * 判断是否为复合表达式：含括号（函数调用）或含 AS 别名
     */
    private static boolean isComplexExpression(String trimmed) {
        return trimmed.contains("(") || CharSequenceUtil.containsIgnoreCase(trimmed, " as ");
    }

    /**
     * 从复合表达式中提取列引用，校验是否在白名单内。
     * 跳过：SQL 函数/关键字、纯数值、AS 后的别名。
     */
    private static void validateColumnRefsInExpression(String expr, Set<String> allowedColumns, String clauseName) {
        String exprWithoutAlias = expr.replaceAll("(?i)\\s+as\\s+[A-Za-z_][A-Za-z0-9_]*\\s*$", "").trim();
        Matcher matcher = IDENTIFIER_IN_EXPR_PATTERN.matcher(exprWithoutAlias);
        while (matcher.find()) {
            String token = matcher.group();
            String columnPart = token.toLowerCase(Locale.ROOT);
            int dotIndex = columnPart.lastIndexOf('.');
            if (dotIndex >= 0) {
                columnPart = columnPart.substring(dotIndex + 1);
            }
            if (SQL_FUNCTION_KEYWORDS.contains(columnPart)) {
                continue;
            }
            if (columnPart.matches("\\d+")) {
                continue;
            }
            if (!allowedColumns.contains(columnPart)) {
                throw new GXDBConditionException(
                        CharSequenceUtil.format("{} expression references column [{}] which is not in whitelist: {}",
                                clauseName, token, expr));
            }
        }
    }

    private static String sanitizeStructuralColumn(String column, Set<String> allowedColumns, String clauseName, boolean whitelistRequired) {
        String trimmed = CharSequenceUtil.trim(column);
        if ("SELECT".equalsIgnoreCase(clauseName) && CharSequenceUtil.containsAny(trimmed, "*")) {
            LOGGER.error("SELECT field '*' is allowed for compatibility, please prefer explicit columns when possible.");
            return trimmed;
        }
        if (CharSequenceUtil.isBlank(trimmed)) {
            throw new GXDBConditionException(CharSequenceUtil.format("{} field is blank", clauseName));
        }
        if (isComplexExpression(trimmed)) {
            if (GXDBStringEscapeUtils.check(trimmed)) {
                throw new GXSqlInjectionException(
                        CharSequenceUtil.format("{} field has SQL injection risk: {}", clauseName, column));
            }
            if (whitelistRequired && allowedColumns.isEmpty()) {
                throw new GXDBConditionException(
                        CharSequenceUtil.format("{} column whitelist is unavailable", clauseName));
            }
            if (!allowedColumns.isEmpty()) {
                validateColumnRefsInExpression(trimmed, allowedColumns, clauseName);
            }
        } else {
            if (!SAFE_IDENTIFIER_PATTERN.matcher(trimmed).matches()) {
                throw new GXDBConditionException(
                        CharSequenceUtil.format("{} field is invalid: {}", clauseName, column));
            }
            if (GXDBStringEscapeUtils.check(trimmed)) {
                throw new GXSqlInjectionException(
                        CharSequenceUtil.format("{} field has SQL injection risk: {}", clauseName, column));
            }
            if (whitelistRequired && allowedColumns.isEmpty()) {
                throw new GXDBConditionException(
                        CharSequenceUtil.format("{} column whitelist is unavailable", clauseName));
            }
            String columnToCheck = trimmed.toLowerCase(Locale.ROOT);
            int dotIndex = columnToCheck.lastIndexOf('.');
            if (dotIndex >= 0) {
                columnToCheck = columnToCheck.substring(dotIndex + 1);
            }
            if (!allowedColumns.isEmpty() && !allowedColumns.contains(columnToCheck)) {
                throw new GXDBConditionException(
                        CharSequenceUtil.format("{} field is not in whitelist: {}", clauseName, column));
            }
        }
        return trimmed;
    }

    private static String sanitizeOrderBy(String column, String direction, Set<String> allowedColumns) {
        if (CharSequenceUtil.isBlank(column)) {
            throw new GXDBConditionException("ORDER BY column must not be blank");
        }
        if (!CollUtil.safeContains(allowedColumns, column)) {
            LOGGER.error("排序字段不在被允许的字段中！！已经实时将排序字段加入到了允许字段中！！");
        }
        CollUtil.addIfAbsent(allowedColumns, column);
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
        LOGGER.error("Unable to resolve database type, please configure 'dbType' in GXDataSourceProperties");
        return "mysql";
    }
}
