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
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionJsonEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionIsNotNULL;
import cn.maple.core.framework.dto.inner.condition.GXConditionIsNULL;
import cn.maple.core.framework.dto.inner.condition.GXConditionSegment;
import cn.maple.core.framework.dto.inner.condition.GXExclusionDeletedFieldCondition;
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

    static String updateFieldByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXUpdateField<?>> fieldList) {
        if (dbQueryParamInnerDto == null) {
            throw new GXBusinessException("鏌ヨ鍙傛暟瀵硅薄涓嶈兘涓虹┖!");
        }
        if (CollUtil.isEmpty(fieldList)) {
            throw new GXBusinessException("鏇存柊瀛楁鍒楄〃涓嶈兘涓虹┖!");
        }

        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();
        String tableName = dbQueryParamInnerDto.getTableName();
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("鏇存柊鏉′欢涓嶈兘涓虹┖锛屼负闃叉鍏ㄨ〃鏇存柊椋庨櫓!");
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
        LOGGER.debug("鐢熸垚鐨勬洿鏂癝QL: {}", resultSql);
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
                    .map(column -> sanitizeStructuralColumn(column, allowedColumns, "GROUP BY"))
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
            LOGGER.debug("条件为空，不添加任何条件");
            return new Tuple(new ArrayList<>(), paramMap);
        }

        List<String> lastWheres = new ArrayList<>();
        conditions.forEach(c -> {
            if (!GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass())) {
                if (Objects.isNull(c.getValue())
                        && !GXConditionIsNULL.class.isAssignableFrom(c.getClass())
                        && !GXConditionIsNotNULL.class.isAssignableFrom(c.getClass())) {
                    String msg = CharSequenceUtil.format("查询条件错误，字段{}.{}的值为null", c.getTableNameAlias(), c.getFieldExpression());
                    throw new GXDBConditionException(msg);
                }
                GXConditionSegment segment = renderCondition(c);
                if (Objects.nonNull(segment) && CharSequenceUtil.isNotEmpty(segment.getSql())) {
                    lastWheres.add(segment.getSql());
                    paramMap.putAll(segment.getParams());
                    LOGGER.trace("条件为空，不添加任何条件: {}, 参数: {}", segment.getSql(), segment.getParams());
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
            throw new IllegalArgumentException("SQL对象不能为空");
        }
        Map<String, Object> paramMap = new HashMap<>();
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

    static String deleteSoftCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXUpdateField<?>> updateFieldList) {
        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();
        String tableName = dbQueryParamInnerDto.getTableName();
        Dict extraData = Optional.ofNullable(Convert.convert(Dict.class, dbQueryParamInnerDto.getExtraData())).orElse(Dict.create());
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }

        TableInfo tableInfo = getTableInfoSafely(tableName);
        if (Objects.isNull(tableInfo)) {
            throw new GXBusinessException(CharSequenceUtil.format("未找到数据表{}对应的TableInfo信息，请检查表名或实体映射关系", tableName));
        }

        String logicDeletedSetSql = buildLogicDeletedSetSql(tableInfo);
        if (CharSequenceUtil.isBlank(logicDeletedSetSql)) {
            throw new GXBusinessException("未配置逻辑删除字段，请在实体中使用@TableLogic声明");
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
            // ignore
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
        String sql;

        if (CollUtil.contains(Arrays.asList("mysql", "mariadb", "h2", "sqlite"), dbType)) {
            sql = CharSequenceUtil.format("JSON_EXTRACT({}, {}) = {}", field, pathParam, valueParam);
        } else if (CollUtil.contains(Arrays.asList("postgres", "postgresql", "postgre_sql"), dbType)) {
            sql = CharSequenceUtil.format("CAST({} AS jsonb) #>> string_to_array({}, '.') = CAST({} AS text)", field, normalizedPathExpr, valueParam);
        } else if (CollUtil.contains(Arrays.asList("sqlserver", "sql_server", "mssql", "sql-server"), dbType)) {
            sql = CharSequenceUtil.format("JSON_VALUE({}, {}) = CAST({} AS NVARCHAR)", field, pathParam, valueParam);
        } else {
            sql = CharSequenceUtil.format("JSON_VALUE({}, {}) = CAST({} AS VARCHAR)", field, pathParam, valueParam);
        }

        Map<String, Object> params = new HashMap<>();
        params.put(condition.getParamName(), condition.getValue());
        params.put(condition.getParamName() + "_path", condition.getJsonPath());
        return new GXConditionSegment(sql, params);
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
        collectAllowedColumns(allowedColumns, tableName, tableAlias);
        if (CollUtil.isNotEmpty(joins)) {
            for (GXJoinDto join : joins) {
                collectAllowedColumns(allowedColumns, join.getJoinTableName(), join.getJoinTableNameAlias());
            }
        }
        return allowedColumns;
    }

    private static void collectAllowedColumns(Set<String> allowedColumns, String tableName, String tableAlias) {
        TableInfo tableInfo = getTableInfoSafely(tableName);
        if (Objects.isNull(tableInfo)) {
            return;
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
    }

    private static String sanitizeSelectColumn(String column, Set<String> allowedColumns) {
        String trimmed = CharSequenceUtil.trim(column);
        if ("1".equals(trimmed)) {
            return "1";
        }
        return sanitizeStructuralColumn(trimmed, allowedColumns, "SELECT");
    }

    private static String sanitizeStructuralColumn(String column, Set<String> allowedColumns, String clauseName) {
        String trimmed = CharSequenceUtil.trim(column);
        if (CharSequenceUtil.isBlank(trimmed) || !SAFE_IDENTIFIER_PATTERN.matcher(trimmed).matches()) {
            throw new GXDBConditionException(CharSequenceUtil.format("{} 字段不合法: {}", clauseName, column));
        }
        if (GXDBStringEscapeUtils.check(trimmed)) {
            throw new GXSqlInjectionException(CharSequenceUtil.format("{} 字段存在SQL注入风险: {}", clauseName, column));
        }
        if (!allowedColumns.isEmpty() && !allowedColumns.contains(trimmed.toLowerCase(Locale.ROOT))) {
            throw new GXDBConditionException(CharSequenceUtil.format("{} 字段不在白名单中: {}", clauseName, column));
        }
        return trimmed;
    }

    private static String sanitizeOrderBy(String column, String direction, Set<String> allowedColumns) {
        String safeColumn = sanitizeStructuralColumn(column, allowedColumns, "ORDER BY");
        String safeDirection = Optional.ofNullable(direction).map(CharSequenceUtil::trim).orElse("").toUpperCase(Locale.ROOT);
        if (!"ASC".equals(safeDirection) && !"DESC".equals(safeDirection)) {
            throw new GXDBConditionException(CharSequenceUtil.format("ORDER BY 排序方向不合法: {}", direction));
        }
        return CharSequenceUtil.format("{} {}", safeColumn, safeDirection);
    }

    private static String sanitizeHavingClause(String clause, Set<String> allowedColumns) {
        String trimmed = CharSequenceUtil.trim(clause);
        if (CharSequenceUtil.isBlank(trimmed)) {
            throw new GXDBConditionException("HAVING 子句不能为空");
        }
        if (GXDBStringEscapeUtils.check(trimmed) || trimmed.contains(";") || trimmed.contains("--") || trimmed.contains("/*")) {
            throw new GXSqlInjectionException(CharSequenceUtil.format("HAVING 子句存在SQL注入风险: {}", clause));
        }
        Set<String> keywordWhitelist = new HashSet<>(Arrays.asList("AND", "OR", "NOT", "NULL", "IS", "LIKE", "IN", "BETWEEN", "AS", "DISTINCT", "CASE", "WHEN", "THEN", "ELSE", "END",
                "SUM", "COUNT", "AVG", "MIN", "MAX"));
        java.util.regex.Matcher matcher = HAVING_TOKEN_PATTERN.matcher(trimmed);
        while (matcher.find()) {
            String token = matcher.group();
            String upper = token.toUpperCase(Locale.ROOT);
            if (keywordWhitelist.contains(upper)) {
                continue;
            }
            if (!allowedColumns.isEmpty() && !allowedColumns.contains(token.toLowerCase(Locale.ROOT))) {
                throw new GXDBConditionException(CharSequenceUtil.format("HAVING 字段不在白名单中: {}", token));
            }
        }
        return trimmed;
    }

    private static String validateRawSqlStrict(String rawSQL) {
        if (CharSequenceUtil.isBlank(rawSQL)) {
            throw new GXSqlInjectionException("原始SQL不能为空");
        }
        String normalized = rawSQL.trim();
        if (GXDBStringEscapeUtils.check(normalized)) {
            throw new GXSqlInjectionException("检测到SQL注入风险，查询已被阻止");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains(";") || lower.contains("--") || lower.contains("/*") || lower.contains("*/")) {
            throw new GXSqlInjectionException("原始SQL包含非法控制符");
        }
        if (!(lower.startsWith("select") || lower.startsWith("with"))) {
            throw new GXSqlInjectionException("原始SQL仅允许SELECT/WITH查询");
        }
        if (Pattern.compile("(?i)\\b(update|delete|insert|alter|drop|truncate|create|grant|revoke|call|exec|merge)\\b").matcher(normalized).find()) {
            throw new GXSqlInjectionException("原始SQL包含危险关键字");
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
            // ignore
        }
        return "mysql";
    }
}

