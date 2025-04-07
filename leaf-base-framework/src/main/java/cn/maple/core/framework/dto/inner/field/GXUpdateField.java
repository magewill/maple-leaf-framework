package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import lombok.Getter;

import java.io.Serializable;

/**
 * 数据库字段更新抽象基类
 * <p>
 * 该类为所有数据库字段更新操作的基础类，提供了通用的字段更新功能。
 * 子类需要实现具体的字段值获取逻辑，确保不同类型的字段能够正确地转换为SQL语句。
 * </p>
 *
 * @param <T> 字段值的类型参数
 * @author 塵子曦
 */
public abstract class GXUpdateField<T> implements Serializable {
    /**
     * 表名别名，用于多表操作时指定表
     */
    protected String tableNameAlias;

    /**
     * 字段名，自动转换为下划线命名格式
     */
    @Getter
    protected String fieldName;

    /**
     * 字段值，类型由子类决定
     */
    @SuppressWarnings("all")
    protected Object value;

    /**
     * 构造函数
     *
     * @param tableNameAlias 表名别名，可以为空
     * @param fieldName 字段名，会自动转换为下划线格式
     * @param value 字段值
     */
    protected GXUpdateField(String tableNameAlias, String fieldName, Object value) {
        this.tableNameAlias = tableNameAlias;
        this.fieldName = CharSequenceUtil.toUnderlineCase(fieldName);
        this.value = value;
    }

    /**
     * 生成用于UPDATE语句的字段更新表达式
     * <p>
     * 根据是否有表名别名，生成不同格式的更新表达式：
     * - 有表名别名：table_alias.field_name = value
     * - 无表名别名：field_name = value
     * </p>
     *
     * @return 格式化的字段更新表达式
     */
    public String updateString() {
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} = {}", fieldName, getFieldValue());
        }
        return CharSequenceUtil.format("{}.{} = {}", tableNameAlias, fieldName, getFieldValue());
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