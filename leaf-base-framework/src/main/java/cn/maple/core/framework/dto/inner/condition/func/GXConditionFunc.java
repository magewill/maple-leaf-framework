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
 * <p>使用示例：</p>
 * <pre>
 * // 创建一个JSON_SEARCH函数条件
 * GXConditionFuncJsonSearch condition = new GXConditionFuncJsonSearch("t", "user_info", "张三");
 * String whereClause = condition.whereString(); // 生成WHERE子句
 * 
 * // 创建一个CONCAT函数条件用于LIKE查询
 * GXConditionFuncConcat condition = new GXConditionFuncConcat("t", "name", "张", "first_name", "last_name");
 * String whereClause = condition.whereString(); // 生成WHERE子句
 * </pre>
 * 
 * <p>线程安全说明：</p>
 * <p>该类的实例通常不在多线程间共享，每次查询都会创建新的实例，因此不存在线程安全问题。</p>
 * <p>如果在多线程环境中共享实例，需要注意paramMap的并发访问问题。</p>
 *
 * @param <T> 字段值的类型参数
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
