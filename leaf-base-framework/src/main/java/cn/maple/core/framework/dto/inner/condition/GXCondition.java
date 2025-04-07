package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXDataSourceConstant;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 数据库查询条件抽象基类
 * <p>
 * 该类为所有数据库查询条件的基础类，提供了通用的条件构建功能。
 * 子类需要实现具体的操作符和字段值获取逻辑，确保不同类型的条件能够正确地转换为SQL语句。
 * </p>
 *
 * @param <T> 条件值的类型参数
 * @author 塵子曦
 */
public abstract class GXCondition<T> implements Serializable {
    /**
     * 字段表达式
     * <p>
     * 可以是一个具体的字段名字，例如：goods_name
     * 也可以是一个函数表达式，例如：concat(g_goods.goods_name, '-', g_goods.goods_sn)
     * </p>
     */
    protected final String fieldExpression;

    /**
     * 表名别名，用于多表操作时指定表
     */
    @Setter
    @Getter
    protected String tableNameAlias;

    /**
     * 条件值，类型由子类决定
     */
    @SuppressWarnings("all")
    @Getter
    protected Object value;

    /**
     * 构造函数（无表名别名）
     *
     * @param fieldExpression 字段表达式
     * @param value 条件值
     */
    protected GXCondition(String fieldExpression, Object value) {
        this("", fieldExpression, value);
    }

    /**
     * 构造函数
     *
     * @param tableNameAlias 表名别名，可以为空
     * @param fieldExpression 字段表达式
     * @param value 条件值
     */
    protected GXCondition(String tableNameAlias, String fieldExpression, Object value) {
        this.tableNameAlias = tableNameAlias;
        this.fieldExpression = fieldExpression;
        this.value = value;
    }

    /**
     * 获取操作符的抽象方法
     * <p>
     * 子类必须实现此方法，提供适合SQL语句的操作符表示。
     * 例如：=, >, <, LIKE, IN 等
     * </p>
     *
     * @return SQL操作符
     */
    public abstract String getOp();

    /**
     * 生成用于WHERE子句的条件表达式
     * <p>
     * 根据是否有表名别名，生成不同格式的条件表达式：
     * - 有表名别名：table_alias.field_expression op field_value
     * - 无表名别名：field_expression op field_value
     * </p>
     *
     * @return 格式化的WHERE条件表达式
     */
    public String whereString() {
        String opStr = getOp();
        if (CharSequenceUtil.isEmpty(opStr) && CharSequenceUtil.equals(opStr, GXDataSourceConstant.IGNORE_DATA_FILTER_CONDITION_OP_VALUE)) {
            return "";
        }
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} {} {}", getFieldExpression(), opStr, getFieldValue());
        }
        return CharSequenceUtil.format("{}.{} {} {}", tableNameAlias, getFieldExpression(), opStr, getFieldValue());
    }

    /**
     * 获取格式化后的字段表达式
     * <p>
     * 将字段表达式转换为下划线命名格式。
     * </p>
     *
     * @return 格式化后的字段表达式
     */
    public String getFieldExpression() {
        return CharSequenceUtil.toUnderlineCase(fieldExpression);
    }

    /**
     * 获取字段值的抽象方法
     * <p>
     * 子类必须实现此方法，提供适合SQL语句的字段值表示。
     * 实现时应当注意SQL注入防护和类型转换。
     * </p>
     *
     * @return 格式化后的字段值，可直接用于SQL语句
     */
    public abstract T getFieldValue();
}
