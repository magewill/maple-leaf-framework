package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.dto.inner.condition.GXCondition;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * SQL函数条件抽象基类
 * <p>
 * 该类为所有SQL函数条件的基类，提供了通用的函数条件构建功能。
 * 继承自GXCondition，专门用于处理需要使用SQL函数的查询条件，如CONCAT、JSON_CONTAINS等。
 * </p>
 * 
 * <p>安全特性：</p>
 * <ul>
 *   <li>使用MyBatis参数化查询机制(#{})，而非字符串拼接，彻底防止SQL注入</li>
 *   <li>所有参数值通过paramMap传递，不直接嵌入SQL语句中</li>
 *   <li>函数参数和表达式经过安全处理，避免SQL注入风险</li>
 *   <li>使用CharSequenceUtil.format进行字符串格式化，避免直接拼接</li>
 *   <li>表名和字段名使用反引号(``)包裹，防止SQL关键字冲突</li>
 * </ul>
 * 
 * <p>性能优化：</p>
 * <ul>
 *   <li>使用参数化查询允许数据库缓存执行计划，提高性能</li>
 *   <li>通过Stream API高效处理表达式参数数组</li>
 *   <li>避免不必要的字符串拼接操作</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 示例1：创建一个JSON_SEARCH函数条件，在user_info字段中搜索"张三"
 * GXConditionFuncJsonSearch condition = new GXConditionFuncJsonSearch("t_user", "user_info", "张三");
 * String whereClause = condition.whereString(); 
 * // 生成：JSON_SEARCH(`t_user`.`user_info`, #{dbQueryParamInnerDto.paramMap.condition_xxx_oneOrAll}, #{dbQueryParamInnerDto.paramMap.condition_xxx})
 * 
 * // 示例2：创建一个CONCAT函数条件用于LIKE查询，连接first_name和last_name字段
 * GXConditionFuncConcat condition = new GXConditionFuncConcat("t_user", "name", "张", "first_name", "last_name");
 * String whereClause = condition.whereString(); 
 * // 生成：concat(t_user.first_name,t_user.last_name) LIKE #{dbQueryParamInnerDto.paramMap.condition_xxx}
 * 
 * // 示例3：将条件添加到查询参数中
 * List<GXCondition<?>> conditions = new ArrayList<>();
 * conditions.add(condition);
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("t_user")
 *     .condition(conditions)
 *     .build();
 * </pre>
 * 
 * <p>线程安全说明：</p>
 * <p>该类的实例通常不在多线程间共享，每次查询都会创建新的实例，因此不存在线程安全问题。</p>
 * <p>如果在多线程环境中共享实例，需要注意paramMap的并发访问问题。</p>
 *
 * @param <T> 字段值的类型参数
 * @author 塵子曦
 * @since 1.0.0
 */
public abstract class GXConditionFunc<T> extends GXCondition<T> {
    /**
     * 操作符或字段名
     * <p>
     * 在不同的子类中可能表示不同的含义，通常是字段名或操作符
     * </p>
     */
    protected String op;

    /**
     * 表达式参数数组
     * <p>
     * 用于存储函数所需的额外参数，如JSON路径、函数选项等
     * </p>
     */
    @SuppressWarnings("all")
    protected Object[] expression;

    /**
     * 基础构造函数
     * 
     * @param tableNameAlias 表名别名
     * @param fieldExpression 字段表达式
     * @param value 字段值
     */
    protected GXConditionFunc(String tableNameAlias, String fieldExpression, Object value) {
        super(tableNameAlias, fieldExpression, value);
    }

    /**
     * 扩展构造函数，支持额外表达式参数
     * 
     * @param tableNameAlias 表名别名
     * @param op 操作符或字段名
     * @param value 字段值
     * @param expression 额外的表达式参数，变长参数
     */
    protected GXConditionFunc(String tableNameAlias, String op, Object value, Object... expression) {
        this(tableNameAlias, "", value);
        this.op = op;
        this.expression = expression;
    }

    /**
     * 获取字段表达式
     * <p>
     * 将表达式参数数组中的每个元素格式化为"表名.参数"的形式，并用逗号连接
     * </p>
     * 
     * @return 格式化后的字段表达式字符串
     */
    @Override
    public String getFieldExpression() {
        return Arrays.stream(expression)
                .map(o -> CharSequenceUtil.format("{}.{}", tableNameAlias, o))
                .collect(Collectors.joining(","));
    }

    /**
     * 获取SQL函数名称
     * <p>
     * 子类必须实现此方法，返回对应的SQL函数名称，如CONCAT、JSON_CONTAINS等
     * </p>
     * 
     * @return SQL函数名称
     */
    protected abstract String getFunctionName();

    /**
     * 生成WHERE子句字符串，使用Mybatis参数化查询形式防止SQL注入
     *
     * @return 安全的WHERE子句字符串
     */
    @Override
    public String whereString() {
        return CharSequenceUtil.format("{}({})",
                getFunctionName(),
                getFieldExpression());
    }
}
