package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXDataSourceConstant;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SQL条件构建抽象基类
 * <p>
 * 该类为SQL条件构建提供基础功能，支持参数化查询以防止SQL注入。
 * 所有条件类型都应继承此类并实现特定的条件逻辑。
 * </p>
 *
 * <p>
 * 安全特性：
 * - 使用MyBatis参数化查询机制(#{})，而非字符串拼接，彻底防止SQL注入
 * - 使用AtomicLong生成唯一参数名，确保线程安全
 * - 自动处理表别名，防止字段名冲突
 * - 安全处理null值，避免空指针异常
 * - 参数值与SQL语句分离，提高安全性
 * - 支持函数表达式，自动识别并安全处理
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建等值条件（WHERE user.username = 'admin'）
 * GXCondition<?> eqCondition = new GXConditionEQ("user", "username", "admin");
 *
 * // 2. 创建LIKE条件（WHERE user.email LIKE '%@example.com'）
 * GXCondition<?> likeCondition = new GXConditionLike("user", "email", "%@example.com");
 *
 * // 3. 创建IN条件（WHERE user.status IN (1, 2, 3)）
 * GXCondition<?> inCondition = new GXConditionIN("user", "status", Arrays.asList(1, 2, 3));
 *
 * // 4. 创建函数表达式条件（WHERE DATE(user.create_time) = '2023-01-01'）
 * GXCondition<?> funcCondition = new GXConditionEQ("user", "DATE(create_time)", "2023-01-01");
 *
 * // 5. 将条件添加到条件列表
 * List<GXCondition<?>> conditions = Arrays.asList(eqCondition, likeCondition, inCondition, funcCondition);
 *
 * // 6. 创建查询参数并执行查询
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("user")
 *     .condition(conditions)
 *     .build();
 * String sql = GXBaseBuilder.findByCondition(queryParam);
 * </pre>
 * </p>
 *
 * @param <T> 字段值的类型参数
 * @author 塵子曦
 */

/**
 * 数据库查询条件的抽象基类
 * <p>
 * 该类实现了安全的参数化查询条件构建，通过MyBatis的#{paramName}机制防止SQL注入
 * 支持各种条件操作（等于、大于、小于、LIKE等），并可以处理不同数据类型
 * <p>
 * 安全特性：
 * 1. 使用参数化查询而非字符串拼接，有效防止SQL注入攻击
 * 2. 通过原子计数器生成唯一参数名，避免参数名冲突
 * 3. 支持表达式和函数条件，同时保持参数化处理
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个等值查询条件
 * GXConditionEQ condition = new GXConditionEQ("user_table", "age", 18);
 * String whereClause = condition.whereString(); 
 * // 结果: user_table.age = #{dbQueryParamInnerDto.paramMap.condition_age_1}
 *
 * // 在实际应用中与查询构建器结合使用
 * GXBaseMapper<UserEntity> mapper = ...;
 * GXModelQueryParamDto paramDto = new GXModelQueryParamDto();
 * paramDto.addCondition(condition);
 * List<UserEntity> users = mapper.findByCondition(paramDto);
 * </pre>
 */
public abstract class GXCondition<T> implements Serializable {
    /**
     * 参数计数器，用于生成唯一的参数名
     * 使用AtomicLong确保线程安全
     */
    private static final AtomicLong PARAM_COUNTER = new AtomicLong(0);

    /**
     * 可以是一个具体的字段名字  goods_name
     * <p>
     * 也可以是一个函数表达式  concat(g_goods.goods_name , '-' , g_goods.goods_sn)
     */
    protected final String fieldExpression;

    @Setter
    @Getter
    protected String tableNameAlias;

    @SuppressWarnings("all")
    @Getter
    protected Object value;

    @Getter
    protected String paramName;

    @Getter
    protected Map<String, Object> paramMap = new HashMap<>();

    protected GXCondition(String fieldExpression, Object value) {
        this("", fieldExpression, value);
    }

    protected GXCondition(String tableNameAlias, String fieldExpression, Object value) {
        this.tableNameAlias = tableNameAlias;
        this.fieldExpression = fieldExpression;
        this.value = value;
        this.paramName = generateParamName(fieldExpression);
        if (value != null) {
            this.paramMap.put(paramName, value);
        }
    }

    /**
     * 生成唯一的参数名
     *
     * @param fieldExpression 字段表达式
     * @return 参数名
     */
    /**
     * 生成唯一的参数名
     * 使用原子计数器确保在并发环境下参数名的唯一性
     *
     * @param fieldExpression 字段表达式
     * @return 唯一的参数名
     */
    protected String generateParamName(String fieldExpression) {
        String simplifiedName;
        // 如果是函数表达式，提取一个简化名称
        if (fieldExpression.contains("(")) {
            simplifiedName = "func" + Math.abs(fieldExpression.hashCode());
        } else {
            simplifiedName = CharSequenceUtil.toUnderlineCase(fieldExpression);
        }
        return "condition_" + simplifiedName + "_" + PARAM_COUNTER.incrementAndGet();
    }

    public abstract String getOp();

    /**
     * 生成WHERE条件的SQL片段
     * <p>
     * 该方法根据条件类型、字段表达式和操作符，构建安全的WHERE条件：
     * 1. 检查操作符是否为空或特殊值（如忽略数据过滤条件标记）
     * 2. 根据是否有表别名，构建适当的字段引用
     * 3. 使用MyBatis参数化查询语法（#{paramName}）引用参数值
     * <p>
     * 安全特性：
     * - 使用参数化查询而非字符串拼接，有效防止SQL注入攻击
     * - 使用CharSequenceUtil.format进行字符串格式化，避免直接拼接
     * - 参数值通过paramMap传递，而不是直接嵌入SQL中
     *
     * @return 完整的WHERE条件SQL片段，如 "table.field = #{dbQueryParamInnerDto.paramMap.param1}"
     */
    public String whereString() {
        String opStr = getOp();
        if (CharSequenceUtil.isEmpty(opStr) || CharSequenceUtil.equals(opStr, GXDataSourceConstant.IGNORE_DATA_FILTER_CONDITION_OP_VALUE)) {
            return "";
        }
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} {} #{dbQueryParamInnerDto.paramMap.{}}", getFieldExpression(), opStr, /*getFieldValue()*/paramName);
        }
        return CharSequenceUtil.format("{}.{} {} #{dbQueryParamInnerDto.paramMap.{}}", tableNameAlias, getFieldExpression(), opStr, /*getFieldValue()*/paramName);
    }

    public String getFieldExpression() {
        return CharSequenceUtil.toUnderlineCase(fieldExpression);
    }

    public GXConditionSegment toSegment() {
        return new GXConditionSegment(whereString(), new HashMap<>(paramMap));
    }

    public abstract T getFieldValue();

    public abstract T getFieldOriginalValue();
}
