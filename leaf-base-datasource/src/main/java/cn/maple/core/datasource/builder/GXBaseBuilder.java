package cn.maple.core.datasource.builder;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.Tuple;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.properties.GXDataSourceProperties;
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

    static String updateFieldByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXUpdateField<?>> fieldList) {
        if (dbQueryParamInnerDto == null) {
            throw new GXBusinessException("查询参数对象不能为空!");
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
        dbQueryParamInnerDto.setLimit(1);
        dbQueryParamInnerDto.setColumns(CollUtil.newHashSet("1"));
        return findOneByCondition(dbQueryParamInnerDto);
    }

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
            sql.GROUP_BY(groupByField.toArray(new String[0]));
        }
        if (CollUtil.isNotEmpty(having)) {
            sql.HAVING(having.toArray(new String[0]));
        }
        if (Objects.nonNull(orderByField) && !orderByField.isEmpty()) {
            String[] orderColumns = new String[orderByField.size()];
            Integer[] idx = new Integer[]{0};
            orderByField.forEach((k, v) -> orderColumns[idx[0]++] = CharSequenceUtil.format("{} {}", k, v));
            sql.ORDER_BY(orderColumns);
        }

        return appendDialectLimit(sql.toString(), limit);
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
                if (ObjectUtil.isNull(c.getFieldValue()) && !GXConditionIsNULL.class.isAssignableFrom(c.getClass())) {
                    String msg = CharSequenceUtil.format("数据查询条件错误【查询字段{}.{}的值是null】", c.getTableNameAlias(), c.getFieldExpression());
                    throw new GXDBConditionException(msg);
                }
                String str = c.whereString();
                if (CharSequenceUtil.isNotEmpty(str)) {
                    lastWheres.add(str);
                    paramMap.putAll(c.getParamMap());
                    LOGGER.trace("添加WHERE条件: {}, 参数: {}", str, c.getParamMap());
                }
            }
        });
        return new Tuple(lastWheres, paramMap);
    }

    static <R> String paginate(IPage<R> page, GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (CharSequenceUtil.isNotBlank(dbQueryParamInnerDto.getRawSQL())) {
            String rawSQL = dbQueryParamInnerDto.getRawSQL();
            if (GXDBStringEscapeUtils.check(rawSQL)) {
                LOGGER.error("检测到SQL注入风险！原始SQL：{}", rawSQL);
                throw new GXSqlInjectionException("检测到SQL注入风险，查询已被阻止");
            }
            return rawSQL;
        }
        return findByCondition(dbQueryParamInnerDto);
    }

    static String findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        int limit = Optional.ofNullable(dbQueryParamInnerDto.getLimit()).orElse(1);
        if (limit <= 0) {
            limit = 1;
        }
        dbQueryParamInnerDto.setLimit(limit);
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

    static <R> String unionPaginate(IPage<R> page, GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (CharSequenceUtil.isNotBlank(dbQueryParamInnerDto.getRawSQL())) {
            return dbQueryParamInnerDto.getRawSQL();
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

    private static String appendDialectLimit(String baseSql, Integer limit) {
        if (Objects.isNull(limit) || limit <= 0 || CharSequenceUtil.isBlank(baseSql)) {
            return baseSql;
        }
        String lowerSql = baseSql.toLowerCase(Locale.ROOT);
        if (lowerSql.contains(" limit ") || lowerSql.contains(" fetch first ") || lowerSql.contains(" top ")) {
            return baseSql;
        }

        String dbType = resolveDbTypeFromContext().toLowerCase(Locale.ROOT);
        if (CollUtil.contains(Arrays.asList("mysql", "mariadb", "h2", "sqlite", "postgresql", "postgres"), dbType)) {
            return baseSql + " LIMIT " + limit;
        }
        if (CollUtil.contains(Arrays.asList("oracle", "oracle12c", "oracle_12c", "db2", "dm"), dbType)) {
            return baseSql + " FETCH FIRST " + limit + " ROWS ONLY";
        }
        if (CollUtil.contains(Arrays.asList("sqlserver", "sql_server", "mssql", "sql-server"), dbType)) {
            return "SELECT TOP " + limit + " * FROM (" + baseSql + ") tmp_limit";
        }
        return baseSql + " LIMIT " + limit;
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
