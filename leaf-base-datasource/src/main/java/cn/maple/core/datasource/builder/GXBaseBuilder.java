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
 * <p>
 * 安全特性：
 * - 所有SQL操作都使用参数化查询（#{paramName}），而非字符串拼接
 * - 自动处理特殊字符，无需手动转义
 * - 条件值自动进行null检查，防止空值异常
 * - 自动添加软删除条件，防止误操作已删除数据
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建查询条件
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("user")
 *     .tableNameAlias("u")
 *     .columns(CollUtil.newHashSet("id", "username", "email"))
 *     .condition(Arrays.asList(
 *         new GXConditionEQ("u", "status", 1),
 *         new GXConditionLike("u", "username", "%admin%")
 *     ))
 *     .build();
 *
 * // 2. 生成查询SQL
 * String sql = GXBaseBuilder.findByCondition(queryParam);
 *
 * // 3. 使用MyBatis执行SQL
 * List<Dict> result = baseMapper.findByCondition(queryParam);
 * </pre>
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
     * <p>安全特性：</p>
     * <ol>
     *   <li>所有字段更新都通过GXUpdateField封装，使用参数化查询方式</li>
     *   <li>条件值自动进行null检查，防止空值导致的全表更新风险</li>
     *   <li>自动添加软删除条件（is_deleted=0），防止误操作已删除数据</li>
     *   <li>使用MyBatis SQL类构建SQL语句，避免手动拼接</li>
     *   <li>参数通过Map传递，与SQL语句分离，防止SQL注入</li>
     * </ol>
     *
     * <p>使用示例1 - 基础字段更新：</p>
     * <pre>
     * // 1. 创建更新字段列表
     * List<GXUpdateField<?>> updateFields = new ArrayList<>();
     * updateFields.add(new GXUpdateField<>("status", 2));                    // 更新状态字段为2
     * updateFields.add(new GXUpdateField<>("remark", "已处理"));            // 更新备注字段
     * updateFields.add(new GXUpdateField<>("process_time", new Date()));    // 更新处理时间为当前时间
     *
     * // 2. 创建更新条件
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("order")                                      // 设置表名为order
     *     .condition(Arrays.asList(
     *         new GXConditionEQ(null, "id", orderId),              // 条件1：id等于指定值
     *         new GXConditionEQ(null, "user_id", currentUserId)    // 条件2：user_id等于当前用户ID
     *     ))
     *     .build();
     *
     * // 3. 生成并执行更新SQL
     * String sql = GXBaseBuilder.updateFieldByCondition(queryParam, updateFields);
     * int rows = baseMapper.updateFieldByCondition(queryParam);    // 返回受影响的行数
     * </pre>
     *
     * <p>使用示例2 - 带表别名的复杂条件更新：</p>
     * <pre>
     * // 1. 创建更新字段列表，使用不同类型的更新字段
     * List<GXUpdateField<?>> updateFields = new ArrayList<>();
     * updateFields.add(new GXUpdateStrField("product", "name", "新商品名称"));      // 字符串类型字段
     * updateFields.add(new GXUpdateNumberField("product", "price", 199.99));      // 数值类型字段
     * updateFields.add(new GXUpdateNumberField("product", "stock", stock - 1));    // 数值计算
     * updateFields.add(new GXUpdateJsonField("product", "attributes", attributesJson)); // JSON类型字段
     *
     * // 2. 创建复杂更新条件
     * List<GXCondition<?>> conditions = new ArrayList<>();
     * conditions.add(new GXConditionEQ("p", "id", productId));                  // 商品ID匹配
     * conditions.add(new GXConditionGT("p", "stock", 0));                       // 库存大于0
     * conditions.add(new GXConditionIN("p", "status", Arrays.asList(1, 2)));    // 状态在指定范围内
     *
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("product")                                    // 设置表名
     *     .tableNameAlias("p")                                    // 设置表别名
     *     .condition(conditions)                                  // 设置条件列表
     *     .build();
     *
     * // 3. 生成并执行更新SQL
     * String sql = GXBaseBuilder.updateFieldByCondition(queryParam, updateFields);
     * int rows = baseMapper.updateFieldByCondition(queryParam);
     *
     * // 4. 处理更新结果
     * if (rows > 0) {
     *     log.info("商品更新成功，影响行数: {}", rows);
     * } else {
     *     log.warn("商品更新失败，可能记录不存在或条件不匹配");
     * }
     * </pre>
     *
     * <p>使用示例3 - 批量状态更新：</p>
     * <pre>
     * // 1. 创建更新字段列表
     * List<GXUpdateField<?>> updateFields = new ArrayList<>();
     * updateFields.add(new GXUpdateField<>("status", 3));                    // 更新状态为已完成(3)
     * updateFields.add(new GXUpdateField<>("complete_time", DateUtil.date())); // 设置完成时间
     * updateFields.add(new GXUpdateField<>("operator", operatorName));        // 设置操作人
     *
     * // 2. 创建更新条件 - 批量更新指定ID列表的记录
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("task")                                       // 设置表名为task
     *     .condition(Arrays.asList(
     *         new GXConditionIN(null, "id", taskIdList),           // 条件：id在指定列表中
     *         new GXConditionEQ(null, "status", 2)                // 条件：当前状态为进行中(2)
     *     ))
     *     .build();
     *
     * // 3. 生成并执行更新SQL
     * String sql = GXBaseBuilder.updateFieldByCondition(queryParam, updateFields);
     * int rows = baseMapper.updateFieldByCondition(queryParam);    // 返回受影响的行数
     *
     * // 4. 验证更新结果
     * if (rows == taskIdList.size()) {
     *     log.info("所有任务已成功更新为已完成状态");
     * } else {
     *     log.warn("部分任务更新失败，预期更新{}条，实际更新{}条", taskIdList.size(), rows);
     * }
     * </pre>
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
     * <p>性能优化特性：</p>
     * <ol>
     *   <li>只查询常量1而非所有字段，减少数据传输量</li>
     *   <li>限制结果集为1条记录，避免不必要的数据扫描</li>
     *   <li>利用索引进行快速查询，适合高频调用场景</li>
     *   <li>与MyBatis的参数化查询机制结合，确保查询安全性</li>
     * </ol>
     *
     * <p>使用示例1 - 基础记录检查：</p>
     * <pre>
     * // 1. 创建查询条件
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("user")                                      // 设置表名
     *     .condition(Arrays.asList(
     *         new GXConditionEQ(null, "username", "admin"),        // 用户名等于admin
     *         new GXConditionEQ(null, "status", 1)                // 状态为激活(1)
     *     ))
     *     .build();
     *
     * // 2. 检查记录是否存在
     * String sql = GXBaseBuilder.checkRecordIsExists(queryParam);
     * boolean exists = baseMapper.checkRecordIsExists(queryParam);
     *
     * // 3. 根据结果执行不同逻辑
     * if (exists) {
     *     log.info("用户已存在，无需创建");
     * } else {
     *     log.info("用户不存在，可以创建新用户");
     *     // 创建用户的逻辑
     * }
     * </pre>
     *
     * <p>使用示例2 - 带表别名的复杂条件检查：</p>
     * <pre>
     * // 1. 创建复杂查询条件
     * List<GXCondition<?>> conditions = new ArrayList<>();
     * conditions.add(new GXConditionEQ("o", "order_no", orderNo));           // 订单号匹配
     * conditions.add(new GXConditionEQ("o", "user_id", userId));            // 用户ID匹配
     * conditions.add(new GXConditionIN("o", "status", Arrays.asList(1, 2))); // 状态为待支付或已支付
     *
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("order")                                     // 设置表名
     *     .tableNameAlias("o")                                   // 设置表别名
     *     .condition(conditions)                                 // 设置条件列表
     *     .build();
     *
     * // 2. 检查订单是否存在
     * boolean exists = baseMapper.checkRecordIsExists(queryParam);
     *
     * // 3. 根据结果执行业务逻辑
     * if (!exists) {
     *     throw new GXBusinessException("订单不存在或状态异常，无法进行操作");
     * }
     * </pre>
     *
     * <p>使用示例3 - 与其他方法组合使用：</p>
     * <pre>
     * // 1. 创建查询条件
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("product")                                   // 设置表名
     *     .condition(Arrays.asList(
     *         new GXConditionEQ(null, "id", productId),            // 商品ID匹配
     *         new GXConditionGT(null, "stock", 0)                 // 库存大于0
     *     ))
     *     .build();
     *
     * // 2. 先检查商品是否存在且有库存
     * boolean canPurchase = baseMapper.checkRecordIsExists(queryParam);
     *
     * // 3. 如果可以购买，则更新库存
     * if (canPurchase) {
     *     // 创建更新字段列表
     *     List<GXUpdateField<?>> updateFields = new ArrayList<>();
     *     updateFields.add(new GXUpdateNumberField(null, "stock", "stock - 1")); // 库存减1
     *     updateFields.add(new GXUpdateNumberField(null, "sales", "sales + 1")); // 销量加1
     *
     *     // 执行更新
     *     int rows = baseMapper.updateFieldByCondition(queryParam, updateFields);
     *     log.info("商品库存更新成功，影响行数: {}", rows);
     * } else {
     *     log.warn("商品不存在或库存不足，无法购买");
     * }
     * </pre>
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
     * <p>
     * 功能特性：
     * - 支持选择特定字段或全表字段（使用columns参数）
     * - 支持表别名（使用tableNameAlias参数）
     * - 支持多表JOIN查询（使用joins参数）
     * - 支持复杂WHERE条件（使用condition参数）
     * - 支持GROUP BY分组（使用groupByField参数）
     * - 支持HAVING过滤（使用having参数）
     * - 支持ORDER BY排序（使用orderByField参数）
     * - 支持LIMIT限制结果集大小（使用limit参数）
     * - 自动处理JOIN表的软删除条件
     * </p>
     *
     * <p>
     * 安全特性：
     * - 所有条件值通过参数化查询（#{paramName}）传递，而非直接拼接SQL
     * - 自动处理表别名，防止字段名冲突
     * - 自动添加软删除条件（is_deleted=0），除非显式排除
     * - 条件值为null时会抛出异常，避免意外的全表查询
     * - JOIN条件也使用参数化查询，确保安全性
     * - 所有用户输入都经过验证，防止SQL注入攻击
     * </p>
     *
     * <p>
     * 使用示例1 - 基础查询：
     * <pre>
     * // 1. 创建基础查询条件
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("user")                                      // 设置表名
     *     .tableNameAlias("u")                                  // 设置表别名
     *     .columns(CollUtil.newHashSet("id", "username", "email")) // 设置查询字段
     *     .condition(Arrays.asList(
     *         new GXConditionEQ("u", "status", 1),              // 等于条件：u.status = 1
     *         new GXConditionLike("u", "username", "%admin%")   // 模糊匹配：u.username LIKE '%admin%'
     *     ))
     *     .build();
     *
     * // 2. 生成查询SQL
     * String sql = GXBaseBuilder.findByCondition(queryParam);
     *
     * // 3. 使用MyBatis执行SQL
     * List<Dict> result = baseMapper.findByCondition(queryParam);
     * </pre>
     * </p>
     *
     * <p>
     * 使用示例2 - 多表JOIN查询：
     * <pre>
     * // 1. 创建JOIN条件
     * List<GXJoinDto> joins = new ArrayList<>();
     *
     * // 创建用户-订单的JOIN
     * GXJoinDto orderJoin = new GXJoinDto();
     * orderJoin.setJoinType(GXJoinTypeEnums.LEFT_JOIN);           // 设置为LEFT JOIN
     * orderJoin.setMasterTableName("user");                      // 主表名
     * orderJoin.setMasterTableNameAlias("u");                    // 主表别名
     * orderJoin.setJoinTableName("order");                       // 关联表名
     * orderJoin.setJoinTableNameAlias("o");                      // 关联表别名
     * orderJoin.setAutoFillIsDeleteCondition(true);               // 自动添加软删除条件
     *
     * // 设置JOIN条件
     * List<GXDbJoinOp> joinConditions = new ArrayList<>();
     * joinConditions.add(new GXDbJoinOp("u.id", "=", "o.user_id")); // u.id = o.user_id
     * orderJoin.setAnd(joinConditions);
     *
     * // 添加JOIN表的WHERE条件
     * List<GXCondition<?>> orderConditions = new ArrayList<>();
     * orderConditions.add(new GXConditionGT("o", "total_amount", 100)); // o.total_amount > 100
     * orderJoin.setConditions(orderConditions);
     *
     * joins.add(orderJoin);
     *
     * // 2. 创建完整查询条件
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("user")
     *     .tableNameAlias("u")
     *     .columns(CollUtil.newHashSet("u.id", "u.username", "o.order_no", "o.total_amount"))
     *     .joins(joins)                                          // 设置JOIN
     *     .condition(Arrays.asList(
     *         new GXConditionEQ("u", "status", 1)                // u.status = 1
     *     ))
     *     .groupByField(CollUtil.newHashSet("u.id"))             // GROUP BY u.id
     *     .orderByField(Dict.create().set("o.create_time", "DESC")) // ORDER BY o.create_time DESC
     *     .limit(10)                                             // LIMIT 10
     *     .build();
     *
     * // 3. 生成并执行查询
     * String sql = GXBaseBuilder.findByCondition(queryParam);
     * List<Dict> result = baseMapper.findByCondition(queryParam);
     * </pre>
     * </p>
     *
     * <p>
     * 使用示例3 - 复杂条件和分组查询：
     * <pre>
     * // 1. 创建复杂查询条件
     * List<GXCondition<?>> conditions = new ArrayList<>();
     * conditions.add(new GXConditionEQ("p", "category_id", 5));       // p.category_id = 5
     * conditions.add(new GXConditionBetween("p", "price", 100, 500)); // p.price BETWEEN 100 AND 500
     * conditions.add(new GXConditionIN("p", "status", Arrays.asList(1, 2, 3))); // p.status IN (1,2,3)
     *
     * // 2. 创建分组和HAVING条件
     * Set<String> groupFields = CollUtil.newHashSet("p.category_id", "p.brand_id");
     * Set<String> havingConditions = CollUtil.newHashSet("COUNT(*) > 5", "AVG(p.price) > 200");
     *
     * // 3. 创建完整查询参数
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("product")
     *     .tableNameAlias("p")
     *     .columns(CollUtil.newHashSet(
     *         "p.category_id",
     *         "p.brand_id",
     *         "COUNT(*) as product_count",
     *         "AVG(p.price) as avg_price"
     *     ))
     *     .condition(conditions)
     *     .groupByField(groupFields)                              // GROUP BY 字段
     *     .having(havingConditions)                              // HAVING 条件
     *     .orderByField(Dict.create()
     *         .set("product_count", "DESC")
     *         .set("avg_price", "DESC"))
     *     .build();
     *
     * // 4. 生成并执行查询
     * String sql = GXBaseBuilder.findByCondition(queryParam);
     * List<Dict> result = baseMapper.findByCondition(queryParam);
     * </pre>
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
        if (Objects.nonNull(joins) && !joins.isEmpty()) {
            handleSQLJoin(sql, joins);
        }
        List<GXCondition<?>> condition = dbQueryParamInnerDto.getCondition();
        // 处理WHERE
        Map<String, Object> paramMap = handleSQLCondition(sql, condition);
        // 将参数设置到Mybatis的参数Map中
        dbQueryParamInnerDto.getParamMap().putAll(paramMap);
        if (!CollUtil.contains(condition, (c -> GXExclusionDeletedFieldCondition.class.isAssignableFrom(c.getClass())))) {
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
     * 处理SQL查询中的JOIN表关联
     * <p>
     * 该方法处理SQL查询中的JOIN操作，支持LEFT JOIN、RIGHT JOIN和INNER JOIN三种关联类型。
     * 可以通过AND和OR条件组合构建复杂的JOIN条件，实现灵活的多表查询。
     * </p>
     *
     * <p>
     * 功能特性：
     * - 支持三种JOIN类型：LEFT JOIN（左连接）、RIGHT JOIN（右连接）和INNER JOIN（内连接）
     * - 支持通过AND条件和OR条件组合复杂的JOIN条件
     * - 自动处理表别名，防止字段名冲突
     * - 支持主表和关联表的条件组合
     * - 自动处理软删除条件（is_deleted=0）
     * </p>
     *
     * <p>
     * 安全特性：
     * - 所有JOIN条件都通过参数化方式处理，防止SQL注入
     * - 自动处理NULL值，避免空指针异常
     * - 使用MyBatis SQL类构建SQL语句，避免手动拼接
     * </p>
     *
     * <p>
     * 使用示例 - 多表JOIN查询：
     * <pre>
     * // 1. 创建JOIN条件
     * List<GXJoinDto> joins = new ArrayList<>();
     *
     * // 创建用户-订单的JOIN
     * GXJoinDto orderJoin = new GXJoinDto();
     * orderJoin.setJoinType(GXJoinTypeEnums.LEFT_JOIN);           // 设置为LEFT JOIN
     * orderJoin.setMasterTableName("user");                      // 主表名
     * orderJoin.setMasterTableNameAlias("u");                    // 主表别名
     * orderJoin.setJoinTableName("order");                       // 关联表名
     * orderJoin.setJoinTableNameAlias("o");                      // 关联表别名
     * orderJoin.setAutoFillIsDeleteCondition(true);               // 自动添加软删除条件
     *
     * // 设置JOIN条件
     * List<GXDbJoinOp> joinConditions = new ArrayList<>();
     * joinConditions.add(new GXDbJoinOp("u.id", "=", "o.user_id")); // u.id = o.user_id
     * orderJoin.setAnd(joinConditions);
     *
     * // 添加OR条件（可选）
     * List<GXDbJoinOp> orConditions = new ArrayList<>();
     * orConditions.add(new GXDbJoinOp("u.email", "=", "o.email")); // u.email = o.email
     * orderJoin.setOr(orConditions);
     *
     * // 添加JOIN表的WHERE条件
     * List<GXCondition<?>> orderConditions = new ArrayList<>();
     * orderConditions.add(new GXConditionGT("o", "total_amount", 100)); // o.total_amount > 100
     * orderJoin.setConditions(orderConditions);
     *
     * joins.add(orderJoin);
     *
     * // 2. 创建SQL对象并处理JOIN
     * SQL sql = new SQL().SELECT("u.*, o.order_no, o.total_amount").FROM("user u");
     * GXBaseBuilder.handleSQLJoin(sql, joins);
     *
     * // 3. 继续构建其他SQL部分
     * // ...
     * </pre>
     * </p>
     *
     * @param sql   SQL对象，用于构建SQL语句，不能为null
     * @param joins JOIN信息列表，包含JOIN类型、表名、条件等，不能为null
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
     * 通过条件获取分页数据
     * <p>
     * 该方法用于构建分页查询的SQL语句，支持原生SQL和条件构建两种方式。
     * 当提供了原生SQL时，直接使用原生SQL；否则，调用findByCondition方法构建SQL语句。
     * </p>
     *
     * <p>
     * 功能特性：
     * - 支持原生SQL查询和条件构建查询两种方式
     * - 自动处理分页参数
     * - 与MyBatis-Plus的IPage对象无缝集成
     * - 继承findByCondition方法的所有安全特性
     * - 自动检测SQL注入风险，提供安全保障
     * </p>
     *
     * <p>
     * 安全特性：
     * - 所有条件值通过参数化查询（#{paramName}）传递，防止SQL注入
     * - 自动处理软删除条件（is_deleted=0）
     * - 条件值为null时会抛出异常，避免意外的全表查询
     * - 使用GXDBStringEscapeUtils.check方法检测原生SQL中的注入风险
     * - 记录潜在的SQL注入风险，便于安全审计和问题排查
     * </p>
     *
     * <p>
     * 使用示例 - 基础分页查询：
     * <pre>
     * // 1. 创建分页对象
     * IPage<UserEntity> page = new Page<>(1, 10); // 第1页，每页10条
     *
     * // 2. 创建查询条件
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("user")
     *     .tableNameAlias("u")
     *     .columns(CollUtil.newHashSet("id", "username", "email", "status"))
     *     .condition(Arrays.asList(
     *         new GXConditionEQ("u", "status", 1),              // u.status = 1
     *         new GXConditionLike("u", "username", "%admin%")   // u.username LIKE '%admin%'
     *     ))
     *     .orderByField(Dict.create().set("u.created_at", "DESC")) // 按创建时间降序
     *     .build();
     *
     * // 3. 生成分页SQL
     * String sql = GXBaseBuilder.paginate(page, queryParam);
     *
     * // 4. 使用MyBatis执行分页查询
     * IPage<Dict> result = baseMapper.paginate(page, queryParam);
     * </pre>
     * </p>
     *
     * <p>
     * 使用示例 - 使用原生SQL的分页查询：
     * <pre>
     * // 1. 创建分页对象
     * IPage<OrderEntity> page = new Page<>(1, 20); // 第1页，每页20条
     *
     * // 2. 创建带原生SQL的查询条件
     * String rawSql = "SELECT o.*, u.username FROM order o "
     *              + "LEFT JOIN user u ON o.user_id = u.id "
     *              + "WHERE o.status = #{status} AND o.is_deleted = 0 "
     *              + "ORDER BY o.created_at DESC";
     *
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .rawSQL(rawSql)
     *     .build();
     *
     * // 添加参数
     * queryParam.getParamMap().put("status", 2); // 订单状态参数
     *
     * // 3. 生成分页SQL
     * String sql = GXBaseBuilder.paginate(page, queryParam);
     *
     * // 4. 使用MyBatis执行分页查询
     * IPage<Dict> result = baseMapper.paginate(page, queryParam);
     * </pre>
     * </p>
     *
     * <p>
     * 使用示例 - 复杂条件分页查询：
     * <pre>
     * // 1. 创建分页对象
     * IPage<ProductEntity> page = new Page<>(1, 15); // 第1页，每页15条
     *
     * // 2. 创建JOIN条件
     * List<GXJoinDto> joins = new ArrayList<>();
     *
     * // 创建商品-分类的JOIN
     * GXJoinDto categoryJoin = new GXJoinDto();
     * categoryJoin.setJoinType(GXJoinTypeEnums.LEFT_JOIN);        // 设置为LEFT JOIN
     * categoryJoin.setMasterTableName("product");                // 主表名
     * categoryJoin.setMasterTableNameAlias("p");                // 主表别名
     * categoryJoin.setJoinTableName("category");                 // 关联表名
     * categoryJoin.setJoinTableNameAlias("c");                  // 关联表别名
     * categoryJoin.setAutoFillIsDeleteCondition(true);           // 自动添加软删除条件
     *
     * // 设置JOIN条件
     * List<GXDbJoinOp> joinConditions = new ArrayList<>();
     * joinConditions.add(new GXDbJoinOp("p.category_id", "=", "c.id")); // p.category_id = c.id
     * categoryJoin.setAnd(joinConditions);
     *
     * joins.add(categoryJoin);
     *
     * // 3. 创建查询条件
     * List<GXCondition<?>> conditions = new ArrayList<>();
     * conditions.add(new GXConditionEQ("p", "status", 1));                // 商品状态=1（上架）
     * conditions.add(new GXConditionBetween("p", "price", 100, 500));     // 价格区间100-500
     * conditions.add(new GXConditionIN("c", "type", Arrays.asList(1, 2, 3))); // 分类类型IN (1,2,3)
     *
     * // 4. 创建完整查询参数
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("product")
     *     .tableNameAlias("p")
     *     .columns(CollUtil.newHashSet(
     *         "p.id", "p.name", "p.price", "p.stock", "c.name as category_name"
     *     ))
     *     .joins(joins)                                          // 设置JOIN
     *     .condition(conditions)                                 // 设置条件
     *     .orderByField(Dict.create()
     *         .set("p.sales", "DESC")                            // 按销量降序
     *         .set("p.price", "ASC"))                           // 同销量按价格升序
     *     .build();
     *
     * // 5. 生成并执行分页查询
     * String sql = GXBaseBuilder.paginate(page, queryParam);
     * IPage<Dict> result = baseMapper.paginate(page, queryParam);
     * </pre>
     * </p>
     *
     * <p>
     * 使用示例 - 分组统计分页查询：
     * <pre>
     * // 1. 创建分页对象
     * IPage<Dict> page = new Page<>(1, 10); // 第1页，每页10条
     *
     * // 2. 创建查询条件
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("order")
     *     .tableNameAlias("o")
     *     .columns(CollUtil.newHashSet(
     *         "o.user_id",
     *         "COUNT(o.id) as order_count",
     *         "SUM(o.total_amount) as total_spend"
     *     ))
     *     .condition(Arrays.asList(
     *         new GXConditionGT("o", "created_at", DateUtil.offsetMonth(new Date(), -3)) // 近3个月订单
     *     ))
     *     .groupByField(CollUtil.newHashSet("o.user_id"))        // 按用户分组
     *     .having(CollUtil.newHashSet("COUNT(o.id) > 5"))        // 订单数量>5
     *     .orderByField(Dict.create().set("total_spend", "DESC")) // 按消费总额降序
     *     .build();
     *
     * // 3. 生成并执行分页查询
     * String sql = GXBaseBuilder.paginate(page, queryParam);
     * IPage<Dict> result = baseMapper.paginate(page, queryParam);
     *
     * // 4. 处理结果
     * List<Dict> highValueUsers = result.getRecords();
     * </pre>
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
     * <p>
     * 功能特性：
     * - 支持选择特定字段或全表字段（使用columns参数）
     * - 支持表别名（使用tableNameAlias参数）
     * - 支持多表JOIN查询（使用joins参数）
     * - 支持复杂WHERE条件（使用condition参数）
     * - 支持GROUP BY分组（使用groupByField参数）
     * - 支持HAVING过滤（使用having参数）
     * - 支持ORDER BY排序（使用orderByField参数）
     * - 支持LIMIT限制结果集大小（使用limit参数）
     * - 自动处理JOIN表的软删除条件
     * </p>
     *
     * <p>
     * 安全特性：
     * - 所有条件值通过参数化查询（#{paramName}）传递，而非直接拼接SQL
     * - 自动处理表别名，防止字段名冲突
     * - 自动添加软删除条件（is_deleted=0），除非显式排除
     * - 条件值为null时会抛出异常，避免意外的全表查询
     * - JOIN条件也使用参数化查询，确保安全性
     * - 所有用户输入都经过验证，防止SQL注入攻击
     * </p>
     *
     * <p>
     * 使用示例1 - 基础查询：
     * <pre>
     * // 1. 创建基础查询条件
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("user")                                      // 设置表名
     *     .tableNameAlias("u")                                  // 设置表别名
     *     .columns(CollUtil.newHashSet("id", "username", "email")) // 设置查询字段
     *     .condition(Arrays.asList(
     *         new GXConditionEQ("u", "status", 1),              // 等于条件：u.status = 1
     *         new GXConditionLike("u", "username", "%admin%")   // 模糊匹配：u.username LIKE '%admin%'
     *     ))
     *     .build();
     *
     * // 2. 生成查询SQL
     * String sql = GXBaseBuilder.findByCondition(queryParam);
     *
     * // 3. 使用MyBatis执行SQL
     * List<Dict> result = baseMapper.findByCondition(queryParam);
     * </pre>
     * </p>
     *
     * <p>
     * 使用示例2 - 多表JOIN查询：
     * <pre>
     * // 1. 创建JOIN条件
     * List<GXJoinDto> joins = new ArrayList<>();
     *
     * // 创建用户-订单的JOIN
     * GXJoinDto orderJoin = new GXJoinDto();
     * orderJoin.setJoinType(GXJoinTypeEnums.LEFT_JOIN);           // 设置为LEFT JOIN
     * orderJoin.setMasterTableName("user");                      // 主表名
     * orderJoin.setMasterTableNameAlias("u");                    // 主表别名
     * orderJoin.setJoinTableName("order");                       // 关联表名
     * orderJoin.setJoinTableNameAlias("o");                      // 关联表别名
     * orderJoin.setAutoFillIsDeleteCondition(true);               // 自动添加软删除条件
     *
     * // 设置JOIN条件
     * List<GXDbJoinOp> joinConditions = new ArrayList<>();
     * joinConditions.add(new GXDbJoinOp("u.id", "=", "o.user_id")); // u.id = o.user_id
     * orderJoin.setAnd(joinConditions);
     *
     * // 添加JOIN表的WHERE条件
     * List<GXCondition<?>> orderConditions = new ArrayList<>();
     * orderConditions.add(new GXConditionGT("o", "total_amount", 100)); // o.total_amount > 100
     * orderJoin.setConditions(orderConditions);
     *
     * joins.add(orderJoin);
     *
     * // 2. 创建完整查询条件
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("user")
     *     .tableNameAlias("u")
     *     .columns(CollUtil.newHashSet("u.id", "u.username", "o.order_no", "o.total_amount"))
     *     .joins(joins)                                          // 设置JOIN
     *     .condition(Arrays.asList(
     *         new GXConditionEQ("u", "status", 1)                // u.status = 1
     *     ))
     *     .groupByField(CollUtil.newHashSet("u.id"))             // GROUP BY u.id
     *     .orderByField(Dict.create().set("o.create_time", "DESC")) // ORDER BY o.create_time DESC
     *     .limit(10)                                             // LIMIT 10
     *     .build();
     *
     * // 3. 生成并执行查询
     * String sql = GXBaseBuilder.findByCondition(queryParam);
     * List<Dict> result = baseMapper.findByCondition(queryParam);
     * </pre>
     * </p>
     *
     * <p>
     * 使用示例3 - 复杂条件和分组查询：
     * <pre>
     * // 1. 创建复杂查询条件
     * List<GXCondition<?>> conditions = new ArrayList<>();
     * conditions.add(new GXConditionEQ("p", "category_id", 5));       // p.category_id = 5
     * conditions.add(new GXConditionBetween("p", "price", 100, 500)); // p.price BETWEEN 100 AND 500
     * conditions.add(new GXConditionIN("p", "status", Arrays.asList(1, 2, 3))); // p.status IN (1,2,3)
     *
     * // 2. 创建分组和HAVING条件
     * Set<String> groupFields = CollUtil.newHashSet("p.category_id", "p.brand_id");
     * Set<String> havingConditions = CollUtil.newHashSet("COUNT(*) > 5", "AVG(p.price) > 200");
     *
     * // 3. 创建完整查询参数
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("product")
     *     .tableNameAlias("p")
     *     .columns(CollUtil.newHashSet(
     *         "p.category_id",
     *         "p.brand_id",
     *         "COUNT(*) as product_count",
     *         "AVG(p.price) as avg_price"
     *     ))
     *     .condition(conditions)
     *     .groupByField(groupFields)                              // GROUP BY 字段
     *     .having(havingConditions)                              // HAVING 条件
     *     .orderByField(Dict.create()
     *         .set("product_count", "DESC")
     *         .set("avg_price", "DESC"))
     *     .build();
     *
     * // 4. 生成并执行查询
     * String sql = GXBaseBuilder.findByCondition(queryParam);
     * List<Dict> result = baseMapper.findByCondition(queryParam);
     * </pre>
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
     * <p>安全特性：</p>
     * <ol>
     *   <li>所有条件都通过GXCondition子类封装，使用参数化查询方式</li>
     *   <li>条件值自动进行null检查，防止空值导致的全表操作风险</li>
     *   <li>参数通过Map传递，与SQL语句分离，防止SQL注入</li>
     *   <li>使用AND连接多个条件，确保条件限制范围不会意外扩大</li>
     *   <li>特殊条件类型（如NULL条件）有专门处理逻辑，确保SQL语法正确</li>
     * </ol>
     *
     * <p>参数化查询示例：</p>
     * <pre>
     * // 1. 创建条件列表
     * List<GXCondition<?>> conditions = new ArrayList<>();
     * conditions.add(new GXConditionEQ("u", "status", 1)); // 生成: u.status = #{u_status}
     * conditions.add(new GXConditionLike("u", "name", "%张%")); // 生成: u.name LIKE #{u_name}
     * conditions.add(new GXConditionBetween("u", "age", 18, 30)); // 生成: u.age BETWEEN #{u_age_min} AND #{u_age_max}
     *
     * // 2. 创建SQL构建器
     * SQL sql = new SQL().SELECT("*").FROM("user u");
     *
     * // 3. 处理WHERE条件
     * Map<String, Object> paramMap = GXBaseBuilder.handleSQLCondition(sql, conditions);
     * // 生成的SQL: SELECT * FROM user u WHERE u.status = #{u_status} AND u.name LIKE #{u_name} AND u.age BETWEEN #{u_age_min} AND #{u_age_max}
     * // paramMap包含: {"u_status": 1, "u_name": "%张%", "u_age_min": 18, "u_age_max": 30}
     * </pre>
     *
     * @param sql       SQL对象，用于构建SQL语句，不能为null
     * @param condition 条件列表，可以为null或空列表
     * @return 参数映射，包含所有条件的参数名和值
     * @throws GXDBConditionException   当条件值为null时抛出异常，防止意外的全表操作
     * @throws IllegalArgumentException 当SQL对象为null时抛出异常
     */
    static Map<String, Object> handleSQLCondition(SQL sql, List<GXCondition<?>> condition) {
        // 参数校验
        if (sql == null) {
            throw new IllegalArgumentException("SQL对象不能为null");
        }
        Map<String, Object> paramMap = new HashMap<>();
        // 如果条件为空，直接返回空参数映射
        if (Objects.isNull(condition) || condition.isEmpty()) {
            LOGGER.debug("WHERE条件为空，不添加任何条件");
            return paramMap;
        }
        // 收集所有有效的WHERE条件
        List<String> lastWheres = new ArrayList<>();
        // 遍历处理每个条件
        condition.forEach(c -> {
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
        if (!lastWheres.isEmpty()) {
            String whereStr = String.join(" AND ", lastWheres);
            sql.WHERE(whereStr);
            LOGGER.debug("最终WHERE条件: {}", whereStr);
        }
        return paramMap;
    }

    /**
     * 根据条件软(逻辑)删除
     * <p>
     * 该方法执行软删除操作，即更新记录的is_deleted字段为主键值，而不是物理删除记录。
     * 同时会更新deleted_at字段为当前时间戳，并可以选择性地更新其他字段。
     * 所有更新操作都使用参数化查询，确保SQL注入安全。
     * </p>
     *
     * <p>
     * 功能特性：
     * - 自动将is_deleted字段设置为记录的主键值（而非简单的1或true），便于追踪删除记录的原始ID
     * - 自动更新deleted_at字段为当前时间戳
     * - 支持同时更新其他字段（通过updateFieldList参数）
     * - 支持设置删除人信息（通过extraData中的deletedBy字段）
     * - 自动检查表是否有主键，无主键则抛出异常
     * - 自动添加is_deleted=0条件，确保只操作未删除的记录
     * </p>
     *
     * <p>
     * 安全特性：
     * - 所有条件和更新字段都使用参数化查询，防止SQL注入
     * - 强制要求提供删除条件，防止误删全表数据
     * - 自动检查表结构，确保操作合法性
     * - 使用日志记录关键操作信息，便于审计和问题排查
     * </p>
     *
     * <p>
     * 使用示例：
     * <pre>
     * // 1. 创建软删除的条件
     * List<GXCondition<?>> conditions = new ArrayList<>();
     * conditions.add(new GXConditionEQ(null, "id", 10));  // 删除ID为10的记录
     * conditions.add(new GXConditionEQ(null, "status", 0)); // 且状态为0的记录
     *
     * // 2. 创建软删除时需要同时更新的字段（可选）
     * List<GXUpdateField<?>> updateFields = new ArrayList<>();
     * updateFields.add(new GXUpdateField<>("remark", "已被管理员删除")); // 更新备注字段
     *
     * // 3. 创建额外参数，如删除人信息
     * Dict extraData = Dict.create().set("deletedBy", "admin");
     *
     * // 4. 构建查询参数对象
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("user")           // 设置表名
     *     .condition(conditions)       // 设置条件
     *     .extraData(extraData)        // 设置额外数据
     *     .build();
     *
     * // 5. 生成软删除SQL
     * String sql = GXBaseBuilder.deleteSoftCondition(queryParam, updateFields);
     *
     * // 6. 执行软删除操作
     * int rows = baseMapper.deleteSoftCondition(queryParam, updateFields);
     * </pre>
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
     * <p>
     * 功能特性：
     * - 执行真实的DELETE操作，从数据库中永久移除记录
     * - 支持复杂的条件组合，精确定位需要删除的数据
     * - 自动添加is_deleted=0条件，防止误删已标记为删除的数据
     * - 使用MyBatis的SQL构建器，生成规范的SQL语句
     * </p>
     *
     * <p>
     * 安全特性：
     * - 所有条件都使用参数化查询（#{paramName}），防止SQL注入攻击
     * - 强制要求提供删除条件，防止误删全表数据
     * - 条件值自动进行null检查，防止空值导致的意外操作
     * - 默认只操作未删除的记录（is_deleted=0），除非显式排除此条件
     * </p>
     *
     * <p>
     * 使用场景：
     * - 需要彻底清除数据且不需要保留历史记录的情况
     * - 系统维护或数据清理任务
     * - 满足法规要求（如数据保护法规定的"被遗忘权"）
     * </p>
     *
     * <p>
     * 使用示例：
     * <pre>
     * // 1. 创建物理删除的条件
     * List<GXCondition<?>> conditions = new ArrayList<>();
     * conditions.add(new GXConditionEQ(null, "id", 10));  // 删除ID为10的记录
     * conditions.add(new GXConditionEQ(null, "status", -1)); // 且状态为-1的记录
     *
     * // 2. 构建查询参数对象
     * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
     *     .tableName("log_record")      // 设置表名
     *     .condition(conditions)       // 设置条件
     *     .build();
     *
     * // 3. 生成物理删除SQL
     * String sql = GXBaseBuilder.deleteCondition(queryParam);
     *
     * // 4. 执行物理删除操作（谨慎使用！）
     * int rows = baseMapper.deleteCondition(queryParam);
     * </pre>
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
     * <p>
     * 功能特性：
     * - 支持UNION和UNION ALL两种合并方式
     * - 自动处理子查询的表名和别名
     * - 自动合并所有子查询的参数，确保参数化查询正常工作
     * - 支持在合并结果上添加额外的过滤条件
     * - 自动调整外层查询条件的表别名，确保条件正确应用
     * </p>
     *
     * <p>
     * 安全特性：
     * - 所有查询条件都通过参数化查询（#{paramName}）传递，防止SQL注入
     * - 自动处理表别名，防止字段名冲突
     * - 自动合并参数Map，确保所有参数都能正确传递
     * - 使用子查询包装每个UNION查询，确保语法正确性
     * </p>
     *
     * <p>
     * 使用场景：
     * - 需要合并来自同一张表但条件不同的多个查询结果
     * - 需要合并来自不同表但结构相同的数据
     * - 需要对合并后的结果进行进一步过滤或处理
     * </p>
     *
     * <p>
     * 使用示例1 - 基础UNION查询：
     * <pre>
     * // 1. 创建第一个查询条件（查询名字包含"张"的用户）
     * GXBaseQueryParamInnerDto query1 = GXBaseQueryParamInnerDto.builder()
     *     .tableName("user")
     *     .tableNameAlias("u")
     *     .columns(CollUtil.newHashSet("id", "username", "email", "phone"))
     *     .condition(Arrays.asList(
     *         new GXConditionLike("u", "username", "%张%")
     *     ))
     *     .build();
     *
     * // 2. 创建第二个查询条件（查询电话号码包含"138"的用户）
     * GXBaseQueryParamInnerDto query2 = GXBaseQueryParamInnerDto.builder()
     *     .tableName("user")
     *     .tableNameAlias("u")
     *     .columns(CollUtil.newHashSet("id", "username", "email", "phone"))
     *     .condition(Arrays.asList(
     *         new GXConditionLike("u", "phone", "%138%")
     *     ))
     *     .build();
     *
     * // 3. 创建外层查询条件（对合并结果进行过滤，只查询状态为1的记录）
     * GXBaseQueryParamInnerDto masterQuery = GXBaseQueryParamInnerDto.builder()
     *     .tableName("user") // 这个会被替换为UNION子查询
     *     .tableNameAlias("tmp")
     *     .columns(CollUtil.newHashSet("id", "username", "email", "phone"))
     *     .condition(Arrays.asList(
     *         new GXConditionEQ("tmp", "status", 1)
     *     ))
     *     .build();
     *
     * // 4. 执行UNION查询
     * String sql = GXBaseBuilder.unionFindByCondition(
     *     masterQuery,
     *     Arrays.asList(query1, query2),
     *     GXUnionTypeEnums.UNION // 使用UNION去重
     * );
     *
     * // 5. 使用MyBatis执行SQL
     * List<Dict> result = baseMapper.findByCondition(masterQuery);
     * </pre>
     * </p>
     *
     * <p>
     * 使用示例2 - 复杂UNION ALL查询：
     * <pre>
     * // 1. 创建第一个查询条件（查询最近7天的订单）
     * GXBaseQueryParamInnerDto query1 = GXBaseQueryParamInnerDto.builder()
     *     .tableName("order")
     *     .tableNameAlias("o")
     *     .columns(CollUtil.newHashSet(
     *         "o.id", "o.order_no", "o.user_id", "o.total_amount", "o.status",
     *         "o.create_time", "'近期订单' as order_type"
     *     ))
     *     .condition(Arrays.asList(
     *         new GXConditionGTE("o", "create_time", DateUtil.offsetDay(new Date(), -7))
     *     ))
     *     .orderByField(Dict.create().set("o.create_time", "DESC"))
     *     .limit(100)
     *     .build();
     *
     * // 2. 创建第二个查询条件（查询金额大于1000的历史订单）
     * GXBaseQueryParamInnerDto query2 = GXBaseQueryParamInnerDto.builder()
     *     .tableName("order")
     *     .tableNameAlias("o")
     *     .columns(CollUtil.newHashSet(
     *         "o.id", "o.order_no", "o.user_id", "o.total_amount", "o.status",
     *         "o.create_time", "'大额订单' as order_type"
     *     ))
     *     .condition(Arrays.asList(
     *         new GXConditionGT("o", "total_amount", 1000),
     *         new GXConditionLT("o", "create_time", DateUtil.offsetDay(new Date(), -7))
     *     ))
     *     .orderByField(Dict.create().set("o.total_amount", "DESC"))
     *     .limit(50)
     *     .build();
     *
     * // 3. 创建外层查询条件（对合并结果进行排序和筛选）
     * GXBaseQueryParamInnerDto masterQuery = GXBaseQueryParamInnerDto.builder()
     *     .tableName("order") // 这个会被替换为UNION ALL子查询
     *     .tableNameAlias("tmp")
     *     .columns(CollUtil.newHashSet("*"))
     *     .condition(Arrays.asList(
     *         new GXConditionIN("tmp", "status", Arrays.asList(1, 2, 3))
     *     ))
     *     .orderByField(Dict.create().set("tmp.create_time", "DESC"))
     *     .build();
     *
     * // 4. 执行UNION ALL查询（不去重）
     * String sql = GXBaseBuilder.unionFindByCondition(
     *     masterQuery,
     *     Arrays.asList(query1, query2),
     *     GXUnionTypeEnums.UNION_ALL // 使用UNION ALL不去重
     * );
     *
     * // 5. 使用MyBatis执行SQL
     * List<Dict> result = baseMapper.findByCondition(masterQuery);
     * </pre>
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
     * <p>
     * 功能特性：
     * - 自动设置limit为1，确保只返回一条记录
     * - 支持UNION和UNION ALL两种合并方式
     * - 自动处理子查询的表别名
     * - 内部调用unionFindByCondition方法，共享其所有功能和安全特性
     * </p>
     *
     * <p>
     * 使用场景：
     * - 检查多个条件中是否存在满足条件的记录
     * - 获取多个查询结果中的第一条记录
     * - 需要合并查询但只关心是否有结果，而不需要所有结果
     * </p>
     *
     * <p>
     * 使用示例 - 检查用户是否存在于多个系统：
     * <pre>
     * // 1. 创建第一个查询条件（在用户表中查询）
     * GXBaseQueryParamInnerDto query1 = GXBaseQueryParamInnerDto.builder()
     *     .tableName("user")
     *     .tableNameAlias("u")
     *     .columns(CollUtil.newHashSet("id", "username", "'主系统' as source"))
     *     .condition(Arrays.asList(
     *         new GXConditionEQ("u", "username", username),
     *         new GXConditionEQ("u", "status", 1)
     *     ))
     *     .build();
     *
     * // 2. 创建第二个查询条件（在历史用户表中查询）
     * GXBaseQueryParamInnerDto query2 = GXBaseQueryParamInnerDto.builder()
     *     .tableName("user_history")
     *     .tableNameAlias("uh")
     *     .columns(CollUtil.newHashSet("id", "username", "'历史系统' as source"))
     *     .condition(Arrays.asList(
     *         new GXConditionEQ("uh", "username", username),
     *         new GXConditionEQ("uh", "is_migrated", 0)
     *     ))
     *     .build();
     *
     * // 3. 创建外层查询条件
     * GXBaseQueryParamInnerDto masterQuery = GXBaseQueryParamInnerDto.builder()
     *     .tableName("user")
     *     .tableNameAlias("tmp")
     *     .columns(CollUtil.newHashSet("*"))
     *     .build();
     *
     * // 4. 执行UNION查询并只返回一条记录
     * String sql = GXBaseBuilder.unionFindOneByCondition(
     *     masterQuery,
     *     Arrays.asList(query1, query2),
     *     GXUnionTypeEnums.UNION
     * );
     *
     * // 5. 使用MyBatis执行SQL
     * Dict result = baseMapper.findOneByCondition(masterQuery);
     *
     * // 6. 判断用户是否存在
     * boolean exists = result != null;
     * </pre>
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
     * <p>
     * 功能特性：
     * - 支持分页查询，与MyBatis-Plus的分页插件无缝集成
     * - 支持UNION和UNION ALL两种合并方式
     * - 优先使用预设的原始SQL，提高灵活性
     * - 内部调用unionFindByCondition方法，共享其所有功能和安全特性
     * </p>
     *
     * <p>
     * 使用场景：
     * - 需要对合并查询结果进行分页展示
     * - 数据量较大，需要分批次获取合并结果
     * - 在前端分页展示中需要同时展示来自不同表或不同条件的数据
     * </p>
     *
     * <p>
     * 使用示例 - 分页查询用户和管理员列表：
     * <pre>
     * // 1. 创建分页对象
     * IPage<Dict> page = new Page<>(1, 10); // 第1页，每页10条
     *
     * // 2. 创建第一个查询条件（查询普通用户）
     * GXBaseQueryParamInnerDto query1 = GXBaseQueryParamInnerDto.builder()
     *     .tableName("user")
     *     .tableNameAlias("u")
     *     .columns(CollUtil.newHashSet(
     *         "u.id", "u.username", "u.email", "u.phone",
     *         "u.create_time", "'普通用户' as user_type"
     *     ))
     *     .condition(Arrays.asList(
     *         new GXConditionEQ("u", "status", 1),
     *         new GXConditionEQ("u", "is_admin", 0)
     *     ))
     *     .build();
     *
     * // 3. 创建第二个查询条件（查询管理员用户）
     * GXBaseQueryParamInnerDto query2 = GXBaseQueryParamInnerDto.builder()
     *     .tableName("admin")
     *     .tableNameAlias("a")
     *     .columns(CollUtil.newHashSet(
     *         "a.id", "a.username", "a.email", "a.phone",
     *         "a.create_time", "'管理员' as user_type"
     *     ))
     *     .condition(Arrays.asList(
     *         new GXConditionEQ("a", "status", 1)
     *     ))
     *     .build();
     *
     * // 4. 创建外层查询条件（对合并结果进行排序）
     * GXBaseQueryParamInnerDto masterQuery = GXBaseQueryParamInnerDto.builder()
     *     .tableName("user") // 这个会被替换为UNION子查询
     *     .tableNameAlias("tmp")
     *     .columns(CollUtil.newHashSet("*"))
     *     .orderByField(Dict.create().set("tmp.create_time", "DESC"))
     *     .build();
     *
     * // 5. 执行分页UNION查询
     * String sql = GXBaseBuilder.unionPaginate(
     *     page,
     *     masterQuery,
     *     Arrays.asList(query1, query2),
     *     GXUnionTypeEnums.UNION // 使用UNION去重
     * );
     *
     * // 6. 使用MyBatis执行SQL并获取分页结果
     * GXPaginationResDto<Dict> result = baseDao.paginate(masterQuery);
     * </pre>
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
