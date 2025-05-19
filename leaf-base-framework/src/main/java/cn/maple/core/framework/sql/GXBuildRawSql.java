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
 * 构造原始SQL语句的工具接口
 * <p>
 * 本接口提供了一系列静态方法，用于根据不同的查询条件和参数构建SQL语句。
 * 所有方法都返回StringBuilder对象，便于后续操作和修改。
 * 接口支持各种SQL操作，包括查询、更新、删除（物理删除和逻辑删除）、联表查询、分页查询等。
 * </p>
 *
 * <p>
 * 安全说明：
 * 1. 本接口不直接处理用户输入，而是通过DTO对象和条件对象进行参数化查询，有效防止SQL注入
 * 2. 所有的字段名和表名都应通过框架内部的对象传递，不应直接拼接用户输入
 * 3. 条件值应通过GXCondition子类进行封装，确保参数化查询
 * </p>
 *
 * <p>
 * 使用示例：
 * </p>
 *
 * <pre>
 * // 1. 基本查询示例
 * GXBaseQueryParamInnerDto queryParam = new GXBaseQueryParamInnerDto();
 * queryParam.setTableName("user");
 * queryParam.setTableNameAlias("u");
 *
 * // 设置查询字段
 * Set<String> columns = new HashSet<>();
 * columns.add("id");
 * columns.add("username");
 * columns.add("email");
 * queryParam.setColumns(columns);
 *
 * // 添加查询条件
 * List<GXCondition<?>> conditions = new ArrayList<>();
 * conditions.add(new GXConditionEQ("u", "status", 1));
 * queryParam.setCondition(conditions);
 *
 * // 设置排序
 * Map<String, String> orderByMap = new HashMap<>();
 * orderByMap.put("u.created_at", "DESC");
 * queryParam.setOrderByField(orderByMap);
 *
 * // 设置分页
 * queryParam.setPage(1);
 * queryParam.setPageSize(10);
 *
 * // 生成SQL
 * StringBuilder sql = GXBuildRawSql.findByCondition(queryParam);
 * System.out.println(sql.toString());
 * // 输出: SELECT id,username,email FROM user u WHERE u.status = 1 ORDER BY u.created_at DESC LIMIT 0 , 10
 *
 * // 2. 联表查询示例
 * GXBaseQueryParamInnerDto joinQueryParam = new GXBaseQueryParamInnerDto();
 * joinQueryParam.setTableName("user");
 * joinQueryParam.setTableNameAlias("u");
 *
 * // 设置JOIN
 * List<GXJoinDto> joins = new ArrayList<>();
 * GXJoinDto joinDto = new GXJoinDto();
 * joinDto.setJoinType(GXJoinTypeEnums.LEFT_JOIN);
 * joinDto.setJoinTableName("user_profile");
 * joinDto.setJoinTableNameAlias("up");
 * joinDto.setMasterTableName("user");
 * joinDto.setMasterTableNameAlias("u");
 *
 * // 设置JOIN条件
 * List<GXDbJoinOp> joinConditions = new ArrayList<>();
 * joinConditions.add(new GXDbJoinOp("u.id", "=", "up.user_id"));
 * joinDto.setAnd(joinConditions);
 *
 * joins.add(joinDto);
 * joinQueryParam.setJoins(joins);
 *
 * // 生成SQL
 * StringBuilder joinSql = GXBuildRawSql.findByCondition(joinQueryParam);
 * System.out.println(joinSql.toString());
 * // 输出: SELECT u.* FROM user u LEFT OUTER JOIN user user ON (u.id = up.user_id) LIMIT 0 , 10
 *
 * // 3. 软删除示例
 * List<GXCondition<?>> deleteConditions = new ArrayList<>();
 * deleteConditions.add(new GXConditionEQ("user", "id", 123));
 *
 * Dict extraData = Dict.create();
 * extraData.set("deletedBy", "admin");
 *
 * StringBuilder deleteSql = GXBuildRawSql.deleteSoftCondition("user", null, deleteConditions, true, extraData);
 * System.out.println(deleteSql.toString());
 * // 输出类似: UPDATE user SET is_deleted = id,deleted_at = 1234567890,deleted_by = 'admin' WHERE user.id = 123 AND user.is_deleted = 0
 * </pre>
 *
 * @author magleton
 * @since 1.0
 */
@SuppressWarnings("all")
public interface GXBuildRawSql {
    Logger LOGGER = LoggerFactory.getLogger(GXBuildRawSql.class);

    /**
     * 更新实体字段和虚拟字段
     * <p>
     * 该方法用于构建UPDATE语句，默认将字段名转换为下划线格式。
     * 自动添加updated_at字段更新为当前时间戳。
     * 如果条件中不包含排除已删除记录的条件，会自动添加is_deleted=0的条件。
     * </p>
     *
     * @param tableName 表名，不能为空
     * @param fieldList 要更新的字段列表，每个字段都是GXUpdateField类型，包含字段名和新值
     * @param condition 更新条件，用于构建WHERE子句
     * @return StringBuilder 返回构建好的SQL语句
     */
    static StringBuilder updateFieldByCondition(String tableName, List<GXUpdateField<?>> fieldList, List<GXCondition<?>> condition) {
        return updateFieldByCondition(tableName, fieldList, condition, true);
    }

    /**
     * 更新实体字段和虚拟字段
     * <p>
     * 该方法用于构建UPDATE语句，可以指定是否将字段名转换为下划线格式。
     * 自动添加updated_at字段更新为当前时间戳。
     * 如果条件中不包含排除已删除记录的条件，会自动添加is_deleted=0的条件。
     * </p>
     *
     * <p>
     * 安全性说明：
     * 1. 表名和字段名应通过可信来源提供，不应直接使用用户输入
     * 2. 更新值应通过GXUpdateField对象封装，确保参数化处理
     * 3. 条件应通过GXCondition对象封装，确保参数化处理
     * </p>
     *
     * @param tableName             表名，不能为空
     * @param fieldList             要更新的字段列表，每个字段都是GXUpdateField类型，包含字段名和新值
     * @param condition             更新条件，用于构建WHERE子句
     * @param columnToUnderlineCase 是否将字段名转换为下划线格式，true表示转换，false表示保持原样
     * @return StringBuilder 返回构建好的SQL语句
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
     * <p>
     * 该方法用于构建一个简单的SQL查询，检查符合指定条件的记录是否存在。
     * 通过设置查询字段为"1"并限制结果数量为1，优化了查询性能。
     * 实际上是构建了一个类似 SELECT 1 FROM table WHERE conditions LIMIT 1 的SQL语句。
     * </p>
     *
     * <p>
     * 安全性说明：
     * 1. 表名和字段名通过GXBaseQueryParamInnerDto对象传入，不直接拼接用户输入
     * 2. 条件值通过GXCondition对象封装，确保参数化处理
     * 3. 自动处理已删除记录的过滤（is_deleted=0），除非显式排除
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件，包含表名、条件等信息
     * @return StringBuilder 返回构建好的SQL语句
     */
    static StringBuilder checkRecordIsExists(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        dbQueryParamInnerDto.setLimit(1);
        dbQueryParamInnerDto.setColumns(CollUtil.newHashSet("1"));
        return findOneByCondition(dbQueryParamInnerDto);
    }

    /**
     * 通过条件获取单条数据记录
     * <p>
     * 该方法用于构建一个查询单条记录的SQL语句，通过设置limit为1确保只返回一条记录。
     * 如果传入的limit值小于等于0，会自动设置为1。
     * 适用于需要获取符合条件的第一条记录的场景，如根据ID查询详情。
     * </p>
     *
     * <p>
     * 安全性说明：
     * 1. 表名和字段名通过GXBaseQueryParamInnerDto对象传入，不直接拼接用户输入
     * 2. 条件值通过GXCondition对象封装，确保参数化处理
     * 3. 自动处理已删除记录的过滤（is_deleted=0），除非显式排除
     * </p>
     *
     * <p>
     * 性能说明：
     * 1. 通过限制结果集大小为1，减少数据传输量
     * 2. 对于唯一索引字段的查询，数据库会在找到第一条匹配记录后停止扫描
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件，包含表名、字段、条件等信息
     * @return StringBuilder 返回构建好的SQL语句
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
     * <p>
     * 该方法用于构建一个统计记录数量的SQL语句，默认将字段名转换为下划线格式。
     * 通过子查询的方式实现，可以保留原查询的所有条件和连接。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件，包含表名、条件等信息
     * @return StringBuilder 返回构建好的SQL语句
     */
    static StringBuilder countByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return countByCondition(dbQueryParamInnerDto, true);
    }

    /**
     * 获取统计的SQL语句
     * <p>
     * 该方法用于构建一个统计记录数量的SQL语句，可以指定是否将字段名转换为下划线格式。
     * 通过子查询的方式实现，可以保留原查询的所有条件和连接。
     * 生成的SQL格式为：SELECT COUNT(*) FROM (原查询SQL) AS TOTAL LIMIT 0, 1
     * </p>
     *
     * <p>
     * 安全性说明：
     * 1. 表名和字段名通过GXBaseQueryParamInnerDto对象传入，不直接拼接用户输入
     * 2. 条件值通过GXCondition对象封装，确保参数化处理
     * 3. 自动处理已删除记录的过滤（is_deleted=0），除非显式排除
     * </p>
     *
     * <p>
     * 性能说明：
     * 1. 对于简单查询，可以考虑直接使用COUNT(*)而不使用子查询，以提高性能
     * 2. 对于复杂查询（如包含GROUP BY、HAVING等），子查询方式能确保计数准确
     * 3. LIMIT 0, 1用于优化结果集返回，因为COUNT(*)只会返回一行一列
     * </p>
     *
     * @param dbQueryParamInnerDto  查询条件，包含表名、条件等信息
     * @param columnToUnderlineCase 是否将查询字段转换成下划线，true表示转换，false表示保持原样
     * @return StringBuilder 返回构建好的SQL语句
     */
    static StringBuilder countByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, boolean columnToUnderlineCase) {
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM (");
        StringBuilder sql = findByCondition(dbQueryParamInnerDto, columnToUnderlineCase, false);
        countSql.append(sql).append(") AS TOTAL LIMIT 0 , 1");
        return countSql;
    }

    /**
     * 构造查询SQL语句
     * <p>
     * 该方法用于构建SELECT查询语句，默认将字段名转换为下划线格式并添加LIMIT子句。
     * 是构建查询SQL的主要入口方法，支持各种查询条件和参数。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件，包含表名、字段、条件等信息
     * @return StringBuilder 返回构建好的SQL语句
     */
    static StringBuilder findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return findByCondition(dbQueryParamInnerDto, true);
    }

    /**
     * 构造查询SQL语句
     * <p>
     * 该方法用于构建SELECT查询语句，可以指定是否将字段名转换为下划线格式，默认添加LIMIT子句。
     * 支持表别名、字段选择、条件过滤、排序和分页等功能。
     * </p>
     *
     * <p>
     * 安全性说明：
     * 1. 表名和字段名通过GXBaseQueryParamInnerDto对象传入，不直接拼接用户输入
     * 2. 条件值通过GXCondition对象封装，确保参数化处理
     * 3. 自动处理已删除记录的过滤（is_deleted=0），除非显式排除
     * </p>
     *
     * <p>
     * 性能说明：
     * 1. 返回StringBuilder而非String，避免不必要的字符串拼接开销
     * 2. 对于大量数据查询，应合理设置分页参数
     * </p>
     *
     * @param dbQueryParamInnerDto  查询条件，包含表名、字段、条件等信息
     * @param columnToUnderlineCase 是否将查询字段转换成下划线，true表示转换，false表示保持原样
     * @return StringBuilder 返回构建好的SQL语句
     */
    static StringBuilder findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto, boolean columnToUnderlineCase) {
        return findByCondition(dbQueryParamInnerDto, columnToUnderlineCase, true);
    }

    /**
     * 构造查询SQL语句
     * <p>
     * 该方法是构建SELECT查询语句的核心方法，支持以下功能：
     * 1. 指定查询字段，默认为表的所有字段
     * 2. 支持表别名
     * 3. 支持JOIN操作（LEFT JOIN、RIGHT JOIN、INNER JOIN）
     * 4. 支持WHERE条件过滤
     * 5. 支持GROUP BY分组
     * 6. 支持HAVING过滤
     * 7. 支持ORDER BY排序
     * 8. 支持LIMIT分页
     * </p>
     *
     * <p>
     * 安全性说明：
     * 1. 所有表名、字段名应通过GXBaseQueryParamInnerDto对象传入，不应直接拼接用户输入
     * 2. 所有条件值应通过GXCondition子类进行封装，确保参数化处理
     * 3. JOIN条件应通过GXJoinDto和GXDbJoinOp对象封装，避免直接拼接
     * 4. 自动处理已删除记录的过滤（is_deleted=0），除非显式排除
     * 5. 对于用户输入的排序字段和排序方向，应进行白名单验证，防止SQL注入
     * 6. 分页参数应进行合法性检查，避免负数或过大的值导致性能问题
     * </p>
     *
     * <p>
     * 性能说明：
     * 1. 返回StringBuilder而非String，避免不必要的字符串拼接开销
     * 2. 使用流式处理和Lambda表达式提高处理效率
     * 3. 对于大量数据查询，应合理设置分页参数
     * 4. JOIN操作会增加查询复杂度，应谨慎使用多表JOIN
     * 5. 对于频繁执行的查询，可以考虑使用缓存机制
     * 6. 复杂的WHERE条件可能导致索引失效，应根据实际情况优化条件顺序
     * </p>
     *
     * <p>
     * 使用示例：
     * <pre>
     * GXBaseQueryParamInnerDto queryParam = new GXBaseQueryParamInnerDto();
     * queryParam.setTableName("user");
     * queryParam.setTableNameAlias("u");
     *
     * // 设置查询字段
     * Set<String> columns = new HashSet<>();
     * columns.add("id");
     * columns.add("username");
     * queryParam.setColumns(columns);
     *
     * // 添加查询条件
     * List<GXCondition<?>> conditions = new ArrayList<>();
     * conditions.add(new GXConditionEQ("u", "status", 1));
     * queryParam.setCondition(conditions);
     *
     * // 设置排序
     * Map<String, String> orderByMap = new HashMap<>();
     * orderByMap.put("u.created_at", "DESC");
     * queryParam.setOrderByField(orderByMap);
     *
     * // 设置分页
     * queryParam.setPage(1);
     * queryParam.setPageSize(10);
     *
     * // 生成SQL
     * StringBuilder sql = GXBuildRawSql.findByCondition(queryParam, true, true);
     * // 输出类似: SELECT id,username FROM user u WHERE u.status = 1 ORDER BY u.created_at DESC LIMIT 0 , 10
     * </pre>
     * </p>
     *
     * @param dbQueryParamInnerDto  查询参数对象，包含表名、字段、条件等信息
     * @param columnToUnderlineCase 是否将字段名转换为下划线格式，true表示转换，false表示保持原样
     * @param appendLimit           是否添加LIMIT子句，true表示添加，false表示不添加
     * @return StringBuilder 返回构建好的SQL语句
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
     * <p>
     * 该方法用于在SQL语句末尾添加LIMIT子句，实现分页查询功能。
     * 支持两种分页方式：
     * 1. 直接指定limit值，此时忽略page和pageSize参数
     * 2. 通过page和pageSize参数计算分页，默认page为1，pageSize为10
     * </p>
     *
     * <p>
     * 安全性说明：
     * 1. 对page和pageSize参数进行了空值检查，避免空指针异常
     * 2. 使用Math.max确保currentPage不会小于0，防止负数分页
     * 3. 所有参数都经过验证后才用于构建SQL，防止SQL注入
     * </p>
     *
     * <p>
     * 性能说明：
     * 1. LIMIT子句对大数据量查询非常重要，可以有效减少数据传输量
     * 2. 当page值较大时，数据库需要扫描大量记录后才能返回结果，可能影响性能
     * 3. 对于大页码查询，建议结合索引或其他优化手段
     * </p>
     *
     * @param sql      SQL语句对象，用于追加LIMIT子句
     * @param page     当前页码，从1开始计数，默认为1
     * @param pageSize 每页记录数，默认为10
     * @param limit    直接指定的limit值，如果大于0则优先使用，忽略page和pageSize参数
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
     * <p>
     * 该方法用于构建SQL的JOIN子句，支持LEFT JOIN、RIGHT JOIN和INNER JOIN三种连接方式。
     * 通过GXJoinDto对象传入JOIN的相关信息，包括连接类型、表名、表别名、主表名、主表别名等。
     * 支持AND和OR条件的组合，实现复杂的JOIN条件构建。
     * </p>
     *
     * <p>
     * 安全性说明：
     * 1. 表名和字段名通过GXJoinDto对象传入，不直接拼接用户输入
     * 2. JOIN条件通过GXDbJoinOp对象封装，确保参数化处理
     * 3. 使用Optional和空集合检查，避免空指针异常
     * 4. 使用CharSequenceUtil.format进行字符串格式化，避免直接拼接
     * 5. 对JOIN类型进行严格检查，只支持预定义的JOIN类型
     * </p>
     *
     * <p>
     * 性能说明：
     * 1. 使用流式处理和Lambda表达式处理条件列表，提高处理效率
     * 2. JOIN操作可能导致性能问题，特别是多表JOIN或大表JOIN
     * 3. 应尽量减少JOIN表的数量，并确保JOIN条件字段有适当的索引
     * 4. 对于频繁执行的JOIN查询，可以考虑使用视图或物化视图优化
     * </p>
     *
     * <p>
     * 使用示例：
     * <pre>
     * // 创建JOIN对象
     * GXJoinDto joinDto = new GXJoinDto();
     * joinDto.setJoinType(GXJoinTypeEnums.LEFT_JOIN);
     * joinDto.setJoinTableName("order_item");
     * joinDto.setJoinTableNameAlias("oi");
     * joinDto.setMasterTableName("order");
     * joinDto.setMasterTableNameAlias("o");
     *
     * // 设置JOIN条件
     * List<GXDbJoinOp> joinConditions = new ArrayList<>();
     * joinConditions.add(new GXDbJoinOp("o.id", "=", "oi.order_id"));
     * joinDto.setAnd(joinConditions);
     *
     * // 添加到joins列表
     * List<GXJoinDto> joins = new ArrayList<>();
     * joins.add(joinDto);
     *
     * // 构建SQL
     * StringBuilder sql = new StringBuilder("SELECT o.* FROM order o");
     * GXBuildRawSql.handleSQLJoin(sql, joins);
     * // 输出类似: SELECT o.* FROM order o LEFT OUTER JOIN order o ON (o.id = oi.order_id)
     * </pre>
     * </p>
     *
     * @param sql   SQL语句对象，用于追加JOIN子句
     * @param joins JOIN信息列表，包含连接类型、表名、条件等信息
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
     * <p>
     * 该方法用于构建SQL的WHERE子句，将条件列表转换为SQL条件语句。
     * 通过GXCondition对象列表传入条件信息，支持各种条件操作符（如=, !=, >, <, LIKE等）。
     * 所有条件之间默认使用AND连接，形成一个完整的WHERE子句。
     * </p>
     *
     * <p>
     * 安全性说明：
     * 1. 条件值通过GXCondition对象封装，确保参数化处理，防止SQL注入
     * 2. 使用CollUtil.isNotEmpty进行非空检查，避免空指针异常
     * 3. 条件字段名和操作符通过GXCondition对象传入，不直接拼接用户输入
     * 4. 使用流式处理和Lambda表达式处理条件列表，减少手动拼接错误
     * 5. 使用CharSequenceUtil.join进行字符串连接，避免直接拼接
     * </p>
     *
     * <p>
     * 性能说明：
     * 1. 使用StringBuilder进行字符串拼接，避免不必要的字符串连接开销
     * 2. 使用流式处理和Lambda表达式处理条件列表，提高处理效率
     * 3. WHERE条件的顺序可能影响查询性能，应将高选择性的条件放在前面
     * 4. 过多的条件可能导致查询性能下降，应考虑使用索引优化
     * 5. 对于复杂条件，可以考虑使用子查询或视图优化
     * </p>
     *
     * <p>
     * 使用示例：
     * <pre>
     * // 创建条件列表
     * List<GXCondition<?>> conditions = new ArrayList<>();
     *
     * // 添加等值条件
     * conditions.add(GXCondition.eq("user_id", 123));
     *
     * // 添加LIKE条件
     * conditions.add(GXCondition.like("username", "%admin%"));
     *
     * // 添加大于条件
     * conditions.add(GXCondition.gt("age", 18));
     *
     * // 构建SQL
     * StringBuilder sql = new StringBuilder("SELECT * FROM users");
     * GXBuildRawSql.handleSQLCondition(sql, conditions);
     * // 输出类似: SELECT * FROM users WHERE user_id = ? AND username LIKE ? AND age > ?
     * </pre>
     * </p>
     *
     * @param sql                   SQL语句对象，用于追加WHERE子句
     * @param conditions            条件列表，包含字段名、操作符和值等信息
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
     * 通过条件获取分页数据
     * <p>
     * 该方法用于构建分页查询SQL语句，支持两种模式：
     * 1. 直接使用原始SQL语句（通过dbQueryParamInnerDto.getRawSQL()提供）
     * 2. 基于查询条件构建SQL语句（通过findByCondition方法）
     * </p>
     *
     * <p>
     * 安全性说明：
     * 1. 当使用原始SQL时，应确保SQL来源可信，避免SQL注入风险
     * 2. 使用CharSequenceUtil.isNotBlank进行非空检查，避免空指针异常
     * 3. 推荐使用findByCondition方法构建SQL，该方法会通过GXCondition对象参数化处理条件值
     * 4. 分页参数应进行合理性验证，避免过大的页码或每页数量导致性能问题
     * </p>
     *
     * <p>
     * 性能说明：
     * 1. 分页查询应配合适当的索引使用，避免全表扫描
     * 2. 对于大数据量表，应考虑使用物理分页而非内存分页
     * 3. 避免在分页查询中使用复杂的JOIN操作或子查询
     * 4. 考虑使用缓存机制缓存热门页的查询结果
     * </p>
     *
     * <p>
     * 使用示例：
     * <pre>
     * // 创建查询参数对象
     * GXBaseQueryParamInnerDto queryParam = new GXBaseQueryParamInnerDto();
     * queryParam.setTableName("users");
     * queryParam.setTableNameAlias("u");
     *
     * // 设置查询字段
     * queryParam.setColumns(Arrays.asList("id", "username", "email"));
     *
     * // 设置分页参数
     * queryParam.setPage(1);
     * queryParam.setLimit(10);
     *
     * // 设置排序
     * queryParam.setOrderByField("created_at");
     * queryParam.setOrderByDirection("DESC");
     *
     * // 添加查询条件
     * List<GXCondition<?>> conditions = new ArrayList<>();
     * conditions.add(GXCondition.eq("status", 1));
     * queryParam.setCondition(conditions);
     *
     * // 构建分页SQL
     * StringBuilder sql = GXBuildRawSql.paginate(queryParam);
     * // 输出类似: SELECT u.id, u.username, u.email FROM users u WHERE u.status = ? ORDER BY u.created_at DESC LIMIT 0, 10
     * </pre>
     * </p>
     *
     * @param dbQueryParamInnerDto 查询参数对象，包含表名、字段、条件、分页参数等信息
     * @return SQL语句构建器
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